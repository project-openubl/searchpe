package io.github.projectopenubl.xhandler.adapters;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.audit.Auditor;
import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.company.CompanyResource;
import io.github.projectopenubl.xhandler.documents.DocumentView;
import io.github.projectopenubl.xhandler.documents.EmitService;
import io.github.projectopenubl.xhandler.storage.FileStore;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/** Inputs other than native JSON (DESIGN.md §5): UBL XML upload, SFS TXT, and tokens for the Nubefact-compatible API. */
@Path("/api/v1/tenants/{tenantId}/companies/{companyId}")
public class AdapterResource {

    public record SfsRequest(@NotBlank String fileName, @NotBlank String cab, @NotBlank String det) {}

    public record TokenRequest(@NotBlank @Size(max = 100) String label) {}

    /** A new adapter token: {@code token} is shown only here. */
    public record NubefactToken(UUID id, String token, String url) {}

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TenantAccess access;
    private final UblUploadService ubl;
    private final EmitService emit;
    private final EntityManager em;
    private final Auditor auditor;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public AdapterResource(TenantAccess access, UblUploadService ubl, EmitService emit, EntityManager em, Auditor auditor,
            com.fasterxml.jackson.databind.ObjectMapper json) {
        this.access = access;
        this.ubl = ubl;
        this.emit = emit;
        this.em = em;
        this.auditor = auditor;
        this.json = json;
    }

    /** A UBL XML built elsewhere: validated, signed if unsigned, stored and sent. */
    @APIResponse(responseCode = "201", description = "Documento recibido y emitido.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DocumentView.class)))
    @Operation(operationId = "uploadUblDocument")
    @POST
    @Path("/documents/ubl")
    @Consumes({"application/xml", "text/xml"})
    public Response uploadUbl(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @HeaderParam("X-Correlation-Id") String correlationId, byte[] xml) {
        DocumentView view = ubl.upload(tenantId, companyId, xml, correlationId == null ? UUID.randomUUID().toString() : correlationId);
        return Response.status(201).entity(view).build();
    }

    /** SUNAT SFS TXT files (.CAB/.NOT + .DET) converted to native JSON; the document keeps its number. */
    @APIResponse(responseCode = "201", description = "Documento emitido (también la respuesta repetida de una Idempotency-Key ya usada, sin access_url).",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DocumentView.class)))
    @APIResponse(responseCode = "402", description = "Sin cuota de documentos en el plan (quota_exceeded).",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @APIResponse(responseCode = "409", description = "Conflicto: Idempotency-Key en proceso, número ya usado, empresa desactivada o no autorizada.",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @APIResponse(responseCode = "422", description = "Documento o solicitud inválidos, o Idempotency-Key reutilizada con otra solicitud.",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @APIResponse(responseCode = "503", description = "Servicio o serie ocupados (busy): reintente en unos segundos.",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @Operation(operationId = "emitSfsDocument")
    @POST
    @Path("/documents/sfs")
    public Response sfs(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @HeaderParam("Idempotency-Key") String idempotencyKey, @Valid SfsRequest request) {
        SfsAdapter.Converted converted = SfsAdapter.convert(request.fileName(), request.cab(), request.det());
        String ruc = QuarkusTransaction.requiringNew().call(() -> {
            access.enter(tenantId, Role.OPERATOR);
            return CompanyResource.find(companyId).ruc;
        });
        if (!ruc.equals(converted.ruc())) {
            throw ApiException.invalid("invalid_document", "El RUC del archivo no es el de la empresa.", null);
        }
        EmitService.Emitted emitted = emit.emitNumbered(tenantId, companyId, idempotencyKey,
                new EmitService.EmitRequest(converted.document(), converted.serie(), null, Map.of("adapter", "sfs")), converted.numero(),
                UUID.randomUUID().toString(), false);
        return Response.status(emitted.status()).entity(emitted.body()).build();
    }

    /** A token for the Nubefact-compatible API (shown once). */
    @APIResponse(responseCode = "201", description = "Token creado; se muestra solo esta vez.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = NubefactToken.class)))
    @Operation(operationId = "createNubefactToken")
    @POST
    @Path("/adapter-tokens/nubefact")
    @Transactional
    public Response createToken(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @Valid TokenRequest request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        Company company = CompanyResource.find(companyId);
        byte[] raw = new byte[24];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        UUID id = Ids.newId();
        em.createNativeQuery("""
                        INSERT INTO xhandler.adapter_token (id, tenant_id, company_id, kind, token_hash, label, created_by)
                        VALUES (:id, :t, :c, 'nubefact', :h, :l, :by)""")
                .setParameter("id", id).setParameter("t", tenantId).setParameter("c", company.id).setParameter("h", hash(token))
                .setParameter("l", request.label()).setParameter("by", scope.actor().auditName()).executeUpdate();
        auditor.record(scope, "adapter_token.created", "company", companyId, Map.of("kind", "nubefact", "label", request.label()));
        return Response.status(201).entity(new NubefactToken(id, token, "/api/v1/public/nubefact/" + company.id)).build();
    }

    /** The company's adapter tokens (never the token itself). */
    @Operation(operationId = "listAdapterTokens")
    @jakarta.ws.rs.GET
    @Path("/adapter-tokens")
    @Transactional
    public java.util.List<AdapterTokenView> listTokens(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        access.enter(tenantId, Role.ADMIN);
        CompanyResource.find(companyId);
        @SuppressWarnings("unchecked")
        java.util.List<Object[]> rows = em.createNativeQuery("""
                        SELECT id, kind, label, created_by, created_at, revoked_at FROM xhandler.adapter_token WHERE company_id = :c ORDER BY created_at DESC""")
                .setParameter("c", companyId).getResultList();
        return rows.stream().map(r -> new AdapterTokenView((UUID) r[0], (String) r[1], (String) r[2], (String) r[3],
                io.github.projectopenubl.xhandler.status.StatusResource.toInstant(r[4]),
                io.github.projectopenubl.xhandler.status.StatusResource.toInstant(r[5]))).toList();
    }

    /** An adapter token as listed: never the token itself. */
    public record AdapterTokenView(UUID id, String kind, String label, String createdBy, java.time.Instant createdAt,
            java.time.Instant revokedAt) {}

    @APIResponse(responseCode = "204", description = "Token revocado.")
    @Operation(operationId = "revokeAdapterToken")
    @DELETE
    @Path("/adapter-tokens/{tokenId}")
    @Transactional
    public Response revokeToken(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @PathParam("tokenId") UUID tokenId) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        int revoked = em.createNativeQuery("UPDATE xhandler.adapter_token SET revoked_at = now() WHERE id = :id AND company_id = :c AND revoked_at IS NULL")
                .setParameter("id", tokenId).setParameter("c", companyId).executeUpdate();
        if (revoked == 0) {
            throw ApiException.notFound();
        }
        auditor.record(scope, "adapter_token.revoked", "company", companyId, Map.of("token_id", tokenId.toString()));
        return Response.noContent().build();
    }

    static String hash(String token) {
        return FileStore.sha256(token.getBytes(StandardCharsets.UTF_8));
    }
}
