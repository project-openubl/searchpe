package io.github.projectopenubl.xhandler.company;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.audit.Auditor;
import io.github.projectopenubl.xhandler.keys.PlatformKeys;
import io.github.projectopenubl.xhandler.notifications.Notifications;
import io.github.projectopenubl.xhandler.storage.FileStore;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PSE mode of a company (DESIGN.md §1.1, §3.2): the tenant authorizes the platform in SUNAT's SOL
 * and registers that authorization here (with evidence); then the platform signs with its PSE
 * certificate and sends with its own SOL users. Revoking it is notified (§26.6).
 */
@Path("/api/v1/tenants/{tenantId}/companies/{companyId}/pse")
public class PseResource {

    public record Authorization(@NotNull LocalDate authorizedAt, @NotBlank String evidenceBase64) {}

    public record EmissionMode(@NotBlank @Pattern(regexp = "software|pse") String emissionMode) {}

    public record AuthorizationView(UUID id, LocalDate authorizedAt, boolean evidence, String registeredBy, Instant approvedAt, Instant revokedAt) {}

    private final TenantAccess access;
    private final EntityManager em;
    private final Auditor auditor;
    private final FileStore files;
    private final Notifications notifications;
    private final PlatformKeys platformKeys;

    public PseResource(TenantAccess access, EntityManager em, Auditor auditor, FileStore files, Notifications notifications,
            PlatformKeys platformKeys) {
        this.access = access;
        this.em = em;
        this.auditor = auditor;
        this.files = files;
        this.notifications = notifications;
        this.platformKeys = platformKeys;
    }

