package io.github.projectopenubl.xhandler.webhooks;

import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.documents.Document;
import io.github.projectopenubl.xhandler.documents.DocumentStatusChanged;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns domain events into webhook deliveries (DESIGN.md §12), in the transaction that caused them
 * (an outbox: nothing is delivered for a rolled-back change). Payloads are CloudEvents 1.0 with a
 * versioned {@code dataschema}.
 */
@ApplicationScoped
public class WebhookEvents {

    public static final String SOURCE = "urn:xhandler-service";
    public static final String DOCUMENT_SCHEMA = "urn:xhandler:schema:document-event:v1";

    /** Status changes become document.* events. */
    void onStatusChanged(@Observes DocumentStatusChanged change) {
        Document d = change.document();
        String type = switch (d.status) {
            case "accepted", "accepted_with_observations" -> "document.accepted";
            case "rejected" -> "document.rejected";
            case "exception" -> "document.exception";
            case "voided" -> "document.voided";
            default -> null;
        };
        if (type == null) {
            return;
        }
        publish(d.tenantId, type, "xh:document:" + d.id, DOCUMENT_SCHEMA, documentData(d));
        if ("rejected".equals(d.status) && d.deliveredAt != null) {
            // A document the buyer already has was rejected: warn (§9.3)
            publish(d.tenantId, "document.rejected_after_delivery", "xh:document:" + d.id, DOCUMENT_SCHEMA, documentData(d));
        }
    }

    /** Queues an event for every active subscription of the tenant that wants it. */
    @Transactional(Transactional.TxType.MANDATORY)
    public int publish(UUID tenantId, String type, String subject, String dataSchema, Map<String, Object> data) {
        List<WebhookSubscription> subscriptions = WebhookSubscription.list("tenantId = ?1 and active and deletedAt is null", tenantId);
        UUID eventId = Ids.newId();
        Instant now = Instant.now();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("specversion", "1.0");
        event.put("id", eventId.toString());
        event.put("source", SOURCE);
        event.put("type", type);
        event.put("subject", subject);
        event.put("time", now.toString());
        event.put("datacontenttype", "application/json");
        event.put("dataschema", dataSchema);
        event.put("data", data);
        int queued = 0;
        for (WebhookSubscription subscription : subscriptions) {
            if (!subscription.wants(type)) {
                continue;
            }
            WebhookDelivery delivery = new WebhookDelivery();
            delivery.id = Ids.newId();
            delivery.tenantId = tenantId;
            delivery.subscriptionId = subscription.id;
            delivery.eventId = eventId;
            delivery.eventType = type;
            delivery.payload = event;
            delivery.nextAttemptAt = now;
            delivery.createdAt = now;
            delivery.updatedAt = now;
            delivery.persist();
            queued++;
        }
        return queued;
    }

    public static Map<String, Object> documentData(Document d) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", d.id.toString());
        data.put("prn", "xh:document:" + d.id);
        data.put("company_id", d.companyId.toString());
        data.put("sunat_env", d.sunatEnv);
        data.put("tipo_comprobante", d.tipoComprobante);
        data.put("serie_numero", d.serieNumero());
        data.put("fecha_emision", d.fechaEmision.toString());
        data.put("status", d.status);
        data.put("sunat_code", d.sunatCode);
        data.put("sunat_description", d.sunatDescription);
        data.put("delivered", d.deliveredAt != null);
        data.put("labels", d.labels);
        return data;
    }
}
