package io.github.projectopenubl.xhandler.conformity;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Page;
import io.github.projectopenubl.xhandler.audit.Auditor;
import io.github.projectopenubl.xhandler.company.CompanyResource;
import io.github.projectopenubl.xhandler.documents.Document;
import io.github.projectopenubl.xhandler.status.StatusResource;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/**
 * Factura negociable conformity (DESIGN.md §11.8): per document, per company, and an export for
 * factoring (no CAVALI integration). An objected factura gets a guided credit note type 13.
 */
@Path("/api/v1/tenants/{tenantId}")
public class ConformityResource {

    public record ConformityView(
            UUID documentId, String serieNumero, String buyerRuc, String state, Instant acceptedAt, Instant windowEndsAt, Instant checkedAt,
            String checkDetail, Instant resolvedAt, Map<String, Object> suggestedCreditNote) {}

    private final TenantAccess access;
    private final EntityManager em;
    private final Auditor auditor;

    public ConformityResource(TenantAccess access, EntityManager em, Auditor auditor) {
        this.access = access;
        this.em = em;
        this.auditor = auditor;
    }

    private static final String SELECT = """
            SELECT c.document_id, d.serie, d.numero, c.buyer_ruc, c.state, c.accepted_at, c.window_ends_at, c.checked_at, c.check_detail,
                   c.resolved_at, d.total, d.moneda, d.fecha_emision, d.customer_nombre
            FROM xhandler.conformity c JOIN xhandler.document d ON d.id = c.document_id""";

    @Operation(operationId = "getDocumentConformity")
    @GET
    @Path("/documents/{documentId}/conformity")
    @Transactional
    public ConformityView document(@PathParam("tenantId") UUID tenantId, @PathParam("documentId") UUID documentId) {
        access.enter(tenantId, Role.VIEWER);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(SELECT + " WHERE c.document_id = :d").setParameter("d", documentId).getResultList();
        if (rows.isEmpty()) {
            throw ApiException.notFound();
        }
        Object[] r = rows.get(0);
        Map<String, Object> creditNote = null;
        if ("objected".equals(r[4])) {
            creditNote = creditNote13(Document.findById(documentId));
        }
        return view(r, creditNote);
    }

    @Operation(operationId = "listConformity", summary = "Facturas negociables de la empresa y su conformidad, las más recientes primero")
    @GET
    @Path("/companies/{companyId}/conformity")
    @Transactional
    public Page<ConformityView> list(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId,
            @Schema(description = "pending, agreed, objected o silent_agreement; sin filtro, todas.") @QueryParam("state") String state,
            @QueryParam("limit") Integer limit, @QueryParam("offset") Integer offset) {
        access.enter(tenantId, Role.VIEWER);
        CompanyResource.find(companyId);
        int size = Page.limit(limit);
        int start = Page.offset(offset);
        return new Page<>(rows(companyId, state, size, start).stream().map(r -> view(r, null)).toList(), size, start);
    }

    /** For factoring: facturas negociables and their conformity, as CSV. */
    @APIResponse(responseCode = "200", description = "Facturas negociables y su conformidad (CSV).", content = {
            @Content(mediaType = "text/csv", schema = @Schema(type = SchemaType.STRING, format = "binary"))
    })
    @Operation(operationId = "exportConformity")
    @GET
    @Path("/companies/{companyId}/conformity/export")
    @Transactional
    public Response export(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @QueryParam("state") String state) {
        TenantScope scope = access.enter(tenantId, Role.VIEWER);
        CompanyResource.find(companyId);
        StringBuilder csv = new StringBuilder("﻿serie_numero,fecha_emision,adquirente_ruc,adquirente,moneda,total,conformidad,"
                + "aceptada_sunat,vence_plazo,resuelta\r\n");
        for (Object[] r : rows(companyId, state, EXPORT_LIMIT, 0)) {
            csv.append(String.join(",", List.of(r[1] + "-" + r[2], String.valueOf(r[12]), nz(r[3]), quote(nz(r[13])), nz(r[11]), nz(r[10]),
                    (String) r[4], String.valueOf(StatusResource.toInstant(r[5])), String.valueOf(StatusResource.toInstant(r[6])),
                    r[9] == null ? "" : String.valueOf(StatusResource.toInstant(r[9]))))).append("\r\n");
        }
        auditor.record(scope, "conformity.exported", "company", companyId, Map.of("state", state == null ? "all" : state));
        return Response.ok(csv.toString().getBytes(StandardCharsets.UTF_8)).type("text/csv; charset=utf-8")
                .header("Content-Disposition", "attachment; filename=\"facturas-negociables-" + companyId + ".csv\"").build();
    }

    /** Rows in one CSV export: the most recent ones. */
    private static final int EXPORT_LIMIT = 1000;

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(UUID companyId, String state, int limit, int offset) {
        if (state != null && !List.of("pending", "agreed", "objected", "silent_agreement").contains(state)) {
            throw ApiException.invalid("invalid_request", "state debe ser pending, agreed, objected o silent_agreement.", null);
        }
        return em.createNativeQuery(SELECT + """
                         WHERE c.company_id = :c AND (cast(:s AS text) IS NULL OR c.state = :s)
                        ORDER BY c.accepted_at DESC, c.document_id DESC LIMIT :limit OFFSET :offset""")
                .setParameter("c", companyId).setParameter("s", state).setParameter("limit", limit).setParameter("offset", offset)
                .getResultList();
    }

    /**
     * The guided correction after an objection: a credit note type 13 (ajustes de montos y/o fechas
     * de pago) on the factura, to complete with the corrected cuotas and send to {@code POST /documents}.
     */
    static Map<String, Object> creditNote13(Document d) {
        Map<String, Object> cliente = new LinkedHashMap<>();
        cliente.put("tipo_documento_identidad", d.customerTipo);
        cliente.put("numero_documento_identidad", d.customerNumero);
        cliente.put("nombre", d.customerNombre);
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("tipo_nota", "13");
        spec.put("comprobante_afectado_tipo", "01");
        spec.put("comprobante_afectado_serie_numero", d.serieNumero());
        spec.put("descripcion_sustento", "Ajuste del monto neto pendiente de pago y/o de las fechas de vencimiento");
        spec.put("moneda", d.moneda);
        spec.put("cliente", cliente);
        spec.put("forma_pago", Map.of("tipo", "Credito", "cuotas", List.of()));
        spec.put("detalles", List.of());
        return Map.of("kind", "CreditNote", "spec", spec);
    }

    private static ConformityView view(Object[] r, Map<String, Object> creditNote) {
        return new ConformityView((UUID) r[0], r[1] + "-" + r[2], (String) r[3], (String) r[4], StatusResource.toInstant(r[5]),
                StatusResource.toInstant(r[6]), StatusResource.toInstant(r[7]), (String) r[8], StatusResource.toInstant(r[9]), creditNote);
    }

    private static String nz(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String quote(String value) {
        String v = !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
