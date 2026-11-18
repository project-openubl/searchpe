package io.github.projectopenubl.xhandler.documents;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.storage.FileStore;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/**
 * Buyer access by capability link (DESIGN.md §3.3, §12): no login, rate limited at the gateway.
 * The token opens one document's data and files; downloading a file counts as delivery (§11.6).
 * Unknown and expired tokens are both 404.
 */
@Path("/api/v1/public/documents/{token}")
public class PublicDocumentResource {

    public record PublicDocument(
            String emisorRuc,
            String emisorRazonSocial,
            String tipoComprobante,
            String serieNumero,
            LocalDate fechaEmision,
            String moneda,
            BigDecimal total,
            String status,
            List<String> files,
            @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "El Libro de Reclamaciones virtual del emisor, si lo configuró.")
            String libroReclamacionesUrl) {}

    private final AccessLinks links;
    private final TenantAccess access;
    private final FileStore files;
    private final EntityManager em;
    private final io.github.projectopenubl.xhandler.pdf.DocumentPdf pdf;

    public PublicDocumentResource(AccessLinks links, TenantAccess access, FileStore files, EntityManager em,
            io.github.projectopenubl.xhandler.pdf.DocumentPdf pdf) {
        this.pdf = pdf;
        this.links = links;
        this.access = access;
        this.files = files;
        this.em = em;
    }

    @Operation(operationId = "getPublicDocument")
    @GET
    @Transactional
    public PublicDocument get(@PathParam("token") String token) {
        Document doc = open(token);
        Company company = Company.findById(doc.companyId);
        java.util.List<String> available = DocumentFile.<DocumentFile>list("documentId", doc.id).stream()
                .map(f -> switch (f.kind) {
                    case "xml_signed" -> "xml";
                    case "cdr_zip" -> "cdr";
                    default -> null;
                })
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        available.add("pdf");
        java.util.Collections.sort(available);
        // A test-environment document must never read as a real one
        String status = "production".equals(doc.sunatEnv) ? publicStatus(doc.status) : "documento_de_prueba";
        return new PublicDocument(company.ruc, company.razonSocial, doc.tipoComprobante, doc.serieNumero(), doc.fechaEmision,
                doc.moneda, doc.total, status, available, company.libroReclamacionesUrl);
    }

    @APIResponse(responseCode = "200", description = "El archivo: XML firmado, CDR (ZIP) o PDF.", content = {
            @Content(mediaType = "application/xml", schema = @Schema(type = SchemaType.STRING, format = "binary")),
            @Content(mediaType = "application/zip", schema = @Schema(type = SchemaType.STRING, format = "binary")),
            @Content(mediaType = "application/pdf", schema = @Schema(type = SchemaType.STRING, format = "binary"))
    })
    @Operation(operationId = "downloadPublicDocumentFile")
    @GET
    @Path("/{kind}")
    public Response file(@PathParam("token") String token, @PathParam("kind") String kind) {
        String stored = switch (kind) {
            case "xml" -> "xml_signed";
            case "cdr" -> "cdr_zip";
            case "pdf" -> "pdf";
            default -> throw ApiException.notFound();
        };
        record Download(byte[] content, String name) {}
        Download download = QuarkusTransaction.requiringNew().call(() -> {
            Document doc = open(token);
            byte[] content = "pdf".equals(kind)
                    ? pdf.render(doc.id, links.url(token))
                    : files.get(DocumentFile.of(doc.id, stored).orElseThrow(ApiException::notFound).storageKey);
            markDelivered(doc.id);
            return new Download(content, DocumentResource.fileName(null, doc, kind));
        });
        return Response.ok(download.content())
                .type(DocumentResource.contentType(kind))
                .header("Content-Disposition", "attachment; filename=\"" + download.name() + "\"")
                .build();
    }

    private Document open(String token) {
        AccessLinks.Resolved resolved = links.resolve(token).orElseThrow(ApiException::notFound);
        access.enterAsPlatform(resolved.tenantId());
        Document doc = Document.findById(resolved.documentId());
        if (doc == null) {
            throw ApiException.notFound();
        }
        return doc;
    }

    /** First download by the buyer: the document counts as delivered (voiding is then refused, §9.3). */
    private void markDelivered(java.util.UUID documentId) {
        Document doc = Document.findById(documentId, LockModeType.PESSIMISTIC_WRITE);
        em.createNativeQuery("""
                        INSERT INTO xhandler.delivery (id, tenant_id, document_id, channel, result) VALUES (:id, :tenant, :document, 'portal', 'downloaded')""")
                .setParameter("id", Ids.newId())
                .setParameter("tenant", doc.tenantId)
                .setParameter("document", doc.id)
                .executeUpdate();
        if (doc.deliveredAt == null) {
            doc.deliveredAt = Instant.now();
            doc.deliveredSource = "service";
            doc.updatedAt = doc.deliveredAt;
            DocumentEvent.record(doc, null, "delivered (buyer portal)", "public:buyer", null);
        }
    }

    /** Buyers see the answer, not the internals: pending until SUNAT's definitive answer. */
    static String publicStatus(String status) {
        return switch (status) {
            case "accepted", "accepted_with_observations" -> "aceptado";
            case "rejected" -> "rechazado";
            case "voided" -> "anulado";
            case "voiding" -> "en_anulacion";
            default -> "pendiente";
        };
    }
}
