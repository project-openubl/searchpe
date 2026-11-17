package io.github.projectopenubl.xhandler.documents;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Page;
import io.github.projectopenubl.xhandler.audit.Auditor;
import io.github.projectopenubl.xhandler.storage.FileStore;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;

/** Issuing and tracking documents (DESIGN.md §11, §12). */
@Path("/api/v1/tenants/{tenantId}")
public class DocumentResource {

    public record Event(Instant at, String status, Integer code, String message, String actor, String correlationId) {}

    public record AccessLink(String accessUrl) {}

    private final TenantAccess access;
    private final VoidingService voiding;
    private final EmitService emit;
    private final DocumentControl control;
    private final AccessLinks links;
    private final FileStore files;
    private final Auditor auditor;

    public DocumentResource(TenantAccess access, EmitService emit, DocumentControl control, AccessLinks links, FileStore files,
            Auditor auditor, VoidingService voiding) {
        this.access = access;
        this.voiding = voiding;
        this.emit = emit;
        this.control = control;
        this.links = links;
        this.files = files;
        this.auditor = auditor;
    }

    @POST
    @Path("/companies/{companyId}/documents")
    @Operation(operationId = "emitDocument", summary = "Emite un documento: lo numera, valida y firma; SUNAT lo recibe después")
    public Response create(
            @PathParam("tenantId") UUID tenantId,
            @PathParam("companyId") UUID companyId,
            @HeaderParam("Idempotency-Key") String idempotencyKey,
            @HeaderParam("X-Correlation-Id") String correlationId,
            EmitService.EmitRequest request) {
        EmitService.Emitted emitted = emit.emit(tenantId, companyId, idempotencyKey, request, correlation(correlationId));
        return Response.status(emitted.status()).entity(emitted.body()).build();
    }

    @GET
    @Path("/documents")
    @Transactional
    @Operation(operationId = "listDocuments")
    public Page<DocumentView> list(
            @PathParam("tenantId") UUID tenantId,
            @QueryParam("company_id") UUID companyId,
            @QueryParam("status") String status,
            @QueryParam("tipo_comprobante") String tipo,
            @QueryParam("serie") String serie,
            @QueryParam("from") LocalDate from,
            @QueryParam("to") LocalDate to,
            @QueryParam("hold_reason") String holdReason,
            @QueryParam("label") List<String> labels,
            @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset) {
        access.enter(tenantId, Role.VIEWER);
        StringBuilder query = new StringBuilder("1 = 1");
        Map<String, Object> params = new HashMap<>();
        add(query, params, "companyId", companyId);
        add(query, params, "status", status);
        add(query, params, "tipoComprobante", tipo);
        add(query, params, "serie", serie);
        add(query, params, "holdReason", holdReason);
        if (from != null) {
            query.append(" and fechaEmision >= :from");
            params.put("from", from);
        }
        if (to != null) {
            query.append(" and fechaEmision <= :to");
            params.put("to", to);
        }
        int i = 0;
        for (String label : labels == null ? List.<String>of() : labels) {
            int colon = label.indexOf(':');
            if (colon <= 0) {
                throw ApiException.invalid("invalid_request", "Filtre etiquetas como label=clave:valor.", null);
            }
            query.append(" and function('jsonb_extract_path_text', labels, :labelKey").append(i).append(") = :labelValue").append(i);
            params.put("labelKey" + i, label.substring(0, colon));
            params.put("labelValue" + i, label.substring(colon + 1));
            i++;
        }
        query.append(" order by createdAt desc, id desc");
        int size = Page.limit(limit);
        int start = Page.offset(offset);
        List<Document> page = Document.<Document>find(query.toString(), params).range(start, start + size - 1).list();
        return new Page<>(page.stream().map(DocumentView::of).toList(), size, start);
    }

    @GET
    @Path("/documents/{documentId}")
    @Transactional
    @Operation(operationId = "getDocument")
    public DocumentView get(@PathParam("tenantId") UUID tenantId, @PathParam("documentId") UUID documentId) {
        access.enter(tenantId, Role.VIEWER);
        return DocumentView.of(find(documentId));
    }

    @GET
    @Path("/documents/{documentId}/events")
    @Transactional
    @Operation(operationId = "listDocumentEvents", summary = "Línea de tiempo del documento")
    public List<Event> events(@PathParam("tenantId") UUID tenantId, @PathParam("documentId") UUID documentId) {
        access.enter(tenantId, Role.VIEWER);
        find(documentId);
        List<DocumentEvent> events = DocumentEvent.list("documentId = ?1 order by at, id", documentId);
        return events.stream().map(e -> new Event(e.at, e.status, e.code, e.message, e.actor, e.correlationId)).toList();
    }

    /** XML, CDR, input or enriched JSON. Reading a document's files is audited (personal data, §3.4). */
    @GET
    @Path("/documents/{documentId}/files/{kind}")
    @Transactional
    @Operation(operationId = "downloadDocumentFile")
    public Response file(@PathParam("tenantId") UUID tenantId, @PathParam("documentId") UUID documentId,
            @PathParam("kind") String kind) {
        TenantScope scope = access.enter(tenantId, Role.VIEWER);
        Document doc = find(documentId);
        String stored = switch (kind) {
            case "xml" -> "xml_signed";
            case "cdr" -> "cdr_zip";
            case "input", "enriched", "pdf" -> kind;
            default -> throw ApiException.notFound();
        };
        DocumentFile file = DocumentFile.of(doc.id, stored).orElseThrow(ApiException::notFound);
        auditor.record(scope, "document.file_read", "document", doc.id, Map.of("kind", kind));
        return Response.ok(files.get(file.storageKey))
                .type(contentType(kind))
                .header("Content-Disposition", "attachment; filename=\"" + fileName(scope, doc, kind) + "\"")
                .build();
    }

