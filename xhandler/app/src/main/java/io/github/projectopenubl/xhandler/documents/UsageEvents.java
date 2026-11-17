package io.github.projectopenubl.xhandler.documents;

import io.github.projectopenubl.xhandler.api.Ids;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.UUID;

/**
 * Usage for billing (DESIGN.md §14): written to {@code usage_event_outbox} in the business
 * transaction, delivered to Lago by a job. Documents count when SUNAT accepts them.
 */
@ApplicationScoped
public class UsageEvents {

    private final EntityManager em;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public UsageEvents(EntityManager em, com.fasterxml.jackson.databind.ObjectMapper json) {
        this.em = em;
        this.json = json;
    }

    void onStatusChanged(@Observes DocumentStatusChanged change) {
        Document d = change.document();
        if (Document.ACCEPTED.contains(d.status) && !Document.ACCEPTED.contains(change.previousStatus()) && !d.isSummary()) {
            record(d.tenantId, "document.accepted", Map.of("tipo_comprobante", d.tipoComprobante, "company_id", d.companyId.toString(),
                    "sunat_env", d.sunatEnv));
        }
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public void record(UUID tenantId, String type, Map<String, Object> properties) {
        try {
            em.createNativeQuery("""
                            INSERT INTO xhandler.usage_event_outbox (id, tenant_id, event_type, properties)
                            VALUES (:id, :tenant, :type, CAST(:properties AS jsonb))""")
                    .setParameter("id", Ids.newId())
                    .setParameter("tenant", tenantId)
                    .setParameter("type", type)
                    .setParameter("properties", json.writeValueAsString(properties))
                    .executeUpdate();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
