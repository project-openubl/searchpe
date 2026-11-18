package io.github.projectopenubl.xhandler.documents;

import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Retention of deleted drafts (DESIGN.md §11.10, §16): a soft-deleted draft still holds the
 * buyer's personal data, so it is deleted for good {@code xhandler.drafts.purge-after} (30 days)
 * after its deletion. Daily, in batches; the database decides what expired.
 */
@ApplicationScoped
public class DraftPurge implements JobHandler {

    static final String NAME = "drafts.purge";
    private static final int BATCH = 1000;

    private final EntityManager em;
    private final int days;

    public DraftPurge(EntityManager em, @ConfigProperty(name = "xhandler.drafts.purge-after", defaultValue = "30d") Duration purgeAfter) {
        this.em = em;
        this.days = (int) Math.max(1, purgeAfter.toDays());
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, Object> run(JobContext context) {
        int purged = 0;
        for (int batch = 0; batch < 100; batch++) {
            int n = QuarkusTransaction.requiringNew().call(() -> ((Number) em.createNativeQuery(
                            "SELECT xhandler.purge_deleted_drafts(:days, :limit)")
                    .setParameter("days", days).setParameter("limit", BATCH).getSingleResult()).intValue());
            purged += n;
            if (n < BATCH) {
                break;
            }
        }
        if (purged > 0) {
            Logger.getLogger("xhandler.audit.platform").infof("draft retention deleted %d drafts deleted over %d days ago", purged, days);
        }
        return Map.of("drafts", purged);
    }
}
