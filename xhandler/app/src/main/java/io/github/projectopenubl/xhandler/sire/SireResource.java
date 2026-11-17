package io.github.projectopenubl.xhandler.sire;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.audit.Auditor;
import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.company.CompanyResource;
import io.github.projectopenubl.xhandler.jobs.Jobs;
import io.github.projectopenubl.xhandler.reports.RegisterRows;
import io.github.projectopenubl.xhandler.status.StatusResource;
import io.github.projectopenubl.xhandler.storage.FileStore;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SIRE per company, register ({@code rvie} sales, {@code rce} purchases) and period (DESIGN.md
 * §11.7): request SUNAT's proposal, see its reconciliation with the stored documents, submit the
 * replacement. SUNAT is called by jobs; these endpoints answer {@code 202}.
 */
@Path("/api/v1/tenants/{tenantId}/companies/{companyId}/sire/{book}/{period}")
public class SireResource {

    public record SirePeriodView(
            UUID id, String book, String period, String sunatEnv, String status, String detail, JsonNode reconciliation,
            boolean proposalFile, boolean replacementFile, Instant createdAt, Instant updatedAt) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    private final TenantAccess access;
    private final Jobs jobs;
    private final EntityManager em;
    private final FileStore files;
    private final Auditor auditor;

    public SireResource(TenantAccess access, Jobs jobs, EntityManager em, FileStore files, Auditor auditor) {
        this.access = access;
        this.jobs = jobs;
        this.em = em;
        this.files = files;
        this.auditor = auditor;
    }

    private static void check(String book, String period) {
        if (!"rvie".equals(book) && !"rce".equals(book)) {
            throw ApiException.notFound();
        }
        RegisterRows.month(period);
    }

    /** Asks SUNAT for its proposal (again, to start over after an error or a change). */
    @POST
    @Path("/proposal")
    @Transactional
    public Response requestProposal(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @PathParam("book") String book, @PathParam("period") String period) {
        check(book, period);
        TenantScope scope = access.enter(tenantId, Role.OPERATOR);
        Company company = CompanyResource.find(companyId);
        if ("beta".equals(company.sunatEnv)) {
            throw ApiException.conflict("sire_not_available", "SUNAT no tiene ambiente de pruebas para SIRE.");
        }
        UUID id = Ids.newId();
        @SuppressWarnings("unchecked")
        List<Object[]> existing = em.createNativeQuery("""
                        SELECT id, status FROM xhandler.sire_period WHERE company_id = :c AND sunat_env = :env AND book = :b AND period = :p FOR UPDATE""")
                .setParameter("c", companyId).setParameter("env", company.sunatEnv).setParameter("b", book).setParameter("p", period)
                .getResultList();
        if (existing.isEmpty()) {
            em.createNativeQuery("""
                            INSERT INTO xhandler.sire_period (id, tenant_id, company_id, sunat_env, book, period, status, requested_by)
                            VALUES (:id, :t, :c, :env, :b, :p, 'requested', :by)""")
                    .setParameter("id", id).setParameter("t", tenantId).setParameter("c", companyId).setParameter("env", company.sunatEnv)
                    .setParameter("b", book).setParameter("p", period).setParameter("by", scope.actor().auditName()).executeUpdate();
        } else {
            id = (UUID) existing.get(0)[0];
            String status = (String) existing.get(0)[1];
            if ("submitting".equals(status) || "submitted".equals(status)) {
                throw ApiException.conflict("sire_in_progress", "El reemplazo está en proceso en SUNAT; espere su resultado.");
            }
            em.createNativeQuery("""
                            UPDATE xhandler.sire_period SET status = 'requested', proposal_ticket = NULL, reconciliation = NULL, detail = NULL,
                                requested_by = :by, updated_at = now() WHERE id = :id""")
                    .setParameter("by", scope.actor().auditName()).setParameter("id", id).executeUpdate();
        }
        jobs.enqueue(scope, SireJobs.Request.NAME, 3, Map.of("sire_period_id", id.toString()), List.of("sire:" + id), null);
        auditor.record(scope, "sire.proposal_requested", "company", companyId, Map.of("book", book, "period", period));
        return Response.status(202).entity(view(id)).build();
    }

