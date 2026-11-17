package io.github.projectopenubl.xhandler.jobs;

import static io.github.projectopenubl.xhandler.Api.tenantOf;
import static io.github.projectopenubl.xhandler.engine.EngineStub.Reply;
import static io.github.projectopenubl.xhandler.engine.EngineStub.sendAnswer;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.projectopenubl.xhandler.company.TestCompanies;
import io.github.projectopenubl.xhandler.documents.Document;
import io.github.projectopenubl.xhandler.documents.TestDocuments;
import io.github.projectopenubl.xhandler.engine.EngineStub;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Chaos tests of the job system (DESIGN.md §10.4, §15.2, §24.3): dead and paused workers, a lost
 * lock connection, and a SUNAT outage with an ordered, breaker-limited drain.
 */
@QuarkusTest
@QuarkusTestResource(EngineStub.class)
class RecoveryTest {

    static final Duration WAIT = Duration.ofSeconds(30);

    @Inject
    TestDocuments docs;

    @Inject
    TestCompanies companies;

    @Inject
    EntityManager em;

    @Inject
    DirectConnections connections;

    @Inject
    WorkerRuntime worker;

    UUID tenant;
    UUID company;
    String endpoint;

    @BeforeEach
    void setUp() {
        tenant = tenantOf("alice");
        company = companies.verified(tenant, "fake");
        endpoint = "t-" + UUID.randomUUID() + ":invoice";
    }

    @AfterEach
    void tearDown() {
        EngineStub.reset();
        if (!worker.running()) {
            worker.start();
        }
    }

    @Test
    void a_dead_workers_send_goes_through_unknown_and_is_resolved_once() {
        // The document's send started on a worker that then died
        UUID job = UUID.randomUUID();
        TestDocuments.Created doc = docs.pending(tenant, company, endpoint, d -> {
            d.nextAttemptAt = Instant.now().plusSeconds(5);
            d.sendStartedAt = Instant.now().minusSeconds(60);
            d.attempts = 1;
            d.attemptJobId = job;
        });
        startGhostJob(doc.id(), "ghost-" + UUID.randomUUID(), job);

        // Cleanup fails the job (its lock is free), the document moves to unknown, and the
        // reconciler resumes it after the lease: resolution (consult in the fake) finds it accepted
        EngineStub.on("/v1/status-cdr", body ->
                Reply.ok("{\"status\":\"accepted\",\"code\":\"0001\",\"description\":\"aceptado\",\"cdr_zip\":\"UEsDBA==\"}"));
        await().atMost(WAIT).until(() -> "failed".equals(jobState(job)));
        await().atMost(WAIT).until(() -> "accepted".equals(docs.get(tenant, doc.id()).status));
        assertEquals(0, EngineStub.requests("/v1/send", doc.marker()).size(), "never resent blindly");
    }

    @Test
    void a_paused_worker_keeps_its_job_and_no_second_attempt_runs() throws Exception {
        TestDocuments.Created doc = docs.pending(tenant, company, endpoint, d -> d.nextAttemptAt = Instant.now().plusSeconds(2));
        // The paused worker's lock connection is still alive: it holds the job lock (taken before
        // the job is visible, so recovery never sees it unlocked)
        try (Connection paused = connections.open("paused-worker")) {
            UUID job = startGhostJob(doc.id(), "paused-" + UUID.randomUUID(), UUID.randomUUID(), seq -> {
                try (PreparedStatement lock = paused.prepareStatement("SELECT pg_advisory_lock(?)")) {
                    lock.setLong(1, seq);
                    lock.executeQuery().close();
                } catch (java.sql.SQLException e) {
                    throw new IllegalStateException(e);
                }
            });
            // Its heartbeat is stale, but recovery can't take the lock; the lease expires and the
            // reconciler creates a new attempt, which waits behind the resource (FIFO)
            Thread.sleep(4000);
            assertEquals("running", jobState(job));
            assertEquals(0, EngineStub.requests("/v1/send", doc.marker()).size());
            paused.close();
            // The connection is gone: recovery fails the job and the waiting attempt runs
            await().atMost(WAIT).until(() -> "failed".equals(jobState(job)));
        }
        await().atMost(WAIT).until(() -> Document.ACCEPTED.contains(docs.get(tenant, doc.id()).status));
        assertEquals(1, EngineStub.requests("/v1/send", doc.marker()).size());
    }

    @Test
    void losing_the_lock_connection_stops_claims() {
        worker.breakLockConnection();
        await().atMost(WAIT).until(() -> !worker.claimingJobs());
        await().atMost(WAIT).until(() -> !worker.running());

        TestDocuments.Created doc = docs.pending(tenant, company, endpoint, null);
        assertEquals("pending_send", docs.get(tenant, doc.id()).status);

        worker.start();
        await().atMost(WAIT).until(() -> "accepted".equals(docs.get(tenant, doc.id()).status));
    }