    @GET
    @Transactional
    public List<AuthorizationView> list(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        access.enter(tenantId, Role.VIEWER);
        CompanyResource.find(companyId);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT id, authorized_at, evidence_ref IS NOT NULL, registered_by, approved_at, revoked_at FROM xhandler.pse_authorization
                        WHERE company_id = :c ORDER BY created_at DESC""").setParameter("c", companyId).getResultList();
        return rows.stream().map(r -> new AuthorizationView((UUID) r[0], toDate(r[1]), (Boolean) r[2], (String) r[3],
                io.github.projectopenubl.xhandler.status.StatusResource.toInstant(r[4]),
                io.github.projectopenubl.xhandler.status.StatusResource.toInstant(r[5]))).toList();
    }

    /** Registers the SOL authorization; the evidence (a PDF or image of SOL's confirmation) is kept. */
    @POST
    @Path("/authorization")
    @Transactional
    public AuthorizationView authorize(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @Valid Authorization request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        Company company = CompanyResource.findForUpdate(companyId);
        if (company.rucVerifiedAt == null) {
            throw ApiException.conflict("ruc_not_verified", "Verifique el RUC de la empresa antes de registrar la autorización de PSE.");
        }
        if (request.authorizedAt().isAfter(io.github.projectopenubl.xhandler.documents.Deadlines.todayInLima())) {
            throw ApiException.invalid("invalid_request", "La fecha de autorización no puede ser futura.", null);
        }
        if (!em.createNativeQuery("SELECT 1 FROM xhandler.pse_authorization WHERE company_id = :c AND revoked_at IS NULL")
                .setParameter("c", companyId).getResultList().isEmpty()) {
            throw ApiException.conflict("already_authorized", "La empresa ya tiene una autorización vigente.");
        }
        // Evidence of the SOL authorization (a PDF or image), reviewed by a platform admin before it counts
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(request.evidenceBase64());
        } catch (IllegalArgumentException e) {
            throw ApiException.invalid("invalid_request", "La evidencia debe estar en base64.", null);
        }
        if (bytes.length > 2 * 1024 * 1024) {
            throw ApiException.invalid("invalid_request", "La evidencia no debe pasar de 2 MB.", null);
        }
        boolean pdf = bytes.length > 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
        boolean png = bytes.length > 4 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
        boolean jpeg = bytes.length > 3 && (bytes[0] & 0xff) == 0xFF && (bytes[1] & 0xff) == 0xD8;
        if (!pdf && !png && !jpeg) {
            throw ApiException.invalid("invalid_request", "La evidencia debe ser un PDF, PNG o JPEG.", null);
        }
        String evidence = files.put(company.tenantId, bytes).key();
        UUID id = Ids.newId();
        em.createNativeQuery("""
                        INSERT INTO xhandler.pse_authorization (id, tenant_id, company_id, authorized_at, evidence_ref, registered_by)
                        VALUES (:id, :t, :c, :at, :e, :by)""")
                .setParameter("id", id).setParameter("t", tenantId).setParameter("c", companyId).setParameter("at", request.authorizedAt())
                .setParameter("e", evidence).setParameter("by", scope.actor().auditName()).executeUpdate();
        auditor.record(scope, "pse.authorization_registered", "company", companyId, Map.of("authorized_at", request.authorizedAt().toString()));
        return new AuthorizationView(id, request.authorizedAt(), true, scope.actor().auditName(), null, null);
    }

    /** The tenant revoked the platform in SOL: PSE stops, the company goes back to software mode. */
    @DELETE
    @Path("/authorization")
    @Transactional
    public Map<String, Object> revoke(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        Company company = CompanyResource.findForUpdate(companyId);
        long pending = ((Number) em.createNativeQuery("""
                        SELECT count(*) FROM xhandler.document WHERE company_id = :c AND emission_mode = 'pse'
                          AND status IN ('pending_send', 'in_progress', 'unknown', 'voiding')""").setParameter("c", companyId).getSingleResult())
                .longValue();
        if (pending > 0) {
            // The platform must finish what it signed as PSE before it stops being the company's PSE
            throw ApiException.conflict("pse_documents_pending",
                    "Hay " + pending + " documentos enviados como PSE sin respuesta definitiva. Revoque cuando terminen.");
        }
        int revoked = em.createNativeQuery("""
                        UPDATE xhandler.pse_authorization SET revoked_at = now(), revoked_by = :by WHERE company_id = :c AND revoked_at IS NULL""")
                .setParameter("by", scope.actor().auditName()).setParameter("c", companyId).executeUpdate();
        if (revoked == 0) {
            throw ApiException.notFound();
        }
        company.emissionMode = "software";
        auditor.record(scope, "pse.authorization_revoked", "company", companyId);
        notifications.publish(tenantId, Notifications.PSE_AUTHORIZATION, "Autorización de PSE revocada: " + company.ruc,
                "La empresa " + company.razonSocial + " dejó de autorizar a la plataforma como PSE. Sus documentos se firmarán con su propio "
                        + "certificado y se enviarán con su usuario SOL.", null);
        return Map.of("company_id", companyId, "emission_mode", company.emissionMode);
    }

    @PUT
    @Path("/mode")
    @Transactional
    public CompanyResource.CompanyView mode(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @Valid EmissionMode request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        Company company = CompanyResource.findForUpdate(companyId);
        if ("pse".equals(request.emissionMode())) {
            if (em.createNativeQuery("SELECT 1 FROM xhandler.pse_authorization WHERE company_id = :c AND revoked_at IS NULL AND approved_at IS NOT NULL")
                    .setParameter("c", companyId).getResultList().isEmpty()) {
                throw ApiException.conflict("pse_not_authorized", "La autorización a la plataforma como PSE no está registrada o aún no fue aprobada.");
            }
            if (company.oseObligated || "ose".equals(company.submissionRoute)) {
                // A company that sends through an OSE can't also send directly as PSE (RS 117-2017 art. 12.2)
                throw ApiException.conflict("pse_not_allowed", "La empresa emite a través de un OSE: no puede usar a la plataforma como PSE.");
            }
            if ("production".equals(company.sunatEnv) && platformKeys.active("pse").isEmpty()) {
                throw ApiException.conflict("pse_unavailable", "La plataforma aún no opera como PSE en producción.");
            }
        }
        company.emissionMode = request.emissionMode();
        auditor.record(scope, "company.emission_mode_changed", "company", companyId, Map.of("mode", request.emissionMode()));
        return CompanyResource.view(company);
    }

    static LocalDate toDate(Object value) {
        return value instanceof LocalDate d ? d : ((java.sql.Date) value).toLocalDate();
    }
}
