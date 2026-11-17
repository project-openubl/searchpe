package io.github.projectopenubl.xhandler.documents;

import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.webhooks.WebhookEvents;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Deadline alerts (DESIGN.md §9.2.4, §24.1): a document without a definitive answer whose legal
 * deadline (America/Lima) is near gets one {@code document.delayed} webhook, a tenant notification
 * and a timeline entry. It is still sent if the deadline passes, flagged as late.
 */
@ApplicationScoped
public class DeadlineAlerts implements JobHandler {

    /** Fired with the tenant open, in the alert's transaction (tenant notifications observe it). */
    public record DocumentAtRisk(Document document) {}

    private final EntityManager em;
    private final TenantAccess access;
    private final WebhookEvents webhooks;
    private final Event<DocumentAtRisk> atRisk;
    private final MeterRegistry metrics;
    private final Duration horizon;

    public DeadlineAlerts(EntityManager em, TenantAccess access, WebhookEvents webhooks, Event<DocumentAtRisk> atRisk,
            MeterRegistry metrics, @ConfigProperty(name = "xhandler.documents.deadline-alert", defaultValue = "12h") Duration horizon) {
        this.em = em;
        this.access = access;
        this.webhooks = webhooks;
        this.atRisk = atRisk;
        this.metrics = metrics;
        this.horizon = horizon;
    }

    @Override
    public String name() {
        return "documents.deadline-alerts";
    }

    @Override
    public Map<String, Object> run(JobContext context) {
        record AtRisk(UUID tenantId, UUID documentId) {}
        List<AtRisk> documents = QuarkusTransaction.requiringNew().call(() -> {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery(
                            "SELECT tenant_id, document_id FROM xhandler.documents_at_risk(make_interval(secs => :secs))")
                    .setParameter("secs", (double) horizon.toSeconds())
                    .getResultList();
            return rows.stream().map(r -> new AtRisk((UUID) r[0], (UUID) r[1])).toList();
        });
        int alerted = 0;
        for (AtRisk risk : documents) {
            boolean sent = QuarkusTransaction.requiringNew().call(() -> {
                access.enterAsPlatform(risk.tenantId());
                Document doc = Document.findById(risk.documentId(), LockModeType.PESSIMISTIC_WRITE);
                if (doc == null || doc.deadlineAlertedAt != null || (doc.definitive() && !"exception".equals(doc.status))) {
                    return false;
                }
                doc.deadlineAlertedAt = Instant.now();
                boolean late = doc.deadlineAt.isBefore(doc.deadlineAlertedAt);
                DocumentEvent.record(doc, null, late ? "legal deadline passed without SUNAT's answer" : "legal deadline at risk",
                        "platform:worker", context.correlationId());
                Map<String, Object> data = new java.util.LinkedHashMap<>(WebhookEvents.documentData(doc));
                data.put("deadline_at", doc.deadlineAt.toString());
                data.put("late", late);
                webhooks.publish(doc.tenantId, "document.delayed", "xh:document:" + doc.id, WebhookEvents.DOCUMENT_SCHEMA, data);
                atRisk.fire(new DocumentAtRisk(doc));
                return true;
            });
            if (sent) {
                alerted++;
            }
        }
        metrics.counter("xhandler.documents.deadline_alerts").increment(alerted);
        return Map.of("alerted", alerted);
    }
}
