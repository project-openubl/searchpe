package io.github.projectopenubl.xhandler.ops;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.util.List;

/**
 * Gauges for the SLO alerts (DESIGN.md §18): documents that need a person (exception, unknown for
 * over an hour, past their SUNAT deadline) and the job backlog. Read from the database on scrape,
 * so every pod reports the same and restarts lose nothing; NaN when the database doesn't answer.
 */
@Startup
@ApplicationScoped
public class HealthMetrics {

    private final EntityManager em;

    public HealthMetrics(EntityManager em, MeterRegistry metrics) {
        this.em = em;
        Gauge.builder("xhandler.documents.exception", this, h -> h.documents(0)).description("Documents in exception").register(metrics);
        Gauge.builder("xhandler.documents.unknown_stale", this, h -> h.documents(1)).description("Documents unknown for over an hour")
                .register(metrics);
        Gauge.builder("xhandler.documents.past_deadline", this, h -> h.documents(2)).description("Documents not resolved by their deadline")
                .register(metrics);
        Gauge.builder("xhandler.documents.pending", this, h -> h.documents(3)).description("Documents waiting for SUNAT").register(metrics);
        Gauge.builder("xhandler.jobs.waiting", this, h -> h.jobs(0)).description("Jobs waiting").register(metrics);
        Gauge.builder("xhandler.jobs.oldest_waiting_seconds", this, h -> h.jobs(1)).description("Age of the oldest waiting job")
                .register(metrics);
    }

    double documents(int column) {
        return read("SELECT exception, unknown_stale, past_deadline, pending FROM xhandler.document_health()", column);
    }

    double jobs(int column) {
        return read("SELECT waiting, oldest_seconds FROM xhandler.job_backlog()", column);
    }

    private double read(String sql, int column) {
        try {
            List<?> rows = QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(sql).getResultList());
            Object[] row = (Object[]) rows.get(0);
            return row[column] == null ? 0.0 : ((Number) row[column]).doubleValue();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }
}
