package io.github.projectopenubl.xhandler.documents;

import io.github.projectopenubl.xhandler.engine.Engine;
import io.github.projectopenubl.xhandler.engine.EngineModels;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.storage.FileStore;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Completes the amounts by tax of documents and receptions stored before they were recorded
 * (DESIGN.md §11.9): reads the stored XML, asks the engine, records them. Reports and SIRE refuse
 * to run on rows without them rather than write zeros. File and engine I/O happen outside
 * transactions (§10.8).
 */
@ApplicationScoped
public class AmountsBackfill implements JobHandler {

    private static final Logger LOG = Logger.getLogger(AmountsBackfill.class);
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    private final EntityManager em;
    private final TenantAccess access;
    private final Engine engine;
    private final FileStore files;

    public AmountsBackfill(EntityManager em, TenantAccess access, Engine engine, FileStore files) {
        this.em = em;
        this.access = access;
        this.engine = engine;
        this.files = files;
    }

    @Override
    public String name() {
        return "documents.amounts-backfill";
    }

    @Override
    public Map<String, Object> run(JobContext context) {
        record Item(UUID tenant, UUID id, boolean reception) {}
        List<Item> items = QuarkusTransaction.requiringNew().call(() -> {
            @SuppressWarnings("unchecked")
            List<Object[]> docs = em.createNativeQuery("SELECT tenant_id, document_id FROM xhandler.documents_without_amounts(200)").getResultList();
            @SuppressWarnings("unchecked")
            List<Object[]> receptions = em.createNativeQuery("SELECT tenant_id, reception_id FROM xhandler.receptions_without_amounts(200)")
                    .getResultList();
            return java.util.stream.Stream.concat(docs.stream().map(r -> new Item((UUID) r[0], (UUID) r[1], false)),
                    receptions.stream().map(r -> new Item((UUID) r[0], (UUID) r[1], true))).toList();
        });
        int done = 0;
        for (Item item : items) {
            try {
                if (fill(item.tenant(), item.id(), item.reception())) {
                    done++;
                }
            } catch (RuntimeException e) {
                // Marked, so it doesn't block the queue; reports treat it as missing, never as zeros
                LOG.warnf("amounts of %s %s: %s", item.reception() ? "reception" : "document", item.id(), e.toString());
                markUnavailable(item.tenant(), item.id(), item.reception());
            }
        }
        return Map.of("pending", items.size(), "filled", done);
    }

    private void markUnavailable(UUID tenantId, UUID id, boolean reception) {
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenantId);
            em.createNativeQuery("UPDATE xhandler." + (reception ? "reception" : "document")
                            + " SET amounts = cast('{\"_error\": \"unreadable\"}' AS jsonb) WHERE id = :id AND amounts IS NULL")
                    .setParameter("id", id).executeUpdate();
        });
    }

    private boolean fill(UUID tenantId, UUID id, boolean reception) {
        String key = QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(tenantId);
            List<?> ref = reception
                    ? em.createNativeQuery("SELECT xml_ref FROM xhandler.reception WHERE id = :id").setParameter("id", id).getResultList()
                    : em.createNativeQuery("SELECT storage_key FROM xhandler.document_file WHERE document_id = :id AND kind = 'xml_signed'")
                            .setParameter("id", id).getResultList();
            return ref.isEmpty() ? null : (String) ref.get(0);
        });
        if (key == null) {
            throw new IllegalStateException("no stored XML");
        }
        EngineModels.InspectResponse identity = engine.inspect(new String(files.get(key), StandardCharsets.UTF_8));
        Map<String, String> amounts = Document.amountsOf(identity.amounts());
        String json;
        try {
            json = JSON.writeValueAsString(amounts == null ? Map.of() : amounts);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenantId);
            if (reception) {
                em.createNativeQuery("UPDATE xhandler.reception SET amounts = cast(:a AS jsonb) WHERE id = :id AND amounts IS NULL")
                        .setParameter("a", json).setParameter("id", id).executeUpdate();
            } else {
                em.createNativeQuery("""
                                UPDATE xhandler.document SET amounts = cast(:a AS jsonb),
                                    payment_terms = CASE WHEN tipo_comprobante = '01' AND payment_terms IS NULL THEN :terms ELSE payment_terms END
                                WHERE id = :id AND amounts IS NULL""")
                        .setParameter("a", json).setParameter("terms", identity.paymentTerms() == null ? "contado" : identity.paymentTerms())
                        .setParameter("id", id).executeUpdate();
            }
        });
        return true;
    }
}
