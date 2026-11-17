package io.github.projectopenubl.xhandler.ops;

import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;

/**
 * Gauges for the SLO alerts (DESIGN.md §18): documents that need a person (production, not held,
 * active tenants: exception in the last day, unknown for over an hour, past their SUNAT deadline),
 * documents waiting for SUNAT and the backlog of ready jobs. The {@code ops.snapshot} job computes
 * them once every 30 seconds for the whole cluster; every pod's gauges read that small table on
 * scrape (NaN when the database doesn't answer or the snapshot is older than 5 minutes).
 */
@Startup
@ApplicationScoped
public class HealthMetrics {

    static final List<String> NAMES = List.of("documents.exception", "documents.unknown_stale", "documents.past_deadline", "documents.pending",
            "jobs.waiting", "jobs.oldest_waiting_seconds");

    private final EntityManager em;

    public HealthMetrics(EntityManager em, MeterRegistry metrics) {
        this.em = em;
        for (String name : NAMES) {
            Gauge.builder("xhandler." + name, this, h -> h.read(name)).register(metrics);
        }
    }

    double read(String name) {
        try {
            List<?> rows = QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                    "SELECT value FROM xhandler.ops_snapshot WHERE name = :n AND at > now() - interval '5 minutes'").setParameter("n", name)
                    .getResultList());
            return rows.isEmpty() ? Double.NaN : ((Number) rows.get(0)).doubleValue();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    /** Computes the snapshot (scheduled every 30 seconds, one run in the cluster). */
    @ApplicationScoped
    public static class Snapshot implements JobHandler {
        private final EntityManager em;

        public Snapshot(EntityManager em) {
            this.em = em;
        }

        @Override
        public String name() {
            return "ops.snapshot";
        }

        @Override
        public Map<String, Object> run(JobContext context) {
            QuarkusTransaction.requiringNew().run(() -> {
                Object[] d = (Object[]) em.createNativeQuery("SELECT exception, unknown_stale, past_deadline, pending FROM xhandler.document_health()")
                        .getSingleResult();
                Object[] j = (Object[]) em.createNativeQuery("SELECT waiting, oldest_seconds FROM xhandler.job_backlog()").getSingleResult();
                Object[] values = {d[0], d[1], d[2], d[3], j[0], j[1]};
                for (int i = 0; i < NAMES.size(); i++) {
                    em.createNativeQuery("""
                                    INSERT INTO xhandler.ops_snapshot (name, value, at) VALUES (:n, :v, now())
                                    ON CONFLICT (name) DO UPDATE SET value = excluded.value, at = excluded.at""")
                            .setParameter("n", NAMES.get(i)).setParameter("v", values[i] == null ? 0.0 : ((Number) values[i]).doubleValue())
                            .executeUpdate();
                }
            });
            return Map.of();
        }
    }
}
