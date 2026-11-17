package io.github.projectopenubl.xhandler.receptions;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.api.Page;
import io.github.projectopenubl.xhandler.audit.Auditor;
import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.company.CompanyResource;
import io.github.projectopenubl.xhandler.engine.Engine;
import io.github.projectopenubl.xhandler.engine.EngineModels;
import io.github.projectopenubl.xhandler.status.StatusResource;
import io.github.projectopenubl.xhandler.storage.FileStore;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * Supplier invoices received by a company (DESIGN.md §11.5): XML plus optional PDF and CDR, all
 * untrusted (size caps; the engine refuses DTDs). The document must be addressed to the company.
 * Its validity is checked with SUNAT, now if possible and again by a job while unknown. Kept for
 * the purchases side of SIRE.
 */
@Path("/api/v1/tenants/{tenantId}/companies/{companyId}/receptions")
public class ReceptionResource {

    public record ReceptionView(UUID id, String supplierRuc, String tipoComprobante, String serieNumero, LocalDate fechaEmision, String moneda,
            BigDecimal total, String validityState, Instant validityCheckedAt, boolean hasPdf, boolean hasCdr, Instant createdAt) {}

    static final int MAX_XML = 5 * 1024 * 1024;
    static final int MAX_ATTACHMENT = 10 * 1024 * 1024;

    private final TenantAccess access;
    private final Engine engine;
    private final FileStore files;
    private final EntityManager em;
    private final Auditor auditor;
    private final ReceptionValidity validity;

    public ReceptionResource(TenantAccess access, Engine engine, FileStore files, EntityManager em, Auditor auditor, ReceptionValidity validity) {
        this.access = access;
        this.engine = engine;
        this.files = files;
        this.em = em;
        this.auditor = auditor;
        this.validity = validity;
    }

    @Operation(operationId = "receiveDocument")
    @POST
    public Response receive(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @RestForm("xml") FileUpload xml,
            @RestForm("pdf") FileUpload pdf, @RestForm("cdr") FileUpload cdr) throws IOException {
        if (xml == null) {
            throw ApiException.invalid("invalid_request", "Envíe el XML del comprobante (campo xml).", null);
        }
        // Authorize before reading uploads or making the engine parse them
        QuarkusTransaction.requiringNew().run(() -> {
            access.enter(tenantId, Role.OPERATOR);
            CompanyResource.find(companyId);
        });
        byte[] xmlBytes = read(xml, MAX_XML, "El XML");
        byte[] pdfBytes = pdf == null ? null : read(pdf, MAX_ATTACHMENT, "El PDF");
        byte[] cdrBytes = cdr == null ? null : read(cdr, MAX_ATTACHMENT, "El CDR");
        EngineModels.InspectResponse identity = engine.inspect(io.github.projectopenubl.xhandler.api.XmlText.decode(xmlBytes));
        if (identity.tipoComprobante() == null || identity.numero() == null) {
            throw ApiException.invalid("invalid_document", "El XML no es un comprobante.", null);
        }
        UUID id = QuarkusTransaction.requiringNew().call(() -> {
            TenantScope scope = access.enter(tenantId, Role.OPERATOR);
            Company company = CompanyResource.find(companyId);
            if (!"6".equals(identity.customerTipo()) || !company.ruc.equals(identity.customerNumero())) {
                throw ApiException.invalid("invalid_document", "El comprobante no está emitido a la empresa (RUC del adquirente).", null);
            }
            UUID receptionId = Ids.newId();
            try {
                em.createNativeQuery("""
                                INSERT INTO xhandler.reception (id, tenant_id, company_id, supplier_ruc, tipo_comprobante, serie, numero, fecha_emision,
                                    moneda, total, digest_value, xml_ref, pdf_ref, cdr_ref, amounts, affected_serie_numero, affected_tipo)
                                VALUES (:id, :t, :c, :ruc, :tipo, :serie, :numero, :fecha, :moneda, :total, :digest, :xml, :pdf, :cdr,
                                    cast(:amounts AS jsonb), :affected, :affectedTipo)""")
                        .setParameter("amounts", amountsJson(identity))
                        .setParameter("affected", identity.affectedSerieNumero()).setParameter("affectedTipo", identity.affectedTipo())
                        .setParameter("id", receptionId).setParameter("t", tenantId).setParameter("c", companyId)
                        .setParameter("ruc", identity.ruc()).setParameter("tipo", identity.tipoComprobante()).setParameter("serie", identity.serie())
                        .setParameter("numero", identity.numero())
                        .setParameter("fecha", identity.fechaEmision() == null ? null : LocalDate.parse(identity.fechaEmision()))
                        .setParameter("moneda", identity.moneda())
                        .setParameter("total", identity.total() == null ? null : new BigDecimal(identity.total()))
                        .setParameter("digest", identity.digestValue())
                        .setParameter("xml", files.put(tenantId, xmlBytes).key())
                        .setParameter("pdf", pdfBytes == null ? null : files.put(tenantId, pdfBytes).key())
                        .setParameter("cdr", cdrBytes == null ? null : files.put(tenantId, cdrBytes).key())
                        .executeUpdate();
                em.flush();
            } catch (PersistenceException e) {
                if (io.github.projectopenubl.xhandler.api.Constraints.violated(e).isPresent()) {
                    throw ApiException.conflict("reception_exists", "Ese comprobante ya fue recibido.");
                }
                throw e;
            }
            auditor.record(scope, "reception.received", "reception", receptionId,
                    Map.of("supplier_ruc", identity.ruc(), "serie_numero", identity.serie() + "-" + identity.numero()));
            return receptionId;
        });
        try {
            validity.check(tenantId, id);
        } catch (RuntimeException e) {
            // The reception is stored; the validity job checks it later
            org.jboss.logging.Logger.getLogger(ReceptionResource.class).warnf("validity check of reception %s failed: %s", id, e.toString());
        }
        return Response.status(201).entity(QuarkusTransaction.requiringNew().call(() -> {
            access.enter(tenantId, Role.OPERATOR);
            return find(id);
        })).build();
    }

