package io.github.projectopenubl.xhandler.exit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.audit.Auditor;
import io.github.projectopenubl.xhandler.jobs.Job;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.jobs.Jobs;
import io.github.projectopenubl.xhandler.pdf.DocumentPdf;
import io.github.projectopenubl.xhandler.status.StatusResource;
import io.github.projectopenubl.xhandler.storage.FileStore;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.Tenant;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.logging.Logger;

/**
 * A tenant leaving (DESIGN.md §13): a full export first — XML, CDR, PDF and metadata of every
 * document, plus the receptions — in parts of at most {@link Export#PART_DOCUMENTS} documents and
 * an index; then the exit itself, once no document waits for SUNAT and the export covers every
 * document. The tenant becomes {@code exited}: its members can still read it (exports, data-subject
 * requests) but not emit, and its data is deleted 2 years later (the processor's limit, DS
 * 016-2024-JUS art. 31.2) unless it bought the archive plan or a tax audit holds it.
 */
@Path("/api/v1/tenants/{tenantId}")
public class TenantExit {

    public record ExitRequest(@NotBlank String confirmName) {}

    /** An export: {@code parts} lists the ZIPs to download (the index first), once {@code done}. */
    public record ExportView(UUID id, String status, List<ExportPart> parts, Integer documents, String detail, String createdAt,
            String doneAt) {}

    /**
     * A part to download by its position in {@code parts}. Its storage key stays internal: it is
     * read only to serve the download.
     */
    public record ExportPart(
            @Schema(required = true, description = "Nombre del ZIP.") String name,
            @Schema(required = true, description = "Tamaño en bytes.") long size,
            @Schema(description = "Comprobantes (o recepciones) en la parte; en el índice, todos los comprobantes.") Integer documents) {}

    static final Duration DELETE_AFTER = Duration.ofDays(730);

    private final TenantAccess access;
    private final EntityManager em;
    private final Jobs jobs;
    private final FileStore files;
    private final Auditor auditor;

    public TenantExit(TenantAccess access, EntityManager em, Jobs jobs, FileStore files, Auditor auditor) {
        this.access = access;
        this.em = em;
        this.jobs = jobs;
        this.files = files;
        this.auditor = auditor;
    }

    @APIResponse(responseCode = "202", description = "Exportación solicitada; la genera un trabajo.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExportView.class)))
    @Operation(operationId = "createExport")
    @POST
    @Path("/exports")
    @Transactional
    public Response export(@PathParam("tenantId") UUID tenantId) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        if (!em.createNativeQuery("SELECT 1 FROM xhandler.tenant_export WHERE status = 'pending' OR created_at > now() - interval '1 hour'")
                .getResultList().isEmpty()) {
            throw ApiException.conflict("export_recent", "Ya hay una exportación en curso o de la última hora. Use esa.");
        }
        UUID id = Ids.newId();
        em.createNativeQuery("INSERT INTO xhandler.tenant_export (id, tenant_id, requested_by) VALUES (:id, :t, :by)")
                .setParameter("id", id).setParameter("t", tenantId).setParameter("by", scope.actor().auditName()).executeUpdate();
        Job job = jobs.enqueue(scope, Export.NAME, 4, Map.of("export_id", id.toString()), List.of("export:" + tenantId), null);
        em.createNativeQuery("UPDATE xhandler.tenant_export SET job_id = :j WHERE id = :id").setParameter("j", job.id).setParameter("id", id)
                .executeUpdate();
        auditor.record(scope, "tenant.export_requested", "tenant", tenantId, Map.of());
        return Response.status(202).entity(view(id)).build();
    }

    @Operation(operationId = "listExports")
    @GET
    @Path("/exports")
    @Transactional
    public List<ExportView> exports(@PathParam("tenantId") UUID tenantId) {
        access.enter(tenantId, Role.ADMIN);
        // An export whose job died (a crashed worker) is failed, not pending forever
        em.createNativeQuery("""
                        UPDATE xhandler.tenant_export e SET status = 'failed', detail = 'La exportación se interrumpió. Solicítela de nuevo.',
                            done_at = now()
                        WHERE e.status = 'pending' AND e.job_id IS NOT NULL
                          AND (SELECT state FROM xhandler.job_summary(e.job_id)) IN ('failed', 'canceled')""").executeUpdate();
        @SuppressWarnings("unchecked")
        List<UUID> ids = em.createNativeQuery("SELECT id FROM xhandler.tenant_export ORDER BY created_at DESC LIMIT 20").getResultList();
        return ids.stream().map(this::view).toList();
    }

