package io.github.projectopenubl.xhandler.notifications;

import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Scheduled tenant notices (DESIGN.md §26.6): expiring certificates and SUNAT outages. */
public final class PlatformNotices {

    private PlatformNotices() {}

    /** Certificates expiring in 30, 7 or 1 days, each threshold notified once. */
    @ApplicationScoped
    public static class CertificateExpiry implements JobHandler {

        private final EntityManager em;
        private final TenantAccess access;
        private final Notifications notifications;

        public CertificateExpiry(EntityManager em, TenantAccess access, Notifications notifications) {
            this.em = em;
            this.access = access;
            this.notifications = notifications;
        }

        @Override
        public String name() {
            return "platform.certificate-expiry";
        }

        @Override
        public Map<String, Object> run(JobContext context) {
            int notified = 0;
            for (int days : new int[] {1, 7, 30}) {
                record Expiring(UUID tenantId, UUID companyId, UUID keyId, Instant validUntil) {}
                List<Expiring> expiring = QuarkusTransaction.requiringNew().call(() -> {
                    @SuppressWarnings("unchecked")
                    List<Object[]> rows = em.createNativeQuery(
                                    "SELECT tenant_id, company_id, signing_key_id, valid_until FROM xhandler.expiring_certificates(:d)")
                            .setParameter("d", days).getResultList();
                    return rows.stream().map(r -> new Expiring((UUID) r[0], (UUID) r[1], (UUID) r[2],
                            io.github.projectopenubl.xhandler.status.StatusResource.toInstant(r[3]))).toList();
                });
                for (Expiring e : expiring) {
                    QuarkusTransaction.requiringNew().run(() -> {
                        access.enterAsPlatform(e.tenantId());
                        var company = io.github.projectopenubl.xhandler.company.Company.<io.github.projectopenubl.xhandler.company.Company>findById(e.companyId());
                        long left = Math.max(0, ChronoUnit.DAYS.between(Instant.now(), e.validUntil()));
                        notifications.publish(e.tenantId(), Notifications.CERTIFICATE_EXPIRING,
                                "Certificado digital por vencer: " + company.ruc,
                                "El certificado digital de " + company.razonSocial + " vence en " + left + " día(s) (" + e.validUntil()
                                        + "). Suba uno nuevo para seguir firmando.", null);
                        em.createNativeQuery("UPDATE xhandler.signing_key SET expiry_notified_days = :d WHERE id = :id")
                                .setParameter("d", days).setParameter("id", e.keyId()).executeUpdate();
                    });
                    notified++;
                }
            }
            // The platform's own keys (PSE, OSE CDR): their expiry stops every company using them
            QuarkusTransaction.requiringNew().run(() -> {
                @SuppressWarnings("unchecked")
                List<Object[]> platform = em.createNativeQuery("""
                                SELECT purpose, valid_until FROM xhandler.platform_key WHERE status = 'active' AND valid_until < now() + interval '30 days'""")
                        .getResultList();
                for (Object[] key : platform) {
                    org.jboss.logging.Logger.getLogger("xhandler.audit.platform").errorf("platform %s certificate expires at %s: replace it", key[0], key[1]);
                }
            });
            return Map.of("notified", notified);
        }
    }

    /** SUNAT outage started or ended on an endpoint: tenants with documents waiting there are told. */
    @ApplicationScoped
    public static class SunatHealth implements JobHandler {

        private final EntityManager em;
        private final TenantAccess access;
        private final Notifications notifications;

        public SunatHealth(EntityManager em, TenantAccess access, Notifications notifications) {
            this.em = em;
            this.access = access;
            this.notifications = notifications;
        }

        @Override
        public String name() {
            return "platform.sunat-health";
        }

        @Override
        public Map<String, Object> run(JobContext context) {
            record Change(String endpoint, String state) {}
            // Debounced: an endpoint is "down" after 5 minutes with its breaker not closed, and "up"
            // is only announced after a "down" notice. Breakers flap during partial outages.
            List<Change> changes = QuarkusTransaction.requiringNew().call(() -> {
                em.createNativeQuery("""
                                INSERT INTO xhandler.endpoint_alert (endpoint, state)
                                SELECT e.id, 'suspect' FROM xhandler.sunat_endpoint e
                                WHERE (e.id LIKE 'production:%' OR e.id LIKE 'ose:%') AND e.breaker_state <> 'closed'
                                ON CONFLICT (endpoint) DO UPDATE SET state = 'suspect', at = now()
                                WHERE endpoint_alert.state = 'up'""").executeUpdate();
                em.createNativeQuery("""
                                UPDATE xhandler.endpoint_alert a SET state = 'up', at = now() FROM xhandler.sunat_endpoint e
                                WHERE e.id = a.endpoint AND a.state = 'suspect' AND e.breaker_state = 'closed'""").executeUpdate();
                @SuppressWarnings("unchecked")
                List<Object[]> rows = em.createNativeQuery("""
                                SELECT a.endpoint, CASE WHEN a.state = 'suspect' THEN 'down' ELSE 'up' END
                                FROM xhandler.endpoint_alert a JOIN xhandler.sunat_endpoint e ON e.id = a.endpoint
                                WHERE (a.state = 'suspect' AND e.breaker_state <> 'closed' AND a.at < now() - interval '5 minutes')
                                   OR (a.state = 'down' AND e.breaker_state = 'closed')""")
                        .getResultList();
                return rows.stream().map(r -> new Change((String) r[0], (String) r[1])).toList();
            });
            for (Change change : changes) {
                // Record the state first: a failing tenant must not make everyone be notified again
                QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                                "UPDATE xhandler.endpoint_alert SET state = :s, at = now() WHERE endpoint = :e")
                        .setParameter("e", change.endpoint()).setParameter("s", change.state()).executeUpdate());
                List<UUID> tenants = QuarkusTransaction.requiringNew().call(() -> {
                    @SuppressWarnings("unchecked")
                    List<Object[]> rows = em.createNativeQuery("SELECT tenant_id, documents FROM xhandler.tenants_waiting_on(:e)")
                            .setParameter("e", change.endpoint()).getResultList();
                    return rows.stream().map(r -> (UUID) r[0]).toList();
                });
                for (UUID tenant : tenants) {
                    try {
                        QuarkusTransaction.requiringNew().run(() -> {
                            access.enterAsPlatform(tenant);
                            boolean down = "down".equals(change.state());
                            notifications.publish(tenant, Notifications.SUNAT_OUTAGE, down ? "SUNAT no responde" : "SUNAT volvió a responder",
                                    down ? "SUNAT no está respondiendo. Sus documentos están guardados y se enviarán automáticamente cuando vuelva."
                                            : "SUNAT volvió a responder. Los documentos pendientes se están enviando por orden de plazo.", null);
                        });
                    } catch (RuntimeException e) {
                        org.jboss.logging.Logger.getLogger(SunatHealth.class).warnf("outage notice for tenant %s failed: %s", tenant, e.getMessage());
                    }
                }
            }
            return Map.of("changes", changes.size());
        }
    }
}
