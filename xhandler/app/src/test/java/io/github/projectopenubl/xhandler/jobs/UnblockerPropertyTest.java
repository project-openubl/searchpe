package io.github.projectopenubl.xhandler.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.projectopenubl.xhandler.engine.EngineStub;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Property test of the unblocker (DESIGN.md §10.3, §10.8): random job, resource and credential
 * mixes; after every pass, no resource is held twice, FIFO holds per resource, credential slots
 * are respected, and every job eventually runs (no starvation).
 *
 * <p>Each trial runs in one transaction that is rolled back, so the live worker never sees its jobs.
 */
@QuarkusTest
@QuarkusTestResource(EngineStub.class)
class UnblockerPropertyTest {

    record TestJob(UUID id, long seq, Set<String> resources, String credential, int slots) {}

    @Inject
    EntityManager em;

    @Test
    void random_mixes_keep_the_invariants() {
        long seed = System.nanoTime();
        Random random = new Random(seed);
        for (int trial = 0; trial < 25; trial++) {
            runTrial(new Random(random.nextLong()), "seed " + seed + " trial " + trial);
        }
    }

    private void runTrial(Random random, String label) {
        String run = UUID.randomUUID().toString().substring(0, 8);
        QuarkusTransaction.begin();
        try {
            // Hold the UNBLOCK lock like the real unblocker, so the live worker skips its passes
            em.createNativeQuery("SELECT pg_advisory_xact_lock(7001, 1)").getSingleResult();
            List<TestJob> jobs = new ArrayList<>();
            int count = 5 + random.nextInt(25);
            for (int i = 0; i < count; i++) {
                Set<String> resources = new HashSet<>();
                int n = random.nextInt(3);
                for (int r = 0; r < n; r++) {
                    resources.add("prop:" + run + ":r" + random.nextInt(6));
                }
                // Credential c<k> allows k+1 running jobs
                int k = random.nextInt(2);
                String credential = random.nextInt(3) == 0 ? null : "prop:" + run + ":c" + k;
                int slots = k + 1;
                UUID id = UUID.randomUUID();
                em.createNativeQuery("SELECT xhandler.enqueue_platform_job(:id, 'test.never', CAST(2 AS smallint), "
                                + "CAST('{}' AS jsonb), CAST(:res AS text[]), CAST(:cred AS text), CAST(:slots AS int))")
                        .setParameter("id", id)
                        .setParameter("res", "{" + String.join(",", resources) + "}")
                        .setParameter("cred", credential)
                        .setParameter("slots", credential == null ? null : slots)
                        .getSingleResult();
                long seq = ((Number) em.createNativeQuery("SELECT seq FROM xhandler.job_summary(:id)")
                        .setParameter("id", id).getSingleResult()).longValue();
                jobs.add(new TestJob(id, seq, resources, credential, slots));
            }
            Set<UUID> running = new HashSet<>();
            Set<UUID> done = new HashSet<>();
            int passes = 0;
            while (done.size() < jobs.size()) {
                assertTrue(passes++ < jobs.size() * 4 + 10, label + ": starvation");
                em.createNativeQuery("SELECT xhandler.unblock_jobs()").getSingleResult();
                Set<UUID> unblocked = new HashSet<>();
                for (TestJob job : jobs) {
                    if (!done.contains(job.id()) && !running.contains(job.id()) && "waiting".equals(state(job.id())) && isUnblocked(job.id())) {
                        unblocked.add(job.id());
                    }
                }
                Set<UUID> active = new HashSet<>(running);
                active.addAll(unblocked);
                checkNoOverlap(jobs, active, label);
                checkSlots(jobs, active, label);
                checkFifo(jobs, active, done, label);
                // Start some unblocked jobs, finish some running ones
                for (UUID id : unblocked) {
                    if (random.nextBoolean()) {
                        em.createNativeQuery("SELECT 1 FROM xhandler.start_job(:id, 'prop-worker')").setParameter("id", id).getResultList();
                        running.add(id);
                    }
                }
                for (UUID id : new ArrayList<>(running)) {
                    if (random.nextBoolean()) {
                        em.createNativeQuery("SELECT xhandler.finish_job(:id, 'prop-worker', 'completed', NULL, NULL)")
                                .setParameter("id", id).getSingleResult();
                        running.remove(id);
                        done.add(id);
                    }
                }
            }
            assertEquals(jobs.size(), done.size());
        } finally {
            QuarkusTransaction.rollback();
        }
    }

    private String state(UUID id) {
        return (String) em.createNativeQuery("SELECT state FROM xhandler.job_summary(:id)").setParameter("id", id).getSingleResult();
    }

    private boolean isUnblocked(UUID id) {
        return em.createNativeQuery("SELECT unblocked_at FROM xhandler.job_summary(:id)").setParameter("id", id)
                .getSingleResult() != null;
    }

    private static void checkNoOverlap(List<TestJob> jobs, Set<UUID> active, String label) {
        Map<String, UUID> holder = new HashMap<>();
        for (TestJob job : jobs) {
            if (!active.contains(job.id())) {
                continue;
            }
            for (String resource : job.resources()) {
                UUID previous = holder.put(resource, job.id());
                assertTrue(previous == null, label + ": " + resource + " held by two jobs");
            }
        }
    }

    private static void checkSlots(List<TestJob> jobs, Set<UUID> active, String label) {
        Map<String, Integer> used = new HashMap<>();
        for (TestJob job : jobs) {
            if (active.contains(job.id()) && job.credential() != null) {
                int n = used.merge(job.credential(), 1, Integer::sum);
                assertTrue(n <= job.slots(), label + ": credential " + job.credential() + " over its slots");
            }
        }
    }

    /** A job is active only if no earlier unfinished job shares one of its resources. */
    private static void checkFifo(List<TestJob> jobs, Set<UUID> active, Set<UUID> done, String label) {
        for (TestJob job : jobs) {
            if (!active.contains(job.id())) {
                continue;
            }
            for (TestJob earlier : jobs) {
                if (earlier.seq() < job.seq() && !done.contains(earlier.id()) && !active.contains(earlier.id())) {
                    for (String resource : job.resources()) {
                        assertTrue(!earlier.resources().contains(resource), label + ": FIFO broken on " + resource);
                    }
                }
            }
        }
    }
}
