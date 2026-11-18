package io.github.projectopenubl.xhandler.documents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.api.Page;
import io.github.projectopenubl.xhandler.audit.Auditor;
import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.storage.FileStore;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/**
 * Manual issuing (DESIGN.md §9.1, §11.10): drafts are xbuilder documents without a number, edited
 * in xhandler-ui and then issued through the normal emit path.
 */
@Path("/api/v1/tenants/{tenantId}")
public class DraftResource {

    /** What a draft holds: the xbuilder document (kind and spec, no number), the series and labels. */
    public record DraftInput(
            @NotBlank String kind,
            @Schema(description = "El documento de xbuilder sin número (`serie_numero` no se acepta: se numera al emitir).")
            @jakarta.validation.constraints.NotNull ObjectNode spec,
            @Pattern(regexp = "[A-Z0-9]{4}", message = "debe tener 4 caracteres en mayúsculas o dígitos") String serie,
            Map<String, @Size(max = 200) String> labels,
            @Schema(description = "Al editar: la versión que se editó; si el borrador cambió desde entonces, 409 (draft_changed).")
            Integer version) {}

    public record DraftView(
            UUID id,
            String prn,
            UUID companyId,
            @Schema(description = "draft (en edición) o issued (emitido: ver document_id).") String status,
            int version,
            String kind,
            String tipoComprobante,
            String serie,
            JsonNode spec,
            Map<String, String> labels,
            DocumentView.Customer customer,
            String moneda,
            UUID documentId,
            String createdBy,
            Instant createdAt,
            Instant updatedAt) {}

    private static final int MAX_SPEC_BYTES = 512 * 1024;

    private final TenantAccess access;
    private final EmitService emit;
    private final Idempotency idempotency;
    private final Auditor auditor;
    private final ObjectMapper json;

    public DraftResource(TenantAccess access, EmitService emit, Idempotency idempotency, Auditor auditor, ObjectMapper json) {
        this.access = access;
        this.emit = emit;
        this.idempotency = idempotency;
        this.auditor = auditor;
        this.json = json;
    }

