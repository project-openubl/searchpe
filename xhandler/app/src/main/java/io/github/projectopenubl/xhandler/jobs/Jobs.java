package io.github.projectopenubl.xhandler.jobs;

import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates tenant jobs (reports, exports, webhooks...). SUNAT attempts are never created here: the
 * reconciler is their only creator (DESIGN.md §24.1).
 */
@ApplicationScoped
public class Jobs {

    private final JobStore store;

    public Jobs(JobStore store) {
        this.store = store;
    }

    /** Enqueues a job in the open tenant; it runs after this transaction commits. */
    @Transactional(Transactional.TxType.MANDATORY)
    public Job enqueue(TenantScope scope, String name, int priorityClass, Map<String, Object> args, List<String> resources,
            String correlationId) {
        Job job = new Job();
        job.id = Ids.newId();
        job.tenantId = scope.tenantId();
        job.name = name;
        job.priorityClass = (short) priorityClass;
        job.args = args;
        job.reservedResources = resources;
        job.correlationId = correlationId;
        job.runAt = Instant.now();
        job.createdAt = job.runAt;
        job.updatedAt = job.runAt;
        job.persist();
        store.notifyWakeup();
        return job;
    }

    /** Asks a job to stop: waiting jobs are canceled at once, running ones at their next safe point. */
    @Transactional(Transactional.TxType.MANDATORY)
    public String cancel(UUID jobId) {
        Job job = Job.findById(jobId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (job == null) {
            return null;
        }
        switch (job.state) {
            case "waiting" -> {
                job.state = "canceled";
                job.finishedAt = Instant.now();
            }
            case "running" -> {
                job.state = "canceling";
                Job.getEntityManager().createNativeQuery("SELECT pg_notify('xhandler_job_cancel', :id)")
                        .setParameter("id", jobId.toString())
                        .getSingleResult();
            }
            default -> {
                // Already finished or canceling: nothing to do
            }
        }
        job.updatedAt = Instant.now();
        return job.state;
    }
}
