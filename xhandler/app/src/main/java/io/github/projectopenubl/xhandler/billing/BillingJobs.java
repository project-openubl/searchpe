package io.github.projectopenubl.xhandler.billing;

import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jboss.logging.Logger;

/** Lago synchronization (DESIGN.md §14): usage out of the outbox, plans back into entitlements. */
public final class BillingJobs {

    private static final Logger LOG = Logger.getLogger(BillingJobs.class);

    private BillingJobs() {}

    /** Delivers the usage outbox to Lago; events wait there while Lago is unreachable. */
    @ApplicationScoped
    public static class UsageDelivery implements JobHandler {

        static final int BATCH = 100;

        private final EntityManager em;
        private final LagoClient lago;
        private final com.fasterxml.jackson.databind.ObjectMapper json;

        public UsageDelivery(EntityManager em, LagoClient lago, com.fasterxml.jackson.databind.ObjectMapper json) {
            this.em = em;
            this.lago = lago;
            this.json = json;
        }

        @Override
        public String name() {
            return "billing.usage-delivery";
        }

        @Override
        public Map<String, Object> run(JobContext context) throws Exception {
            if (!lago.configured()) {
                return Map.of("skipped", "lago not configured");
            }
            int sent = 0;
            while (true) {
                record Event(UUID id, UUID tenant, String type, int quantity, String properties, Instant at) {}
                List<Event> batch = QuarkusTransaction.requiringNew().call(() -> {
                    @SuppressWarnings("unchecked")
                    List<Object[]> rows = em.createNativeQuery("""
                                    SELECT id, tenant_id, event_type, quantity, CAST(properties AS text), created_at
                                    FROM xhandler.unsent_usage_events(:n)""").setParameter("n", BATCH).getResultList();
                    return rows.stream().map(r -> new Event((UUID) r[0], (UUID) r[1], (String) r[2], ((Number) r[3]).intValue(),
                            (String) r[4], io.github.projectopenubl.xhandler.status.StatusResource.toInstant(r[5]))).toList();
                });
                if (batch.isEmpty()) {
                    return Map.of("sent", sent);
                }
                List<Map<String, Object>> events = new ArrayList<>();
                for (Event e : batch) {
                    Map<String, Object> properties = new LinkedHashMap<>(json.readValue(e.properties(),
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
                    properties.put("quantity", e.quantity());
                    events.add(Map.of("transaction_id", e.id().toString(), "external_subscription_id", e.tenant().toString(),
                            "code", e.type().replace('.', '_'), "timestamp", e.at().getEpochSecond(), "properties", properties));
                }
                lago.sendEvents(events);
                List<UUID> ids = batch.stream().map(Event::id).toList();
                QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("SELECT xhandler.mark_usage_sent(CAST(:ids AS uuid[]))")
                        .setParameter("ids", "{" + String.join(",", ids.stream().map(UUID::toString).toList()) + "}").getSingleResult());
                sent += batch.size();
                if (batch.size() < BATCH) {
                    return Map.of("sent", sent);
                }
            }
        }
    }

    /** Refreshes cached entitlements from Lago's subscriptions. */
    @ApplicationScoped
    public static class EntitlementSync implements JobHandler {

        private final EntityManager em;
        private final LagoClient lago;
        private final TenantAccess access;
        private final Entitlements entitlements;

        public EntitlementSync(EntityManager em, LagoClient lago, TenantAccess access, Entitlements entitlements) {
            this.em = em;
            this.lago = lago;
            this.access = access;
            this.entitlements = entitlements;
        }

        @Override
        public String name() {
            return "billing.entitlement-sync";
        }

        @Override
        public Map<String, Object> run(JobContext context) {
            if (!lago.configured()) {
                return Map.of("skipped", "lago not configured");
            }
            List<UUID> tenants = QuarkusTransaction.requiringNew().call(() -> {
                @SuppressWarnings("unchecked")
                List<UUID> ids = em.createNativeQuery("SELECT tenant_id FROM xhandler.entitlements_to_sync(interval '15 minutes', 500)")
                        .getResultList();
                return ids;
            });
            int synced = 0;
            for (UUID tenant : tenants) {
                try {
                    String plan = lago.activePlan(tenant.toString()).orElse("free");
                    QuarkusTransaction.requiringNew().run(() -> {
                        access.enterAsPlatform(tenant);
                        entitlements.setPlan(tenant, plan, "lago");
                    });
                    synced++;
                } catch (Exception e) {
                    // Keep the cached plan; after 24 hours without a sync it falls back to free
                    LOG.warnf("entitlement sync for tenant %s failed: %s", tenant, e.getMessage());
                }
            }
            return Map.of("synced", synced, "candidates", tenants.size());
        }
    }
}
