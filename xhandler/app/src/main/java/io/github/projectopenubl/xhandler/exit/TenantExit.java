package io.github.projectopenubl.xhandler.exit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.audit.Auditor;
import io.github.projectopenubl.xhandler.documents.Document;
import io.github.projectopenubl.xhandler.documents.DocumentFile;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jboss.logging.Logger;

/**
 * A tenant leaving (DESIGN.md §13): a full export first — XML, CDR, PDF and metadata, one ZIP per
 * issue month plus an index — then the exit itself, which suspends the tenant and schedules the
 * deletion of all its data 2 years later (the processor's limit, DS 016-2024-JUS art. 31.2),
 * unless it bought the archive plan or a tax audit holds it.
 */
@Path("/api/v1/tenants/{tenantId}")
public class TenantExit {

    public record ExitRequest(@NotBlank String confirmName) {}

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

    @POST
    @Path("/exports")
    @Transactional
    public Response export(@PathParam("tenantId") UUID tenantId) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        UUID id = Ids.newId();
        em.createNativeQuery("INSERT INTO xhandler.tenant_export (id, tenant_id, requested_by) VALUES (:id, :t, :by)")
                .setParameter("id", id).setParameter("t", tenantId).setParameter("by", scope.actor().auditName()).executeUpdate();
        jobs.enqueue(scope, Export.NAME, 4, Map.of("export_id", id.toString()), List.of("export:" + tenantId), null);
        auditor.record(scope, "tenant.export_requested", "tenant", tenantId, Map.of());
        return Response.status(202).entity(view(id)).build();
    }

    @GET
    @Path("/exports")
    @Transactional
    public List<Map<String, Object>> exports(@PathParam("tenantId") UUID tenantId) {
        access.enter(tenantId, Role.ADMIN);
        @SuppressWarnings("unchecked")
        List<UUID> ids = em.createNativeQuery("SELECT id FROM xhandler.tenant_export ORDER BY created_at DESC LIMIT 20").getResultList();
        return ids.stream().map(this::view).toList();
    }

    @GET
    @Path("/exports/{exportId}/parts/{index}")
    @Transactional
    public Response part(@PathParam("tenantId") UUID tenantId, @PathParam("exportId") UUID exportId, @PathParam("index") int index) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        Map<String, Object> view = view(exportId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) view.get("parts");
        if (index < 0 || index >= parts.size()) {
            throw ApiException.notFound();
        }
        Map<String, Object> part = parts.get(index);
        auditor.record(scope, "tenant.export_downloaded", "tenant", tenantId, Map.of("part", String.valueOf(part.get("name"))));
        return Response.ok(files.get((String) part.get("ref"))).type("application/zip")
                .header("Content-Disposition", "attachment; filename=\"" + part.get("name") + "\"").build();
    }

    /** Leaves the platform: needs an export finished in the last 30 days and the tenant's name typed. */
    @POST
    @Path("/exit")
    @Transactional
    public Map<String, Object> exit(@PathParam("tenantId") UUID tenantId, @Valid ExitRequest request) {
        TenantScope scope = access.enter(tenantId, Role.OWNER);
        Tenant tenant = Tenant.findById(tenantId);
        if (!tenant.name.equals(request.confirmName())) {
            throw ApiException.invalid("invalid_request", "Escriba el nombre exacto de la organización para confirmar.", null);
        }
        if (em.createNativeQuery("SELECT 1 FROM xhandler.tenant_export WHERE status = 'done' AND done_at > now() - interval '30 days'")
                .getResultList().isEmpty()) {
            throw ApiException.conflict("export_required", "Descargue primero la exportación completa (generada en los últimos 30 días).");
        }
        Instant deleteAfter = tenant.archivePlan ? null : Instant.now().plus(DELETE_AFTER);
        em.createNativeQuery("""
                        UPDATE xhandler.tenant SET exit_requested_at = now(), delete_after = :d, status = 'suspended', updated_at = now()
                        WHERE id = :id""").setParameter("d", deleteAfter).setParameter("id", tenantId).executeUpdate();
        auditor.record(scope, "tenant.exit", "tenant", tenantId, Map.of("delete_after", String.valueOf(deleteAfter)));
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("status", "suspended");
        answer.put("delete_after", deleteAfter == null ? null : deleteAfter.toString());
        return answer;
    }

    Map<String, Object> view(UUID id) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT id, status, cast(parts AS text), documents, detail, created_at, done_at FROM xhandler.tenant_export WHERE id = :id""")
                .setParameter("id", id).getResultList();
        if (rows.isEmpty()) {
            throw ApiException.notFound();
        }
        Object[] r = rows.get(0);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", r[0]);
        view.put("status", r[1]);
        try {
            view.put("parts", new ObjectMapper().readValue((String) r[2], List.class));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        view.put("documents", r[3]);
        view.put("detail", r[4]);
        view.put("created_at", String.valueOf(StatusResource.toInstant(r[5])));
        view.put("done_at", r[6] == null ? null : String.valueOf(StatusResource.toInstant(r[6])));
        return view;
    }

    /** Builds the export: one ZIP per issue month (documents' XML, CDR, PDF and metadata), plus the index. */
    @ApplicationScoped
    public static class Export implements JobHandler {
        public static final String NAME = "tenants.export";
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

        @Override
        public Map<String, Object> run(JobContext context) {
            UUID exportId = context.uuidArg("export_id");
            UUID tenantId = context.tenantId();
            try {
                List<String> months = QuarkusTransaction.requiringNew().call(() -> {
                    access.enterAsPlatform(tenantId);
                    @SuppressWarnings("unchecked")
                    List<String> found = em.createNativeQuery(
                                    "SELECT DISTINCT to_char(fecha_emision, 'YYYY-MM') FROM xhandler.document ORDER BY 1").getResultList();
                    return found;
                });
                List<Map<String, Object>> parts = new ArrayList<>();
                int documents = 0;
                List<Map<String, Object>> index = new ArrayList<>();
                for (String month : months) {
                    Part part = month(tenantId, month);
                    documents += part.count();
                    FileStore.Stored stored = files.put(tenantId, part.zip());
                    parts.add(Map.of("name", "documentos-" + month + ".zip", "ref", stored.key(), "size", stored.size(), "documents", part.count()));
                    index.addAll(part.index());
                }
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

        record Part(byte[] zip, int count, List<Map<String, Object>> index) {}

        private Part month(UUID tenantId, String month) throws IOException {
            record Item(UUID id, String name, String xmlRef, String cdrRef, Map<String, Object> meta) {}
            List<Item> items = QuarkusTransaction.requiringNew().call(() -> {
                access.enterAsPlatform(tenantId);
                List<Document> docs = Document.list("to_char(fechaEmision, 'YYYY-MM') = ?1 order by fechaEmision, serie, numero", month);
                List<Item> out = new ArrayList<>();
                for (Document d : docs) {
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("id", d.id.toString());
                    meta.put("company_id", d.companyId.toString());
                    meta.put("sunat_env", d.sunatEnv);
                    meta.put("tipo_comprobante", d.tipoComprobante);
                    meta.put("serie_numero", d.serieNumero());
                    meta.put("fecha_emision", String.valueOf(d.fechaEmision));
                    meta.put("status", d.status);
                    meta.put("sunat_code", d.sunatCode);
                    meta.put("moneda", d.moneda);
                    meta.put("total", d.total == null ? null : d.total.toPlainString());
                    meta.put("customer", d.customerNumero);
                    String name = d.sunatEnv + "/" + d.tipoComprobante + "-" + d.serieNumero();
                    out.add(new Item(d.id, name, DocumentFile.of(d.id, "xml_signed").map(f -> f.storageKey).orElse(null),
                            DocumentFile.of(d.id, "cdr_zip").map(f -> f.storageKey).orElse(null), meta));
                }
                return out;
            });
            Map<String, byte[]> entries = new LinkedHashMap<>();
            List<Map<String, Object>> index = new ArrayList<>();
            for (Item item : items) {
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
                    // A document that can't be rendered still exports its XML and CDR
                    item.meta().put("pdf", "no disponible");
                }
                entries.put(item.name() + ".json", JSON.writeValueAsBytes(item.meta()));
                index.add(item.meta());
            }
            return new Part(zip(entries), items.size(), index);
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
     * Deletes the data of tenants whose exit period is over (daily): files first, then every row,
     * keeping the tenant row as deleted and the evidence in {@code tenant_deletion}.
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
                @SuppressWarnings("unchecked")
                List<String> refs = QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery("SELECT * FROM xhandler.tenant_file_refs(:t)")
                        .setParameter("t", tenantId).getResultList());
                int filesDeleted = 0;
                for (String ref : refs) {
                    if (ref != null && ref.startsWith("tenant/" + tenantId + "/")) {
                        files.delete(ref);
                        filesDeleted++;
                    }
                }
                int count = filesDeleted;
                String counts = QuarkusTransaction.requiringNew().call(() -> {
                    String result = String.valueOf(em.createNativeQuery("SELECT cast(xhandler.purge_tenant(:t) AS text)").setParameter("t", tenantId)
                            .getSingleResult());
                    em.createNativeQuery("UPDATE xhandler.tenant_deletion SET files_deleted = :f WHERE tenant_id = :t").setParameter("f", count)
                            .setParameter("t", tenantId).executeUpdate();
                    return result;
                });
                Logger.getLogger("xhandler.audit.platform").infof("tenant %s deleted after its exit period: %s, %d files", tenantId, counts, count);
                deleted++;
            }
            return Map.of("deleted", deleted);
        }
    }

    static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