    @Operation(operationId = "listReceptions")
    @GET
    @Transactional
    public Page<ReceptionView> list(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @QueryParam("from") LocalDate from, @QueryParam("to") LocalDate to, @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset) {
        access.enter(tenantId, Role.VIEWER);
        CompanyResource.find(companyId);
        int size = Page.limit(limit);
        int start = Page.offset(offset);
        @SuppressWarnings("unchecked")
        List<UUID> ids = em.createNativeQuery("""
                        SELECT id FROM xhandler.reception WHERE company_id = :c
                          AND (CAST(:from AS date) IS NULL OR fecha_emision >= :from) AND (CAST(:to AS date) IS NULL OR fecha_emision <= :to)
                        ORDER BY fecha_emision DESC NULLS LAST, created_at DESC LIMIT :l OFFSET :o""")
                .setParameter("c", companyId).setParameter("from", from).setParameter("to", to).setParameter("l", size).setParameter("o", start)
                .getResultList();
        return new Page<>(ids.stream().map(this::find).toList(), size, start);
    }

    @Operation(operationId = "downloadReceptionFile")
    @GET
    @Path("/{receptionId}/files/{kind}")
    @Transactional
    public Response file(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @PathParam("receptionId") UUID receptionId,
            @PathParam("kind") String kind) {
        TenantScope scope = access.enter(tenantId, Role.VIEWER);
        String column = switch (kind) {
            case "xml" -> "xml_ref";
            case "pdf" -> "pdf_ref";
            case "cdr" -> "cdr_ref";
            default -> throw ApiException.notFound();
        };
        Object ref = em.createNativeQuery("SELECT " + column + " FROM xhandler.reception WHERE id = :id AND company_id = :c")
                .setParameter("id", receptionId).setParameter("c", companyId).getResultStream().findFirst().orElse(null);
        if (ref == null) {
            throw ApiException.notFound();
        }
        auditor.record(scope, "reception.file_read", "reception", receptionId, Map.of("kind", kind));
        // Supplier files are untrusted: downloaded, never rendered from the API origin
        return Response.ok(files.get(ref.toString())).type("xml".equals(kind) ? "application/xml" : "pdf".equals(kind) ? "application/pdf"
                : "application/zip").header("Content-Disposition", "attachment; filename=\"" + receptionId + "." + ("cdr".equals(kind) ? "zip" : kind) + "\"")
                .header("X-Content-Type-Options", "nosniff").build();
    }

    private String amountsJson(EngineModels.InspectResponse identity) {
        try {
            var amounts = io.github.projectopenubl.xhandler.documents.Document.amountsOf(identity.amounts());
            return amounts == null ? null : JSON.writeValueAsString(amounts);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    private ReceptionView find(UUID id) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT id, supplier_ruc, tipo_comprobante, serie || '-' || numero, fecha_emision, moneda, total, validity_state,
                               validity_checked_at, pdf_ref IS NOT NULL, cdr_ref IS NOT NULL, created_at
                        FROM xhandler.reception WHERE id = :id""").setParameter("id", id).getResultList();
        if (rows.isEmpty()) {
            throw ApiException.notFound();
        }
        Object[] r = rows.get(0);
        return new ReceptionView((UUID) r[0], (String) r[1], (String) r[2], (String) r[3],
                r[4] == null ? null : r[4] instanceof LocalDate d ? d : ((java.sql.Date) r[4]).toLocalDate(), (String) r[5], (BigDecimal) r[6], (String) r[7],
                StatusResource.toInstant(r[8]), (Boolean) r[9], (Boolean) r[10], StatusResource.toInstant(r[11]));
    }

    private static byte[] read(FileUpload upload, int max, String what) throws IOException {
        if (upload.size() > max) {
            throw ApiException.invalid("invalid_request", what + " es demasiado grande.", null);
        }
        return Files.readAllBytes(upload.uploadedFile());
    }
}