    /** Submits the stored rows as the replacement of SUNAT's proposal. */
    @POST
    @Path("/replace")
    @Transactional
    public Response replace(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @PathParam("book") String book,
            @PathParam("period") String period) {
        check(book, period);
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        Company company = CompanyResource.find(companyId);
        UUID id = find(companyId, company.sunatEnv, book, period);
        int moved = em.createNativeQuery("""
                        UPDATE xhandler.sire_period SET status = 'submitting', detail = NULL, updated_at = now()
                        WHERE id = :id AND status IN ('proposal_ready', 'error') AND proposal_ref IS NOT NULL""")
                .setParameter("id", id).executeUpdate();
        if (moved == 0) {
            throw ApiException.conflict("sire_proposal_missing", "Solicite primero la propuesta de SUNAT y revise la conciliación.");
        }
        jobs.enqueue(scope, SireJobs.Replace.NAME, 3, Map.of("sire_period_id", id.toString()), List.of("sire:" + id), null);
        auditor.record(scope, "sire.replacement_submitted", "company", companyId, Map.of("book", book, "period", period));
        return Response.status(202).entity(view(id)).build();
    }

    @GET
    @Transactional
    public SirePeriodView get(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @PathParam("book") String book,
            @PathParam("period") String period) {
        check(book, period);
        access.enter(tenantId, Role.VIEWER);
        Company company = CompanyResource.find(companyId);
        return view(find(companyId, company.sunatEnv, book, period));
    }

    /** SUNAT's proposal ({@code proposal}) or the submitted replacement ({@code replacement}), as SUNAT's ZIP. */
    @GET
    @Path("/files/{kind}")
    @Transactional
    public Response file(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @PathParam("book") String book,
            @PathParam("period") String period, @PathParam("kind") String kind) {
        check(book, period);
        TenantScope scope = access.enter(tenantId, Role.VIEWER);
        Company company = CompanyResource.find(companyId);
        UUID id = find(companyId, company.sunatEnv, book, period);
        String column = switch (kind) {
            case "proposal" -> "proposal_ref";
            case "replacement" -> "replacement_ref";
            default -> throw ApiException.notFound();
        };
        List<?> ref = em.createNativeQuery("SELECT " + column + " FROM xhandler.sire_period WHERE id = :id").setParameter("id", id).getResultList();
        if (ref.isEmpty() || ref.get(0) == null) {
            throw ApiException.notFound();
        }
        auditor.record(scope, "sire.file_read", "company", companyId, Map.of("book", book, "period", period, "kind", kind));
        return Response.ok(files.get((String) ref.get(0))).type("application/zip")
                .header("Content-Disposition", "attachment; filename=\"sire-" + book + "-" + period + "-" + kind + ".zip\"").build();
    }

    private UUID find(UUID companyId, String sunatEnv, String book, String period) {
        List<?> found = em.createNativeQuery("SELECT id FROM xhandler.sire_period WHERE company_id = :c AND sunat_env = :env AND book = :b AND period = :p")
                .setParameter("c", companyId).setParameter("env", sunatEnv).setParameter("b", book).setParameter("p", period).getResultList();
        if (found.isEmpty()) {
            throw ApiException.notFound();
        }
        return (UUID) found.get(0);
    }

    private SirePeriodView view(UUID id) {
        Object[] r = (Object[]) em.createNativeQuery("""
                        SELECT id, book, period, sunat_env, status, detail, cast(reconciliation AS text), proposal_ref IS NOT NULL,
                               replacement_ref IS NOT NULL, created_at, updated_at
                        FROM xhandler.sire_period WHERE id = :id""").setParameter("id", id).getSingleResult();
        JsonNode reconciliation;
        try {
            reconciliation = r[6] == null ? null : JSON.readTree((String) r[6]);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return new SirePeriodView((UUID) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4], (String) r[5], reconciliation,
                (Boolean) r[7], (Boolean) r[8], StatusResource.toInstant(r[9]), StatusResource.toInstant(r[10]));
    }
}
