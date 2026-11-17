package io.github.projectopenubl.xhandler.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The job system's database operations, all through the SECURITY DEFINER functions of V4 (they
 * cross tenants). Stateless; every method runs in the caller's transaction.
 */
@ApplicationScoped
public class JobStore {

    /** A job a worker started. */
    public record Started(UUID id, long seq, UUID tenantId, String name, int attempt, JsonNode args, String correlationId) {}

    public record Candidate(UUID id, long seq) {}

    public record DeadJob(UUID id, long seq, String worker) {}

    private final EntityManager em;
    private final ObjectMapper json;

    public JobStore(EntityManager em, ObjectMapper json) {
        this.em = em;
        this.json = json;
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public int unblock() {
        return ((Number) em.createNativeQuery("SELECT xhandler.unblock_jobs()").getSingleResult()).intValue();
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public List<Candidate> claimable(int limit) {
        List<Object[]> rows = em.createNativeQuery("SELECT id, seq FROM xhandler.claimable_jobs(:n)", Object[].class)
                .setParameter("n", limit)
                .getResultList();
        return rows.stream().map(r -> new Candidate((UUID) r[0], ((Number) r[1]).longValue())).toList();
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public Optional<Started> start(Candidate candidate, String worker) {
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT tenant_id, name, attempt, CAST(args AS text), correlation_id FROM xhandler.start_job(:id, :worker)",
                        Object[].class)
                .setParameter("id", candidate.id())
                .setParameter("worker", worker)
                .getResultList();
        return rows.stream().findFirst().map(r -> new Started(candidate.id(), candidate.seq(), (UUID) r[0], (String) r[1],
                ((Number) r[2]).intValue(), parse((String) r[3]), (String) r[4]));
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public boolean finish(UUID id, String worker, String state, Map<String, Object> result, Map<String, Object> error) {
        Object done = em.createNativeQuery(
                        "SELECT xhandler.finish_job(:id, :worker, :state, CAST(:result AS jsonb), CAST(:error AS jsonb))")
                .setParameter("id", id)
                .setParameter("worker", worker)
                .setParameter("state", state)
                .setParameter("result", write(result))
                .setParameter("error", write(error))
                .getSingleResult();
        notifyWakeup();
        return Boolean.TRUE.equals(done);
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public boolean cancelRequested(UUID id) {
        return Boolean.TRUE.equals(em.createNativeQuery("SELECT xhandler.job_cancel_requested(:id)")
                .setParameter("id", id)
                .getSingleResult());
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public List<DeadJob> jobsOfDeadWorkers(Duration ttl) {
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT id, seq, worker FROM xhandler.jobs_of_dead_workers(make_interval(secs => :ttl))", Object[].class)
                .setParameter("ttl", (double) ttl.toSeconds())
                .getResultList();
        return rows.stream().map(r -> new DeadJob((UUID) r[0], ((Number) r[1]).longValue(), (String) r[2])).toList();
    }

    /** Takes a job's ownership lock until the transaction ends; true means its worker no longer holds it. */
    @Transactional(Transactional.TxType.MANDATORY)
    public boolean tryJobLockForTransaction(long seq) {
        return Boolean.TRUE.equals(em.createNativeQuery("SELECT pg_try_advisory_xact_lock(:seq)")
                .setParameter("seq", seq)
                .getSingleResult());
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public boolean recover(UUID id) {
        return Boolean.TRUE.equals(em.createNativeQuery("SELECT xhandler.recover_job(:id)")
                .setParameter("id", id)
                .getSingleResult());
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public int deleteDeadWorkers(Duration ttl) {
        return ((Number) em.createNativeQuery("SELECT xhandler.delete_dead_workers(make_interval(secs => :ttl))")
                .setParameter("ttl", (double) ttl.toSeconds())
                .getSingleResult()).intValue();
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public int dispatchSchedules() {
        return ((Number) em.createNativeQuery("SELECT xhandler.dispatch_schedules()").getSingleResult()).intValue();
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public int purge(Duration age) {
        return ((Number) em.createNativeQuery("SELECT xhandler.purge_jobs(make_interval(secs => :age))")
                .setParameter("age", (double) age.toSeconds())
                .getSingleResult()).intValue();
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public void updateBreakers(int minCalls, Duration baseOpen, Duration maxOpen) {
        em.createNativeQuery("SELECT xhandler.update_breakers(:min, make_interval(secs => :base), make_interval(secs => :max))")
                .setParameter("min", minCalls)
                .setParameter("base", (double) baseOpen.toSeconds())
                .setParameter("max", (double) maxOpen.toSeconds())
                .getSingleResult();
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public int reconcile(Duration lease, Map<String, Integer> slots) {
        return ((Number) em.createNativeQuery("SELECT xhandler.reconcile(make_interval(secs => :lease), CAST(:slots AS jsonb))")
                .setParameter("lease", (double) lease.toSeconds())
                .setParameter("slots", write(slots))
                .getSingleResult()).intValue();
    }

    /** A platform job (no tenant), e.g. from the admin API. */
    @Transactional(Transactional.TxType.MANDATORY)
    public UUID enqueuePlatform(String name, int priorityClass, Map<String, Object> args, List<String> resources) {
        UUID id = io.github.projectopenubl.xhandler.api.Ids.newId();
        em.createNativeQuery("SELECT xhandler.enqueue_platform_job(:id, :name, CAST(:priority AS smallint), CAST(:args AS jsonb), CAST(:resources AS text[]))")
                .setParameter("id", id)
                .setParameter("name", name)
                .setParameter("priority", priorityClass)
                .setParameter("args", write(args))
                .setParameter("resources", "{" + String.join(",", resources) + "}")
                .getSingleResult();
        return id;
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public void notifyWakeup() {
        em.createNativeQuery("SELECT pg_notify('xhandler_job_wakeup', '')").getSingleResult();
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public void notifyReconcile() {
        em.createNativeQuery("SELECT pg_notify('xhandler_reconcile', '')").getSingleResult();
    }

    private String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private JsonNode parse(String value) {
        try {
            return json.readTree(value == null ? "{}" : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