    @APIResponse(responseCode = "200", description = "La parte de la exportación (ZIP).", content = {
            @Content(mediaType = "application/zip", schema = @Schema(type = SchemaType.STRING, format = "binary"))
    })
    @Operation(operationId = "downloadExportPart")
    @GET
    @Path("/exports/{exportId}/parts/{index}")
    @Transactional
    public Response part(@PathParam("tenantId") UUID tenantId, @PathParam("exportId") UUID exportId, @PathParam("index") int index) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        List<Map<String, Object>> parts = storedParts(exportId);
        if (index < 0 || index >= parts.size()) {
            throw ApiException.notFound();
        }
        Map<String, Object> part = parts.get(index);
        auditor.record(scope, "tenant.export_downloaded", "tenant", tenantId, Map.of("part", String.valueOf(part.get("name"))));
        return Response.ok(files.get((String) part.get("ref"))).type("application/zip")
                .header("Content-Disposition", "attachment; filename=\"" + part.get("name") + "\"").build();
    }

    /** The tenant after its exit: deleted after {@code deleteAfter}, never when it has an archive plan. */
    public record ExitView(String status, String deleteAfter) {}

    /**
     * Leaves the platform: the organization's name typed, no document waiting for SUNAT, and an
     * export from the last 30 days that is newer than every document.
     */
    @Operation(operationId = "exitTenant")
    @POST
    @Path("/exit")
    @Transactional
    public ExitView exit(@PathParam("tenantId") UUID tenantId, @Valid ExitRequest request) {
        TenantScope scope = access.enter(tenantId, Role.OWNER);
        Tenant tenant = Tenant.findById(tenantId);
        if (!"active".equals(tenant.status)) {
            throw ApiException.conflict("not_active", "La organización no está activa.");
        }
        if (!tenant.name.equals(request.confirmName())) {
            throw ApiException.invalid("invalid_request", "Escriba el nombre exacto de la organización para confirmar.", null);
        }
        long open = ((Number) em.createNativeQuery("""
                        SELECT count(*) FROM xhandler.document WHERE status IN ('pending_send', 'in_progress', 'unknown', 'voiding')""")
                .getSingleResult()).longValue();
        if (open > 0) {
            throw ApiException.conflict("pending_documents",
                    "Hay " + open + " comprobantes esperando la respuesta de SUNAT. Salga cuando tengan respuesta definitiva.");
        }
        if (em.createNativeQuery("""
                        SELECT 1 FROM xhandler.tenant_export e WHERE e.status = 'done' AND e.done_at > now() - interval '30 days'
                          AND e.created_at > coalesce((SELECT max(updated_at) FROM xhandler.document), '-infinity')""")
                .getResultList().isEmpty()) {
            throw ApiException.conflict("export_required",
                    "Descargue primero una exportación completa, posterior al último comprobante (de los últimos 30 días).");
        }
        Instant deleteAfter = tenant.archivePlan ? null : Instant.now().plus(DELETE_AFTER);
        em.createNativeQuery("""
                        UPDATE xhandler.tenant SET exit_requested_at = now(), delete_after = :d, status = 'exited', updated_at = now()
                        WHERE id = :id""").setParameter("d", deleteAfter).setParameter("id", tenantId).executeUpdate();
        auditor.record(scope, "tenant.exit", "tenant", tenantId, Map.of("delete_after", String.valueOf(deleteAfter)));
        return new ExitView("exited", deleteAfter == null ? null : deleteAfter.toString());
    }

    ExportView view(UUID id) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT id, status, cast(parts AS text), documents, detail, created_at, done_at FROM xhandler.tenant_export WHERE id = :id""")
                .setParameter("id", id).getResultList();
        if (rows.isEmpty()) {
            throw ApiException.notFound();
        }
        Object[] r = rows.get(0);
        List<ExportPart> parts = parse((String) r[2]).stream()
                .map(p -> new ExportPart(String.valueOf(p.get("name")), ((Number) p.get("size")).longValue(),
                        p.get("documents") == null ? null : ((Number) p.get("documents")).intValue()))
                .toList();
        return new ExportView((UUID) r[0], (String) r[1], parts, r[3] == null ? null : ((Number) r[3]).intValue(), (String) r[4],
                String.valueOf(StatusResource.toInstant(r[5])), r[6] == null ? null : String.valueOf(StatusResource.toInstant(r[6])));
    }

    /** The parts as stored, with their storage keys ({@code ref}): only for downloads, never returned. */
    private List<Map<String, Object>> storedParts(UUID id) {
        List<?> rows = em.createNativeQuery("SELECT cast(parts AS text) FROM xhandler.tenant_export WHERE id = :id").setParameter("id", id)
                .getResultList();
        if (rows.isEmpty()) {
            throw ApiException.notFound();
        }
        return parse((String) rows.get(0));
    }

    private static List<Map<String, Object>> parse(String parts) {
        if (parts == null) {
            return List.of();
        }
        try {
            return new ObjectMapper().readValue(parts, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Builds the export page by page: parts of at most {@link #PART_DOCUMENTS} documents (XML, CDR,
     * PDF and JSON each, named {@code env/RUC-TT-SERIE-NUMERO} like SUNAT), parts of receptions, and
     * an index. Memory stays bounded by one part.
     */
    @ApplicationScoped
    public static class Export implements JobHandler {
        public static final String NAME = "tenants.export";
        static final int PART_DOCUMENTS = 300;
        private static final ObjectMapper JSON = new ObjectMapper();

        private final TenantAccess access;
        private final EntityManager em;
        private final FileStore files;
        private final DocumentPdf pdf;

        public Export(TenantAccess access, EntityManager em, FileStore files, DocumentPdf pdf) {
            this.access = access;
            this.em = em;
            this.files = files;
            this.pdf = pdf;
        }

        @Override
        public String name() {
            return NAME;
        }

        record Item(UUID id, String name, String xmlRef, String cdrRef, Map<String, Object> meta) {}

        @Override
        public Map<String, Object> run(JobContext context) {
            UUID exportId = context.uuidArg("export_id");
            UUID tenantId = context.tenantId();
            try {
                List<Map<String, Object>> parts = new ArrayList<>();
                List<Map<String, Object>> index = new ArrayList<>();
                Set<String> names = new HashSet<>();
                int documents = 0;
                // Keyset pagination: (created_at, id) of the last document of the previous page
                Object[] after = {Instant.EPOCH, new UUID(0, 0)};
                while (true) {
                    List<Item> page = documents(tenantId, (Instant) after[0], (UUID) after[1]);
                    if (page.isEmpty()) {
                        break;
                    }
                    Map<String, byte[]> entries = new LinkedHashMap<>();
                    for (Item item : page) {
                        if (!names.add(item.name())) {
                            throw new IllegalStateException("two documents named " + item.name());
                        }
                        if (item.xmlRef() != null) {
                            entries.put(item.name() + ".xml", files.get(item.xmlRef()));
                        }
                        if (item.cdrRef() != null) {
                            entries.put("R-" + item.name() + ".zip", files.get(item.cdrRef()));
                        }
                        try {
                            byte[] rendered = QuarkusTransaction.requiringNew().call(() -> {
                                access.enterAsPlatform(tenantId);
                                return pdf.render(item.id(), "");
                            });
                            entries.put(item.name() + ".pdf", rendered);
                        } catch (RuntimeException e) {
                            item.meta().put("pdf", "no disponible");
                        }
                        entries.put(item.name() + ".json", JSON.writeValueAsBytes(item.meta()));
                        index.add(item.meta());
                    }
                    documents += page.size();
                    FileStore.Stored stored = files.put(tenantId, zip(entries));
                    parts.add(Map.of("name", String.format("documentos-%04d.zip", parts.size() + 1), "ref", stored.key(), "size", stored.size(),
                            "documents", page.size()));
                    Item last = page.get(page.size() - 1);
                    after = new Object[] {Instant.parse((String) last.meta().get("created_at")), last.id()};
                }
                parts.addAll(receptions(tenantId, index));
                FileStore.Stored indexStored = files.put(tenantId, zip(Map.of("indice.json", JSON.writeValueAsBytes(index))));
                parts.add(0, Map.of("name", "indice.zip", "ref", indexStored.key(), "size", indexStored.size(), "documents", documents));
                String partsJson = JSON.writeValueAsString(parts);
                int total = documents;
                QuarkusTransaction.requiringNew().run(() -> {
                    access.enterAsPlatform(tenantId);
                    em.createNativeQuery("""
                                    UPDATE xhandler.tenant_export SET status = 'done', parts = cast(:p AS jsonb), documents = :n, done_at = now()
                                    WHERE id = :id""").setParameter("p", partsJson).setParameter("n", total).setParameter("id", exportId).executeUpdate();
                });
                return Map.of("documents", documents, "parts", parts.size());
            } catch (Exception e) {
                Logger.getLogger(Export.class).errorf(e, "export %s failed", exportId);
                QuarkusTransaction.requiringNew().run(() -> {
                    access.enterAsPlatform(tenantId);
                    em.createNativeQuery("UPDATE xhandler.tenant_export SET status = 'failed', detail = :d, done_at = now() WHERE id = :id")
                            .setParameter("d", "No se pudo generar la exportación.").setParameter("id", exportId).executeUpdate();
                });
                return Map.of("status", "failed");
            }
        }

        private List<Item> documents(UUID tenantId, Instant afterCreated, UUID afterId) {
            return QuarkusTransaction.requiringNew().call(() -> {
                access.enterAsPlatform(tenantId);
                @SuppressWarnings("unchecked")
                List<Object[]> rows = em.createNativeQuery("""
                                SELECT d.id, c.ruc, d.sunat_env, d.tipo_comprobante, d.serie, d.numero, d.fecha_emision, d.status, d.sunat_code,
                                       d.moneda, d.total, d.customer_tipo, d.customer_numero, d.created_at,
                                       (SELECT storage_key FROM xhandler.document_file f WHERE f.document_id = d.id AND f.kind = 'xml_signed'),
                                       (SELECT storage_key FROM xhandler.document_file f WHERE f.document_id = d.id AND f.kind = 'cdr_zip')
                                FROM xhandler.document d JOIN xhandler.company c ON c.id = d.company_id
                                WHERE (d.created_at, d.id) > (:at, :id)
                                ORDER BY d.created_at, d.id LIMIT :n""")
                        .setParameter("at", afterCreated).setParameter("id", afterId).setParameter("n", PART_DOCUMENTS).getResultList();
                List<Item> out = new ArrayList<>();
                for (Object[] r : rows) {
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("id", r[0].toString());
                    meta.put("ruc", r[1]);
                    meta.put("sunat_env", r[2]);
                    meta.put("tipo_comprobante", r[3]);
                    meta.put("serie_numero", r[4] + "-" + r[5]);
                    meta.put("fecha_emision", String.valueOf(r[6]));
                    meta.put("status", r[7]);
                    meta.put("sunat_code", r[8]);
                    meta.put("moneda", r[9]);
                    meta.put("total", r[10] == null ? null : r[10].toString());
                    meta.put("customer_tipo", r[11]);
                    meta.put("customer", r[12]);
                    meta.put("created_at", String.valueOf(StatusResource.toInstant(r[13])));
                    String name = r[2] + "/" + r[1] + "-" + r[3] + "-" + r[4] + "-" + r[5];
                    out.add(new Item((UUID) r[0], name, (String) r[14], (String) r[15], meta));
                }
                return out;
            });
        }

        /** Supplier documents received, in parts too (the purchases side of the records). */
        private List<Map<String, Object>> receptions(UUID tenantId, List<Map<String, Object>> index) throws IOException {
            List<Map<String, Object>> parts = new ArrayList<>();
            UUID after = new UUID(0, 0);
            while (true) {
                UUID from = after;
                @SuppressWarnings("unchecked")
                List<Object[]> rows = QuarkusTransaction.requiringNew().call(() -> {
                    access.enterAsPlatform(tenantId);
                    return em.createNativeQuery("""
                                    SELECT r.id, c.ruc, r.supplier_ruc, r.tipo_comprobante, r.serie, r.numero, r.fecha_emision, r.validity_state,
                                           r.xml_ref, r.pdf_ref, r.cdr_ref
                                    FROM xhandler.reception r JOIN xhandler.company c ON c.id = r.company_id
                                    WHERE r.id > :after ORDER BY r.id LIMIT :n""").setParameter("after", from).setParameter("n", PART_DOCUMENTS)
                            .getResultList();
                });
                if (rows.isEmpty()) {
                    return parts;
                }
                Map<String, byte[]> entries = new LinkedHashMap<>();
                for (Object[] r : rows) {
                    String name = "recepciones/" + r[1] + "/" + r[2] + "-" + r[3] + "-" + r[4] + "-" + r[5];
                    entries.put(name + ".xml", files.get((String) r[8]));
                    if (r[9] != null) {
                        entries.put(name + ".pdf", files.get((String) r[9]));
                    }
                    if (r[10] != null) {
                        entries.put("R-" + name.replace("recepciones/", "recepciones/R-") + ".zip", files.get((String) r[10]));
                    }
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("reception_id", r[0].toString());
                    meta.put("ruc", r[1]);
                    meta.put("supplier_ruc", r[2]);
                    meta.put("serie_numero", r[4] + "-" + r[5]);
                    meta.put("fecha_emision", String.valueOf(r[6]));
                    meta.put("validity_state", r[7]);
                    index.add(meta);
                }
                FileStore.Stored stored = files.put(tenantId, zip(entries));
                parts.add(Map.of("name", String.format("recepciones-%04d.zip", parts.size() + 1), "ref", stored.key(), "size", stored.size(),
                        "documents", rows.size()));
                after = (UUID) rows.get(rows.size() - 1)[0];
            }
        }

        static byte[] zip(Map<String, byte[]> entries) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream out = new ZipOutputStream(bytes)) {
                for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                    out.putNextEntry(new ZipEntry(e.getKey()));
                    out.write(e.getValue());
                    out.closeEntry();
                }
            }
            return bytes.toByteArray();
        }
    }

    /**
     * Deletes the data of tenants whose exit period is over (daily): every file under the tenant's
     * storage prefix (referenced or not), then every row, keeping the tenant row without personal
     * data and the evidence in {@code tenant_deletion}.
     */
    @ApplicationScoped
    public static class Deletion implements JobHandler {
        private final EntityManager em;
        private final FileStore files;

        public Deletion(EntityManager em, FileStore files) {
            this.em = em;
            this.files = files;
        }

        @Override
        public String name() {
            return "tenants.exit-deletion";
        }

        @Override
        public Map<String, Object> run(JobContext context) {
            @SuppressWarnings("unchecked")
            List<UUID> due = QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery("SELECT tenant_id FROM xhandler.tenants_to_delete()")
                    .getResultList());
            int deleted = 0;
            for (UUID tenantId : due) {
                int filesDeleted = 0;
                for (String key : files.list(FileStore.keyOf(tenantId, ""))) {
                    files.delete(key);
                    filesDeleted++;
                }
                int count = filesDeleted;
                String counts = QuarkusTransaction.requiringNew().call(() -> String.valueOf(em.createNativeQuery(
                                "SELECT cast(xhandler.purge_tenant(:t, :f) AS text)").setParameter("t", tenantId).setParameter("f", count)
                        .getSingleResult()));
                Logger.getLogger("xhandler.audit.platform").infof("tenant %s deleted after its exit period: %s, %d files", tenantId, counts, count);
                deleted++;
            }
            return Map.of("deleted", deleted);
        }
    }
}