    @POST
    @Path("/companies/{companyId}/drafts")
    @APIResponse(responseCode = "201", description = "Borrador creado (también la respuesta repetida de una Idempotency-Key ya usada).",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DraftView.class)))
    @APIResponse(responseCode = "409", description = "Idempotency-Key en proceso.",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @APIResponse(responseCode = "422", description = "Borrador inválido, o Idempotency-Key reutilizada con otra solicitud.",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @Operation(operationId = "createDraft", summary = "Crea un borrador de documento, sin número (emisión manual)")
    public Response create(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @HeaderParam("Idempotency-Key") String idempotencyKey, @Valid DraftInput input) {
        checkInput(input);
        UUID claim = null;
        if (idempotencyKey != null) {
            // Authorize before touching the idempotency table
            QuarkusTransaction.requiringNew().run(() -> access.enter(tenantId, Role.OPERATOR));
            Idempotency.Begun begun = idempotency.begin(tenantId, idempotencyKey, hash(companyId, input));
            if (begun.replay() != null) {
                return Response.status(begun.replay().status()).entity(begun.replay().body()).build();
            }
            claim = begun.claim();
        }
        UUID owned = claim;
        try {
            DraftView view = QuarkusTransaction.requiringNew().call(() -> {
                TenantScope scope = access.enter(tenantId, Role.OPERATOR);
                Company company = Company.findById(companyId);
                if (company == null || company.deletedAt != null) {
                    throw ApiException.notFound();
                }
                Instant now = Instant.now();
                DocumentDraft draft = new DocumentDraft();
                draft.id = Ids.newId();
                draft.tenantId = tenantId;
                draft.companyId = companyId;
                apply(draft, input);
                draft.createdBy = scope.actor().auditName();
                draft.createdAt = now;
                draft.updatedAt = now;
                draft.persist();
                auditor.record(scope, "draft.created", "document_draft", draft.id);
                DraftView created = view(draft);
                if (idempotencyKey != null) {
                    idempotency.complete(tenantId, idempotencyKey, owned, 201, json.valueToTree(created));
                }
                return created;
            });
            return Response.status(201).entity(view).build();
        } catch (RuntimeException e) {
            if (owned != null) {
                idempotency.abandon(tenantId, idempotencyKey, owned);
            }
            throw e;
        }
    }

    @GET
    @Path("/drafts")
    @Transactional
    @Operation(operationId = "listDrafts", summary = "Borradores de documentos, los más recientes primero")
    public Page<DraftView> list(
            @PathParam("tenantId") UUID tenantId,
            @QueryParam("company_id") UUID companyId,
            @Schema(description = "draft o issued; sin filtro, ambos.") @QueryParam("status") String status,
            @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset) {
        access.enter(tenantId, Role.VIEWER);
        StringBuilder query = new StringBuilder("deletedAt is null");
        Map<String, Object> params = new HashMap<>();
        if (companyId != null) {
            query.append(" and companyId = :companyId");
            params.put("companyId", companyId);
        }
        if (status != null) {
            query.append(" and status = :status");
            params.put("status", status);
        }
        query.append(" order by createdAt desc, id desc");
        int size = Page.limit(limit);
        int start = Page.offset(offset);
        List<DocumentDraft> page = DocumentDraft.<DocumentDraft>find(query.toString(), params).range(start, start + size - 1).list();
        return new Page<>(page.stream().map(this::view).toList(), size, start);
    }

    @GET
    @Path("/drafts/{draftId}")
    @Transactional
    @Operation(operationId = "getDraft")
    public DraftView get(@PathParam("tenantId") UUID tenantId, @PathParam("draftId") UUID draftId) {
        access.enter(tenantId, Role.VIEWER);
        return view(find(draftId, false));
    }

    @PUT
    @Path("/drafts/{draftId}")
    @Transactional
    @APIResponse(responseCode = "200", description = "Borrador guardado.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DraftView.class)))
    @APIResponse(responseCode = "409", description = "El borrador ya se emitió (draft_issued) o cambió desde la versión editada (draft_changed).",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @APIResponse(responseCode = "422", description = "Borrador inválido.",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @Operation(operationId = "updateDraft", summary = "Reemplaza el contenido de un borrador")
    public DraftView update(@PathParam("tenantId") UUID tenantId, @PathParam("draftId") UUID draftId, @Valid DraftInput input) {
        checkInput(input);
        TenantScope scope = access.enter(tenantId, Role.OPERATOR);
        // Row lock, then decide (§10.8 rule 2)
        DocumentDraft draft = find(draftId, true);
        checkEditable(draft, input.version());
        apply(draft, input);
        draft.version++;
        draft.updatedAt = Instant.now();
        auditor.record(scope, "draft.updated", "document_draft", draft.id);
        return view(draft);
    }

    @DELETE
    @Path("/drafts/{draftId}")
    @Transactional
    @APIResponse(responseCode = "204", description = "Borrador eliminado.")
    @APIResponse(responseCode = "409", description = "El borrador ya se emitió (draft_issued).",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @Operation(operationId = "deleteDraft")
    public Response delete(@PathParam("tenantId") UUID tenantId, @PathParam("draftId") UUID draftId) {
        TenantScope scope = access.enter(tenantId, Role.OPERATOR);
        DocumentDraft draft = find(draftId, true);
        checkEditable(draft, null);
        draft.deletedAt = Instant.now();
        draft.updatedAt = draft.deletedAt;
        auditor.record(scope, "draft.deleted", "document_draft", draft.id);
        return Response.noContent().build();
    }

    /**
     * Emits the draft: numbered, validated and signed like any emit (§11.1). The draft is marked
     * issued inside the emit transaction, so it gives at most one document; an invalid document
     * rolls back and the draft stays editable.
     */
    @POST
    @Path("/drafts/{draftId}/issue")
    @APIResponse(responseCode = "201", description = "Documento emitido (también la respuesta repetida de una Idempotency-Key ya usada, sin access_url).",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DocumentView.class)))
    @APIResponse(responseCode = "402", description = "Sin cuota de documentos en el plan (quota_exceeded).",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @APIResponse(responseCode = "409", description = "El borrador ya se emitió o cambió mientras se emitía, Idempotency-Key en proceso, empresa desactivada o no autorizada.",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @APIResponse(responseCode = "422", description = "El documento no cumple las validaciones (el borrador sigue editable).",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @APIResponse(responseCode = "503", description = "Servicio o serie ocupados (busy): reintente en unos segundos.",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @Operation(operationId = "issueDraft", summary = "Emite un borrador: lo numera, valida y firma")
    public Response issue(@PathParam("tenantId") UUID tenantId, @PathParam("draftId") UUID draftId,
            @HeaderParam("Idempotency-Key") String idempotencyKey, @HeaderParam("X-Correlation-Id") String correlationId,
            @Schema(description = "La versión que el usuario revisó; si el borrador cambió desde entonces, 409 (draft_changed).")
            @QueryParam("version") Integer version) {
        record Snapshot(UUID companyId, int version, EmitService.EmitRequest request) {}
        Snapshot snapshot = QuarkusTransaction.requiringNew().call(() -> {
            access.enter(tenantId, Role.OPERATOR);
            DocumentDraft draft = find(draftId, false);
            checkEditable(draft, version);
            ObjectNode document = json.createObjectNode();
            document.put("kind", draft.kind);
            document.set("spec", json.valueToTree(draft.spec));
            return new Snapshot(draft.companyId, draft.version,
                    new EmitService.EmitRequest(document, draft.serie, null, draft.labels.isEmpty() ? null : draft.labels));
        });
        String correlation = DocumentResource.correlation(correlationId);
        EmitService.Emitted emitted = emit.emitDraft(tenantId, snapshot.companyId(), idempotencyKey, snapshot.request(), correlation,
                doc -> markIssued(draftId, snapshot.version(), doc));
        return Response.status(emitted.status()).entity(emitted.body()).build();
    }

    /**
     * Inside the emit transaction, after the document insert (lock order: company → series →
     * documents → draft, §10.8 rule 4). A draft edited, deleted or issued since it was read rolls
     * the emit back: its number is not consumed.
     */
    private void markIssued(UUID draftId, int version, Document doc) {
        DocumentDraft draft = DocumentDraft.findById(draftId, LockModeType.PESSIMISTIC_WRITE);
        if (draft == null || draft.deletedAt != null) {
            throw ApiException.conflict("draft_changed", "El borrador se eliminó mientras se emitía.");
        }
        checkEditable(draft, version);
        draft.status = "issued";
        draft.documentId = doc.id;
        draft.version++;
        draft.updatedAt = Instant.now();
    }

    private static void checkEditable(DocumentDraft draft, Integer version) {
        if ("issued".equals(draft.status)) {
            throw ApiException.conflict("draft_issued", "El borrador ya se emitió: los documentos emitidos no se modifican.");
        }
        if (version != null && version != draft.version) {
            throw ApiException.conflict("draft_changed", "El borrador cambió desde que se abrió. Recárguelo e intente nuevamente.");
        }
    }

    private void checkInput(DraftInput input) {
        if (input == null || input.kind() == null || input.spec() == null) {
            throw ApiException.invalid("invalid_request", "Envíe el borrador como {\"kind\": ..., \"spec\": {...}}.", null);
        }
        // Validates the kind: only documents clients may emit (no summaries)
        DocumentTypes.tipoComprobante(input.kind(), input.spec());
        if (input.spec().hasNonNull("serie_numero")) {
            throw ApiException.invalid("invalid_request", "Un borrador no tiene número: indique solo la serie.", "serie_numero");
        }
        if (bytes(input.spec()).length > MAX_SPEC_BYTES) {
            throw ApiException.invalid("invalid_request", "El borrador es demasiado grande.", null);
        }
    }

    private void apply(DocumentDraft draft, DraftInput input) {
        draft.kind = input.kind();
        draft.tipoComprobante = DocumentTypes.tipoComprobante(input.kind(), input.spec());
        draft.serie = input.serie();
        draft.spec = json.convertValue(input.spec(), new TypeReference<Map<String, Object>>() {});
        draft.labels = input.labels() == null ? Map.of() : Map.copyOf(input.labels());
    }

    private static DocumentDraft find(UUID draftId, boolean lock) {
        DocumentDraft draft = lock ? DocumentDraft.findById(draftId, LockModeType.PESSIMISTIC_WRITE) : DocumentDraft.findById(draftId);
        if (draft == null || draft.deletedAt != null) {
            throw ApiException.notFound();
        }
        return draft;
    }

    private DraftView view(DocumentDraft d) {
        JsonNode spec = json.valueToTree(d.spec);
        JsonNode cliente = spec.path("cliente");
        DocumentView.Customer customer = cliente.isObject()
                ? new DocumentView.Customer(text(cliente, "tipo_documento_identidad"), text(cliente, "numero_documento_identidad"),
                        text(cliente, "nombre"))
                : null;
        return new DraftView(d.id, "xh:draft:" + d.id, d.companyId, d.status, d.version, d.kind, d.tipoComprobante, d.serie, spec,
                d.labels, customer, text(spec, "moneda"), d.documentId, d.createdBy, d.createdAt, d.updatedAt);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isValueNode() && !value.isNull() ? value.asText() : null;
    }

    private byte[] bytes(JsonNode node) {
        try {
            return json.writeValueAsBytes(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String hash(UUID companyId, DraftInput input) {
        try {
            JsonNode canonical = EmitService.canonical(json.valueToTree(input));
            return FileStore.sha256(("draft:" + companyId + ":" + json.writeValueAsString(canonical)).getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
