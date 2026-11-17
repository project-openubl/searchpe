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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

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

    /**
     * {@code acknowledgeDifferences}: submit although the stored rows don't match SUNAT's proposal
     * (what SUNAT has and the platform doesn't is left out). {@code confirmNotReceived}: after an
     * unknown outcome, the user checked in SOL that SUNAT didn't get the earlier replacement.
     */
    public record ReplaceRequest(Boolean acknowledgeDifferences, Boolean confirmNotReceived) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    private final TenantAccess access;
    private final Jobs jobs;
    private final EntityManager em;
    private final FileStore files;
    private final Auditor auditor;
    private final RegisterRows registers;

    public SireResource(TenantAccess access, Jobs jobs, EntityManager em, FileStore files, Auditor auditor, RegisterRows registers) {
        this.registers = registers;
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
    @APIResponse(responseCode = "202", description = "Propuesta solicitada a SUNAT; la trae un trabajo.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SirePeriodView.class)))
    @Operation(operationId = "requestSireProposal")
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
        int created = em.createNativeQuery("""
                        INSERT INTO xhandler.sire_period (id, tenant_id, company_id, sunat_env, book, period, status, requested_by)
                        VALUES (:id, :t, :c, :env, :b, :p, 'requested', :by) ON CONFLICT (company_id, sunat_env, book, period) DO NOTHING""")
                .setParameter("id", Ids.newId()).setParameter("t", tenantId).setParameter("c", companyId).setParameter("env", company.sunatEnv)
                .setParameter("b", book).setParameter("p", period).setParameter("by", scope.actor().auditName()).executeUpdate();
        Object[] existing = (Object[]) em.createNativeQuery("""
                        SELECT id, status FROM xhandler.sire_period WHERE company_id = :c AND sunat_env = :env AND book = :b AND period = :p FOR UPDATE""")
                .setParameter("c", companyId).setParameter("env", company.sunatEnv).setParameter("b", book).setParameter("p", period)
                .getSingleResult();
        UUID id = (UUID) existing[0];
        if (created == 0) {
            String status = (String) existing[1];
            if ("submitting".equals(status) || "submitted".equals(status)) {
                throw ApiException.conflict("sire_in_progress", "El reemplazo está en proceso en SUNAT; espere su resultado.");
            }
            if ("unknown".equals(status)) {
                throw ApiException.conflict("sire_unknown",
                        "No sabemos si SUNAT recibió el último reemplazo. Verifíquelo y confirme antes de continuar.");
            }
            if ("accepted".equals(status) || "proposal_ready".equals(status)) {
                // Starting over discards SUNAT's acceptance or an admin's review
                if (!scope.role().allows(Role.ADMIN)) {
                    throw ApiException.forbidden();
                }
            }
            em.createNativeQuery("""
                            UPDATE xhandler.sire_period SET status = 'requested', proposal_ticket = NULL, proposal_ref = NULL, reconciliation = NULL,
                                detail = NULL, resume_status = NULL, poll_attempts = 0, next_poll_at = now(), requested_by = :by, updated_at = now()
                            WHERE id = :id""")
                    .setParameter("by", scope.actor().auditName()).setParameter("id", id).executeUpdate();
        }
        jobs.enqueue(scope, SireJobs.Request.NAME, 3, Map.of("sire_period_id", id.toString()), List.of("sire:" + id), null);
        auditor.record(scope, "sire.proposal_requested", "company", companyId, Map.of("book", book, "period", period));
        return Response.status(202).entity(view(id)).build();
    }

    /** Submits the stored rows as the replacement of SUNAT's proposal. */
    @APIResponse(responseCode = "202", description = "Reemplazo enviado a SUNAT; lo sigue un trabajo.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SirePeriodView.class)))
    @Operation(operationId = "replaceSireProposal")
    @POST
    @Path("/replace")
    @Transactional
    public Response replace(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @PathParam("book") String book,
            @PathParam("period") String period, ReplaceRequest request) {
        check(book, period);
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        Company company = CompanyResource.find(companyId);
        UUID id = find(companyId, company.sunatEnv, book, period);
        boolean acknowledge = request != null && Boolean.TRUE.equals(request.acknowledgeDifferences());
        boolean confirmed = request != null && Boolean.TRUE.equals(request.confirmNotReceived());
        Object[] row = (Object[]) em.createNativeQuery("SELECT status, cast(reconciliation AS text) FROM xhandler.sire_period WHERE id = :id FOR UPDATE")
                .setParameter("id", id).getSingleResult();
        String status = (String) row[0];
        if ("unknown".equals(status) && !confirmed) {
            throw ApiException.conflict("sire_unknown",
                    "No sabemos si SUNAT recibió el último reemplazo. Verifique en SUNAT Operaciones en Línea y confirme que no lo recibió.");
        }
        if (row[1] == null || !List.of("proposal_ready", "error", "unknown").contains(status)) {
            throw ApiException.conflict("sire_proposal_missing", "Solicite primero la propuesta de SUNAT y revise la conciliación.");
        }
        boolean matches;
        try {
            matches = JSON.readTree((String) row[1]).path("matches").asBoolean(false);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        if (!matches && !acknowledge) {
            // The replacement is built from the stored rows only: what SUNAT has and the platform doesn't would be dropped
            throw ApiException.conflict("sire_differences",
                    "La propuesta de SUNAT no coincide con los comprobantes registrados. Revise la conciliación y confirme las diferencias.");
        }
        long unresolved = "rvie".equals(book) ? registers.unresolved(companyId, company.sunatEnv, period) : 0;
        if (unresolved > 0) {
            throw ApiException.conflict("sire_unresolved_documents",
                    "Hay " + unresolved + " comprobantes del periodo sin respuesta definitiva de SUNAT. Espere a que se resuelvan.");
        }
        List<RegisterRows.Row> rows = "rvie".equals(book) ? registers.sales(companyId, company.sunatEnv, period) : registers.purchases(companyId, period);
        long missing = rows.stream().filter(RegisterRows.Row::amountsMissing).count();
        if (missing > 0) {
            throw ApiException.conflict("amounts_pending",
                    missing + " comprobantes aún no tienen el desglose de impuestos; se completa en unos minutos.");
        }
        em.createNativeQuery("""
                        UPDATE xhandler.sire_period SET status = 'submitting', submitting_since = now(), replacement_ticket = NULL, detail = NULL,
                            poll_attempts = 0, next_poll_at = now(), updated_at = now() WHERE id = :id""")
                .setParameter("id", id).executeUpdate();
        jobs.enqueue(scope, SireJobs.Replace.NAME, 3, Map.of("sire_period_id", id.toString()), List.of("sire:" + id), null);
        auditor.record(scope, "sire.replacement_submitted", "company", companyId, Map.of("book", book, "period", period,
                "acknowledged_differences", String.valueOf(!matches), "confirmed_not_received", String.valueOf(confirmed)));
        return Response.status(202).entity(view(id)).build();
    }

    @Operation(operationId = "getSirePeriod")
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
    @APIResponse(responseCode = "200", description = "El archivo de SUNAT (ZIP).", content = {
            @Content(mediaType = "application/zip", schema = @Schema(type = SchemaType.STRING, format = "binary"))
    })
    @Operation(operationId = "downloadSireFile")
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