    @Test
    void an_outage_opens_the_breaker_and_the_backlog_drains_in_deadline_order() {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                        "INSERT INTO xhandler.sunat_endpoint (id, concurrency_cap) VALUES (:id, 1)")
                .setParameter("id", endpoint)
                .executeUpdate());
        // SUNAT is down for this endpoint
        List<String> markers = new ArrayList<>();
        EngineStub.on("/v1/send", body -> markers.stream().anyMatch(body::contains)
                ? Reply.ok(sendAnswer("not_delivered", null, null, null))
                : Reply.ok(sendAnswer("accepted", 0, null, null)));
        List<TestDocuments.Created> created = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            int hours = (i * 5) % 8; // deadlines out of creation order
            TestDocuments.Created doc = docs.pending(tenant, company, endpoint,
                    d -> d.deadlineAt = Instant.now().plus(Duration.ofHours(10 + hours)));
            markers.add(doc.marker());
            created.add(doc);
        }
        await().atMost(WAIT).until(() -> "open".equals(breaker()));
        // No attempt is in flight once the breaker is open (it creates none)
        await().atMost(WAIT).until(() -> outstandingAttempts() == 0);
        int duringOutage = EngineStub.requests().size();

        // SUNAT recovers: every document is due again; the breaker lets one probe through, closes,
        // and the backlog drains one at a time (cap 1), earliest deadline first
        List<String> order = new CopyOnWriteArrayList<>();
        EngineStub.on("/v1/send", body -> {
            markers.stream().filter(body::contains).findFirst().ifPresent(order::add);
            return Reply.ok(sendAnswer("accepted", 0, null, null));
        });
        created.forEach(d -> docs.dueNow(tenant, d.id()));
        await().atMost(Duration.ofSeconds(90)).until(() -> created.stream()
                .allMatch(d -> "accepted".equals(docs.get(tenant, d.id()).status)));

        List<String> byDeadline = created.stream()
                .sorted(java.util.Comparator.comparing(d -> docs.get(tenant, d.id()).deadlineAt))
                .map(TestDocuments.Created::marker)
                .toList();
        assertEquals(byDeadline, order);
        assertTrue(duringOutage > 0);
        assertEquals("closed", breaker());
    }

    String breaker() {
        return QuarkusTransaction.requiringNew().call(() -> (String) em.createNativeQuery(
                        "SELECT breaker_state FROM xhandler.sunat_endpoint WHERE id = :id")
                .setParameter("id", endpoint)
                .getResultStream().findFirst().orElse("none"));
    }

    UUID startGhostJob(UUID documentId, String ghost, UUID job) {
        return startGhostJob(documentId, ghost, job, seq -> {});
    }

    UUID startGhostJob(UUID documentId, String ghost, UUID job, java.util.function.LongConsumer beforeCommit) {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("INSERT INTO xhandler.worker (name, role, last_heartbeat) VALUES (:w, 'worker', now() - interval '1 hour')")
                    .setParameter("w", ghost)
                    .executeUpdate();
            em.createNativeQuery("SELECT xhandler.enqueue_platform_job(:id, 'test.never', CAST(1 AS smallint), "
                            + "CAST(:args AS jsonb), CAST(:res AS text[]))")
                    .setParameter("id", job)
                    .setParameter("args", "{\"document_id\":\"" + documentId + "\"}")
                    .setParameter("res", "{document:" + documentId + "}")
                    .getSingleResult();
            // Unblock and start it as the ghost, in this transaction (the real worker can't see it yet)
            em.createNativeQuery("SELECT xhandler.unblock_jobs()").getSingleResult();
            List<?> started = em.createNativeQuery("SELECT name FROM xhandler.start_job(:id, :w)")
                    .setParameter("id", job)
                    .setParameter("w", ghost)
                    .getResultList();
            assertFalse(started.isEmpty(), "ghost job started");
            beforeCommit.accept(((Number) em.createNativeQuery("SELECT seq FROM xhandler.job_summary(:id)")
                    .setParameter("id", job).getSingleResult()).longValue());
        });
        return job;
    }

    String jobState(UUID job) {
        return QuarkusTransaction.requiringNew().call(() -> (String) em.createNativeQuery(
                        "SELECT state FROM xhandler.job_summary(:id)")
                .setParameter("id", job)
                .getSingleResult());
    }

    long seqOf(UUID job) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) em.createNativeQuery(
                        "SELECT seq FROM xhandler.job_summary(:id)")
                .setParameter("id", job)
                .getSingleResult()).longValue());
    }

    long outstandingAttempts() {
        return QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(tenant);
            return Job.count("endpoint = ?1 and state in ('waiting', 'running')", endpoint);
        });
    }

    @Inject
    io.github.projectopenubl.xhandler.tenancy.TenantAccess access;
}
