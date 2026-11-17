package io.github.projectopenubl.xhandler.ops;

import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.storage.FileStore;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Retention (DESIGN.md §13): documents older than the tenant's retention years (10 by default,
 * counted in full years after the issue year) are deleted with their events, files and links,
 * never while a tax audit holds the tenant. Daily, in batches; the database decides what expired.
 */
@ApplicationScoped
public class RetentionPurge implements JobHandler {

    private static final Logger LOG = Logger.getLogger(RetentionPurge.class);

    private final EntityManager em;
    private final FileStore files;

    public RetentionPurge(EntityManager em, FileStore files) {
        this.em = em;
        this.files = files;
    }

    @Override
    public String name() {
        return "retention.purge";
    }

    @Override
    public Map<String, Object> run(JobContext context) {
        int documents = 0;
        int deletedFiles = 0;
        for (int batch = 0; batch < 20; batch++) {
            @SuppressWarnings("unchecked")
            List<Object[]> expired = QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                    "SELECT tenant_id, document_id FROM xhandler.expired_documents(500)").getResultList());
            if (expired.isEmpty()) {
                break;
            }
            Map<UUID, List<UUID>> byTenant = new LinkedHashMap<>();
            for (Object[] r : expired) {
                byTenant.computeIfAbsent((UUID) r[0], k -> new ArrayList<>()).add((UUID) r[1]);
            }
            for (Map.Entry<UUID, List<UUID>> e : byTenant.entrySet()) {
                @SuppressWarnings("unchecked")
                List<String> keys = QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                                "SELECT * FROM xhandler.purge_documents(:t, cast(:ids AS uuid[]))")
                        .setParameter("t", e.getKey()).setParameter("ids", e.getValue().toArray(UUID[]::new)).getResultList());
                documents += e.getValue().size();
                for (String key : keys) {
                    try {
                        files.delete(key);
                        deletedFiles++;
                    } catch (RuntimeException ex) {
                        LOG.warnf("retention could not delete %s: %s", key, ex.toString());
                    }
                }
            }
        }
        if (documents > 0) {
            Logger.getLogger("xhandler.audit.platform").infof("retention deleted %d documents and %d files", documents, deletedFiles);
        }
        return Map.of("documents", documents, "files", deletedFiles);
    }
}
