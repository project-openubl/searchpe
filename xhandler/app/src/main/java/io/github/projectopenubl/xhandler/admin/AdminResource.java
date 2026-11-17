package io.github.projectopenubl.xhandler.admin;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.documents.Document;
import io.github.projectopenubl.xhandler.documents.DocumentControl;
import io.github.projectopenubl.xhandler.jobs.JobStore;
import io.github.projectopenubl.xhandler.secrets.KeyRotation;
import io.github.projectopenubl.xhandler.tenancy.Actor;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.Tenant;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import io.github.projectopenubl.xhandler.audit.Auditor;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Platform administration, used by xhandler-admin (DESIGN.md §15, §24.1). Every call needs the
 * {@code platform-admin} role; actions on tenant data are written to that tenant's audit log.
 */
@Path("/api/admin/v1")
public class AdminResource {

    public record DocumentState(UUID id, UUID tenantId, String serieNumero, String status, String holdReason, Instant nextAttemptAt,
            int attempts) {}

    public record BulkRetry(UUID companyId, String status, LocalDate from, LocalDate to) {}

    public record Count(int count) {}

    public record Endpoint(String id, String breakerState, Instant breakerUntil, int concurrencyCap, boolean paused) {}

    public record EndpointUpdate(@Min(1) Integer concurrencyCap) {}

    public record JobState(UUID id, String state) {}

    public record Migration(String version, String description, String state, java.time.LocalDateTime installedOn) {}

    private final PlatformAdmin admin;
    private final TenantAccess access;
    private final DocumentControl control;
    private final JobStore jobs;
    private final KeyRotation rotation;
    private final Auditor auditor;
    private final EntityManager em;

    public AdminResource(PlatformAdmin admin, TenantAccess access, DocumentControl control, JobStore jobs, KeyRotation rotation,
            Auditor auditor, EntityManager em) {
        this.admin = admin;
        this.access = access;
        this.control = control;
        this.jobs = jobs;
        this.rotation = rotation;
        this.auditor = auditor;
        this.em = em;
    }

    // --- documents (requeue-document, retry, hold, release) ---------------------

    @POST
    @Path("/documents/{id}/retry")
    @Transactional
    public DocumentState retry(@PathParam("id") UUID id) {
        return view(control.retry(openDocumentTenant(id), id));
    }

    @POST
    @Path("/documents/{id}/hold")
    @Transactional
    public DocumentState hold(@PathParam("id") UUID id) {
        return view(control.hold(openDocumentTenant(id), id));
    }

    @POST
    @Path("/documents/{id}/release")
    @Transactional
    public DocumentState release(@PathParam("id") UUID id) {
        return view(control.release(openDocumentTenant(id), id));
    }

    @POST
    @Path("/tenants/{tenantId}/retry")
    @Transactional
    public Count bulkRetry(@PathParam("tenantId") UUID tenantId, BulkRetry filter) {
        TenantScope scope = openTenant(tenantId);
        BulkRetry f = filter == null ? new BulkRetry(null, null, null, null) : filter;
        return new Count(control.retryWhere(scope, f.companyId(), f.status(), f.from(), f.to()));
    }

    // --- holds per tenant and company ---------------------------------------------

    @POST
    @Path("/tenants/{tenantId}/hold")
    @Transactional
    public Map<String, Object> holdTenant(@PathParam("tenantId") UUID tenantId) {
        return pauseTenant(tenantId, true);
    }

    @POST
    @Path("/tenants/{tenantId}/release")
    @Transactional
    public Map<String, Object> releaseTenant(@PathParam("tenantId") UUID tenantId) {
        return pauseTenant(tenantId, false);
    }

    @POST
    @Path("/tenants/{tenantId}/companies/{companyId}/hold")
    @Transactional
    public Map<String, Object> holdCompany(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        return pauseCompany(tenantId, companyId, true);
    }

    @POST
    @Path("/tenants/{tenantId}/companies/{companyId}/release")
    @Transactional
    public Map<String, Object> releaseCompany(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        return pauseCompany(tenantId, companyId, false);
    }

