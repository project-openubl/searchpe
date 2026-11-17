package io.github.projectopenubl.xhandler.sire;

import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.notifications.Notifications;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jboss.logging.Logger;

/** The SIRE jobs (DESIGN.md §11.7, §10.6). */
public final class SireJobs {

    private static final Logger LOG = Logger.getLogger(SireJobs.class);

    private SireJobs() {}

    /** A user asked for SUNAT's proposal. */
    @ApplicationScoped
    public static class Request implements JobHandler {
        public static final String NAME = "sire.request";
        private final SireService sire;

        public Request(SireService sire) {
            this.sire = sire;
        }

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public Map<String, Object> run(JobContext context) {
            sire.requestProposal(context.tenantId(), context.uuidArg("sire_period_id"));
            return Map.of();
        }
    }

    /** A user asked to submit the replacement. */
    @ApplicationScoped
    public static class Replace implements JobHandler {
        public static final String NAME = "sire.replace";
        private final SireService sire;

        public Replace(SireService sire) {
            this.sire = sire;
        }

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public Map<String, Object> run(JobContext context) {
            sire.replace(context.tenantId(), context.uuidArg("sire_period_id"));
            return Map.of();
        }
    }

    /** Follows SUNAT's tickets of every tenant (scheduled). */
    @ApplicationScoped
    public static class Poll implements JobHandler {
        private final SireService sire;
        private final EntityManager em;

        public Poll(SireService sire, EntityManager em) {
            this.sire = sire;
            this.em = em;
        }

        @Override
        public String name() {
            return "sire.poll";
        }

        @Override
        public Map<String, Object> run(JobContext context) {
            record Due(UUID tenant, UUID id) {}
            List<Due> due = QuarkusTransaction.requiringNew().call(() -> {
                @SuppressWarnings("unchecked")
                List<Object[]> rows = em.createNativeQuery("SELECT tenant_id, sire_period_id FROM xhandler.sire_periods_to_poll(50)").getResultList();
                return rows.stream().map(r -> new Due((UUID) r[0], (UUID) r[1])).toList();
            });
            for (Due d : due) {
                try {
                    sire.poll(d.tenant(), d.id());
                } catch (RuntimeException e) {
                    // One period's failure doesn't stop the rest
                    LOG.warnf("SIRE poll of %s failed: %s", d.id(), e.toString());
                }
            }
            return Map.of("polled", due.size());
        }
    }

    /**
     * Warns of sales registers due within 3 days that SUNAT hasn't accepted (scheduled, daily), from
     * the calendar platform admins load. Once per company and period.
     */
    @ApplicationScoped
    public static class Deadlines implements JobHandler {
        private final EntityManager em;
        private final TenantAccess access;
        private final Notifications notifications;

        public Deadlines(EntityManager em, TenantAccess access, Notifications notifications) {
            this.em = em;
            this.access = access;
            this.notifications = notifications;
        }

        @Override
        public String name() {
            return "sire.deadlines";
        }

        @Override
        public Map<String, Object> run(JobContext context) {
            record Due(UUID tenant, UUID company, String ruc, String period, LocalDate dueDate) {}
            List<Due> due = QuarkusTransaction.requiringNew().call(() -> {
                @SuppressWarnings("unchecked")
                List<Object[]> rows = em.createNativeQuery("SELECT tenant_id, company_id, ruc, period, due_date FROM xhandler.sire_deadlines_due(3)")
                        .getResultList();
                return rows.stream().map(r -> new Due((UUID) r[0], (UUID) r[1], (String) r[2], (String) r[3],
                        r[4] instanceof LocalDate d ? d : ((java.sql.Date) r[4]).toLocalDate())).toList();
            });
            int notified = 0;
            for (Due d : due) {
                boolean sent = QuarkusTransaction.requiringNew().call(() -> {
                    access.enterAsPlatform(d.tenant());
                    String title = "SIRE: registro de ventas " + d.period() + " de " + d.ruc() + " vence el " + d.dueDate();
                    if (!em.createNativeQuery("SELECT 1 FROM xhandler.notification WHERE kind = 'sire.deadline' AND title = :t")
                            .setParameter("t", title).getResultList().isEmpty()) {
                        return false;
                    }
                    notifications.publish(d.tenant(), "sire.deadline", title,
                            "SUNAT aún no tiene aceptado el registro de ventas (RVIE) del periodo " + d.period()
                                    + ". Revise la propuesta y envíe el registro antes del vencimiento.", null);
                    return true;
                });
                if (sent) {
                    notified++;
                }
            }
            return Map.of("due", due.size(), "notified", notified);
        }
    }
}