    @POST
    @Path("/documents/{documentId}/retry")
    @Transactional
    @Operation(operationId = "retryDocument", summary = "Reintenta el envío ahora (exception: reenvía con el mismo número)")
    public DocumentView retry(@PathParam("tenantId") UUID tenantId, @PathParam("documentId") UUID documentId) {
        return DocumentView.of(control.retry(access.enter(tenantId, Role.OPERATOR), documentId));
    }

    @POST
    @Path("/documents/{documentId}/hold")
    @Transactional
    @Operation(operationId = "holdDocument")
    public DocumentView hold(@PathParam("tenantId") UUID tenantId, @PathParam("documentId") UUID documentId) {
        return DocumentView.of(control.hold(access.enter(tenantId, Role.OPERATOR), documentId));
    }

    @POST
    @Path("/documents/{documentId}/release")
    @Transactional
    @Operation(operationId = "releaseDocument")
    public DocumentView release(@PathParam("tenantId") UUID tenantId, @PathParam("documentId") UUID documentId) {
        return DocumentView.of(control.release(access.enter(tenantId, Role.OPERATOR), documentId));
    }

    /**
     * Voids an accepted document the buyer doesn't have (§9.3, §11.3): RA, RC estado 3 or RR. 202:
     * SUNAT answers later; the document is {@code voiding} until then.
     */
    @POST
    @Path("/documents/{documentId}/void")
    @Operation(operationId = "voidDocument", summary = "Anula un documento aceptado y no entregado (RA, RC o RR)")
    public Response voidDocument(@PathParam("tenantId") UUID tenantId, @PathParam("documentId") UUID documentId,
            @HeaderParam("X-Correlation-Id") String correlationId, VoidingService.VoidRequest request) {
        Document voidingDocument = voiding.voidDocument(tenantId, documentId, request, correlation(correlationId));
        return Response.status(202).entity(DocumentView.of(voidingDocument)).build();
    }

    /** The client attests the buyer received the document (§9.3): it can no longer be voided. */
    @POST
    @Path("/documents/{documentId}/delivered")
    @Transactional
    @Operation(operationId = "markDocumentDelivered")
    public DocumentView delivered(@PathParam("tenantId") UUID tenantId, @PathParam("documentId") UUID documentId) {
        TenantScope scope = access.enter(tenantId, Role.OPERATOR);
        Document doc = control.lock(documentId);
        if (doc.deliveredAt == null) {
            doc.deliveredAt = Instant.now();
            doc.deliveredSource = "client";
            doc.updatedAt = doc.deliveredAt;
            DocumentEvent.record(doc, null, "delivered (client)", scope.actor().auditName(), null);
            auditor.record(scope, "document.delivered", "document", doc.id);
        }
        return DocumentView.of(doc);
    }

    /** A new buyer link (the previous ones stay valid until they expire). */
    @POST
    @Path("/documents/{documentId}/access-link")
    @Transactional
    @Operation(operationId = "createDocumentAccessLink")
    public AccessLink accessLink(@PathParam("tenantId") UUID tenantId, @PathParam("documentId") UUID documentId) {
        TenantScope scope = access.enter(tenantId, Role.OPERATOR);
        Document doc = find(documentId);
        auditor.record(scope, "document.access_link_created", "document", doc.id);
        return new AccessLink(links.url(links.create(doc)));
    }

    /** Ends every buyer link of the document now (e.g. sent to the wrong person). */
    @POST
    @Path("/documents/{documentId}/access-links/revoke")
    @Transactional
    @Operation(operationId = "revokeDocumentAccessLinks")
    public Response revokeAccessLinks(@PathParam("tenantId") UUID tenantId, @PathParam("documentId") UUID documentId) {
        TenantScope scope = access.enter(tenantId, Role.OPERATOR);
        Document doc = find(documentId);
        int revoked = Document.getEntityManager()
                .createNativeQuery("UPDATE xhandler.access_token SET expires_at = now() WHERE document_id = :id AND expires_at > now()")
                .setParameter("id", doc.id)
                .executeUpdate();
        auditor.record(scope, "document.access_links_revoked", "document", doc.id, Map.of("count", revoked));
        return Response.noContent().build();
    }

    /** A client correlation id is kept only if it is short and plain; otherwise a new one is made. */
    static String correlation(String given) {
        if (given != null && given.length() <= 100 && given.matches("[A-Za-z0-9._:-]+")) {
            return given;
        }
        return UUID.randomUUID().toString();
    }

    static Document find(UUID documentId) {
        Document doc = Document.findById(documentId);
        if (doc == null) {
            throw ApiException.notFound();
        }
        return doc;
    }

    static String contentType(String kind) {
        return switch (kind) {
            case "xml" -> "application/xml";
            case "cdr" -> "application/zip";
            case "pdf" -> "application/pdf";
            default -> "application/json";
        };
    }

    static String fileName(TenantScope scope, Document doc, String kind) {
        String base = doc.tipoComprobante + "-" + doc.serieNumero();
        return switch (kind) {
            case "xml" -> base + ".xml";
            case "cdr" -> "R-" + base + ".zip";
            case "pdf" -> base + ".pdf";
            default -> base + "-" + kind + ".json";
        };
    }

    private static void add(StringBuilder query, Map<String, Object> params, String field, Object value) {
        if (value != null) {
            query.append(" and ").append(field).append(" = :").append(field);
            params.put(field, value);
        }
    }

}