    /** The OSE obligation is a regulatory status set by the platform (§3.2), not by the tenant. */
    @PUT
    @Path("/tenants/{tenantId}/companies/{companyId}/ose-obligated")
    @Transactional
    public Map<String, Object> oseObligated(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            Map<String, Boolean> body) {
        TenantScope scope = openTenant(tenantId);
        Company company = Company.findById(companyId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (company == null) {
            throw ApiException.notFound();
        }
        boolean value = Boolean.TRUE.equals(body == null ? null : body.get("ose_obligated"));
        company.oseObligated = value;
        auditor.record(scope, "company.ose_obligated", "company", companyId, Map.of("value", value));
        return Map.of("company_id", companyId, "ose_obligated", value);
    }

    // --- endpoints (endpoint-status, hold/release per endpoint) ---------------------

    @GET
    @Path("/endpoints")
    @Transactional
    public List<Endpoint> endpoints() {
        admin.require();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT id, breaker_state, breaker_until, concurrency_cap, paused FROM xhandler.sunat_endpoint ORDER BY id")
                .getResultList();
        return rows.stream().map(AdminResource::endpoint).toList();
    }

    @POST
    @Path("/endpoints/{id}/hold")
    @Transactional
    public Endpoint holdEndpoint(@PathParam("id") String id) {
        return updateEndpoint(id, "paused = true");
    }

    @POST
    @Path("/endpoints/{id}/release")
    @Transactional
    public Endpoint releaseEndpoint(@PathParam("id") String id) {
        return updateEndpoint(id, "paused = false");
    }

    @PUT
    @Path("/endpoints/{id}")
    @Transactional
    public Endpoint setEndpoint(@PathParam("id") String id, EndpointUpdate update) {
        if (update == null || update.concurrencyCap() == null || update.concurrencyCap() < 1) {
            throw ApiException.invalid("invalid_request", "concurrency_cap debe ser al menos 1.", null);
        }
        return updateEndpoint(id, "concurrency_cap = " + update.concurrencyCap());
    }

    // --- jobs (cancel-job, purge-jobs) ------------------------------------------------

    @POST
    @Path("/jobs/{id}/cancel")
    @Transactional
    public JobState cancelJob(@PathParam("id") UUID id) {
        admin.require();
        Object state = em.createNativeQuery("SELECT xhandler.cancel_job_any(:id)").setParameter("id", id).getSingleResult();
        if (state == null) {
            throw ApiException.conflict("job_not_cancelable", "El trabajo no existe o ya terminó.");
        }
        return new JobState(id, state.toString());
    }

    @POST
    @Path("/jobs/purge")
    @Transactional
    public Count purgeJobs(@jakarta.ws.rs.QueryParam("older_than_hours") Integer hours) {
        admin.require();
        return new Count(jobs.purge(java.time.Duration.ofHours(hours == null ? 24 * 7 : Math.max(1, hours))));
    }

    // --- keys and migrations (rotate-keys, migration-status) --------------------------

    @POST
    @Path("/keys/rotate")
    public KeyRotation.Result rotateKeys() {
        admin.require();
        return rotation.rewrapAll();
    }

    @GET
    @Path("/migrations")
    @Transactional
    public List<Migration> migrations() {
        admin.require();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT version, description, success, installed_on FROM xhandler.migration_history()").getResultList();
        return rows.stream().map(r -> new Migration((String) r[0], (String) r[1], Boolean.TRUE.equals(r[2]) ? "Success" : "Failed",
                (java.time.LocalDateTime) r[3])).toList();
    }

    // --- data repair -------------------------------------------------------------------

    /** Creates missing partitions now (normally a daily schedule). */
    @POST
    @Path("/repair/partitions")
    @Transactional
    public Map<String, Object> repairPartitions() {
        admin.require();
        LocalDate today = io.github.projectopenubl.xhandler.documents.Deadlines.todayInLima();
        em.createNativeQuery("SELECT xhandler.ensure_partitions(:day)").setParameter("day", today).getSingleResult();
        return Map.of("day", today.toString());
    }

    /** Makes due again any non-definitive document no automatic attempt would pick up. */
    @POST
    @Path("/repair/requeue-stuck")
    @Transactional
    public Count requeueStuck() {
        admin.require();
        int n = ((Number) em.createNativeQuery("SELECT xhandler.requeue_stuck_documents()").getSingleResult()).intValue();
        notifyReconcile();
        return new Count(n);
    }

    @GET
    @Path("/jobs/{id}")
    @Transactional
    public Map<String, Object> job(@PathParam("id") UUID id) {
        admin.require();
        List<?> rows = em.createNativeQuery("SELECT name, state, worker, CAST(error AS text) FROM xhandler.job_summary(:id)")
                .setParameter("id", id)
                .getResultList();
        if (rows.isEmpty()) {
            throw ApiException.notFound();
        }
        Object[] r = (Object[]) rows.get(0);
        Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("id", id);
        view.put("name", r[0]);
        view.put("state", r[1]);
        view.put("worker", r[2]);
        view.put("error", r[3]);
        return view;
    }

    // ---------------------------------------------------------------------------------

    private TenantScope openDocumentTenant(UUID documentId) {
        String name = admin.require();
        Object tenant = em.createNativeQuery("SELECT xhandler.document_tenant(:id)").setParameter("id", documentId).getSingleResult();
        if (tenant == null) {
            throw ApiException.notFound();
        }
        return scope((UUID) tenant, name);
    }

    private TenantScope openTenant(UUID tenantId) {
        return scope(tenantId, admin.require());
    }

    private TenantScope scope(UUID tenantId, String adminName) {
        access.enterAsPlatform(tenantId);
        if (Tenant.findById(tenantId) == null) {
            throw ApiException.notFound();
        }
        return new TenantScope(tenantId, new Actor("admin", adminName.substring("admin:".length()), false, false), Role.PLATFORM);
    }

    private Map<String, Object> pauseTenant(UUID tenantId, boolean paused) {
        TenantScope scope = openTenant(tenantId);
        Tenant tenant = Tenant.findById(tenantId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        tenant.sendingPaused = paused;
        auditor.record(scope, paused ? "tenant.sending_held" : "tenant.sending_released", "tenant", tenantId);
        notifyReconcile();
        return Map.of("tenant_id", tenantId, "sending_paused", paused);
    }

    private Map<String, Object> pauseCompany(UUID tenantId, UUID companyId, boolean paused) {
        TenantScope scope = openTenant(tenantId);
        Company company = Company.findById(companyId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (company == null) {
            throw ApiException.notFound();
        }
        company.sendingPaused = paused;
        auditor.record(scope, paused ? "company.sending_held" : "company.sending_released", "company", companyId);
        notifyReconcile();
        return Map.of("company_id", companyId, "sending_paused", paused);
    }

    private Endpoint updateEndpoint(String id, String assignment) {
        admin.require();
        int updated = em.createNativeQuery("UPDATE xhandler.sunat_endpoint SET " + assignment + ", updated_at = now() WHERE id = :id")
                .setParameter("id", id)
                .executeUpdate();
        if (updated == 0) {
            throw ApiException.notFound();
        }
        notifyReconcile();
        Object[] row = (Object[]) em.createNativeQuery(
                        "SELECT id, breaker_state, breaker_until, concurrency_cap, paused FROM xhandler.sunat_endpoint WHERE id = :id")
                .setParameter("id", id)
                .getSingleResult();
        return endpoint(row);
    }

    private void notifyReconcile() {
        em.createNativeQuery("SELECT pg_notify('xhandler_reconcile', '')").getSingleResult();
    }

    private static Endpoint endpoint(Object[] r) {
        return new Endpoint((String) r[0], (String) r[1], io.github.projectopenubl.xhandler.status.StatusResource.toInstant(r[2]),
                ((Number) r[3]).intValue(), (Boolean) r[4]);
    }

    private static DocumentState view(Document d) {
        return new DocumentState(d.id, d.tenantId, d.serieNumero(), d.status, d.holdReason, d.nextAttemptAt, d.attempts);
    }
}
