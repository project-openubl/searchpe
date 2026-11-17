package io.github.projectopenubl.xhandler.billing;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.documents.EmitQuota;
import io.github.projectopenubl.xhandler.notifications.Notifications;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Quotas from the tenant's plan, enforced at emit (DESIGN.md §11.1, §14, §26.8). Production documents
 * count per month; beta and the sandbox per day (Lima). The entitlement is cached from Lago; when
 * Lago hasn't been reached for 24 hours, quotas fall back to the free plan (fail closed). Usage is
 * counted from the documents themselves, so there is no hot counter row.
 */
@ApplicationScoped
public class Entitlements implements EmitQuota {

    /** A plan's limits. Null limits are unlimited. */
    public record Plan(String code, String name, Integer productionPerMonth, Integer betaPerDay, Integer sandboxPerDay,
            boolean customSmtp, boolean webhooks) {}

    /** What a tenant may do now, and what it used. */
    public record Usage(Plan plan, String source, Instant syncedAt, boolean stale, long productionThisMonth, long betaToday,
            long sandboxToday) {}

    static final Duration STALE_AFTER = Duration.ofHours(24);

    private final EntityManager em;
    private final Notifications notifications;
    private final io.github.projectopenubl.xhandler.tenancy.TenantAccess access;
    private final String defaultPlan;

    public Entitlements(EntityManager em, Notifications notifications, io.github.projectopenubl.xhandler.tenancy.TenantAccess access,
            @org.eclipse.microprofile.config.inject.ConfigProperty(name = "xhandler.billing.default-plan", defaultValue = "free") String defaultPlan) {
        this.defaultPlan = defaultPlan;
        this.em = em;
        this.notifications = notifications;
        this.access = access;
    }

    @Override
    @Transactional(Transactional.TxType.MANDATORY)
    public void checkEmit(UUID tenantId, String sunatEnv) {
        Usage usage = usage(tenantId);
        Plan plan = usage.plan();
        switch (sunatEnv) {
            case "production" -> {
                Integer limit = plan.productionPerMonth();
                if (limit != null && usage.productionThisMonth() >= limit) {
                    // The emit is refused (its transaction rolls back): the notice commits on its own
                    io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
                        access.enterAsPlatform(tenantId);
                        notifyQuota(tenantId, usage, 100);
                    });
                    throw new ApiException(402, "quota_exceeded",
                            "Alcanzó el límite de " + limit + " documentos del mes en su plan " + plan.name() + ".");
                }
                if (limit != null && usage.productionThisMonth() + 1 >= Math.ceil(limit * 0.8)) {
                    notifyQuota(tenantId, usage, 80);
                }
            }
            case "beta" -> check(plan.betaPerDay(), usage.betaToday(), "SUNAT beta");
            default -> check(plan.sandboxPerDay(), usage.sandboxToday(), "el ambiente de pruebas");
        }
    }

    private static void check(Integer limit, long used, String where) {
        if (limit != null && used >= limit) {
            throw new ApiException(429, "quota_exceeded", "Alcanzó el límite diario de " + limit + " documentos en " + where + ".");
        }
    }

    /** With the tenant open. */
    @Transactional(Transactional.TxType.MANDATORY)
    public Usage usage(UUID tenantId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT plan_code, source, synced_at FROM xhandler.entitlement WHERE tenant_id = :t")
                .setParameter("t", tenantId).getResultList();
        String code = rows.isEmpty() ? defaultPlan : (String) rows.get(0)[0];
        String source = rows.isEmpty() ? "default" : (String) rows.get(0)[1];
        Instant syncedAt = rows.isEmpty() ? null : io.github.projectopenubl.xhandler.status.StatusResource.toInstant(rows.get(0)[2]);
        boolean stale = "lago".equals(source) && syncedAt != null && syncedAt.isBefore(Instant.now().minus(STALE_AFTER));
        // Lago unreachable for too long: fail closed to the free plan's quotas
        Plan plan = plan(stale ? "free" : code);
        LocalDate today = io.github.projectopenubl.xhandler.documents.Deadlines.todayInLima();
        Instant monthStart = YearMonth.from(today).atDay(1).atStartOfDay(io.github.projectopenubl.xhandler.documents.Deadlines.LIMA).toInstant();
        Instant dayStart = today.atStartOfDay(io.github.projectopenubl.xhandler.documents.Deadlines.LIMA).toInstant();
        Object[] counts = (Object[]) em.createNativeQuery("""
                        SELECT count(*) FILTER (WHERE sunat_env = 'production' AND created_at >= :month),
                               count(*) FILTER (WHERE sunat_env = 'beta' AND created_at >= :day),
                               count(*) FILTER (WHERE sunat_env = 'fake' AND created_at >= :day)
                        FROM xhandler.document WHERE created_at >= :month AND origin = 'api'""")
                .setParameter("month", monthStart).setParameter("day", dayStart).getSingleResult();
        return new Usage(plan, source, syncedAt, stale, ((Number) counts[0]).longValue(), ((Number) counts[1]).longValue(),
                ((Number) counts[2]).longValue());
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public Plan plan(String code) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT code, name, production_per_month, beta_per_day, sandbox_per_day, custom_smtp, webhooks FROM xhandler.plan
                        WHERE code = :c""").setParameter("c", code).getResultList();
        Object[] r = rows.isEmpty()
                ? (Object[]) em.createNativeQuery("SELECT code, name, production_per_month, beta_per_day, sandbox_per_day, custom_smtp, webhooks "
                        + "FROM xhandler.plan WHERE code = 'free'").getSingleResult()
                : rows.get(0);
        return new Plan((String) r[0], (String) r[1], (Integer) r[2], (Integer) r[3], (Integer) r[4], (Boolean) r[5], (Boolean) r[6]);
    }

    /** Whether the service knows this plan code. */
    @Transactional(Transactional.TxType.MANDATORY)
    public boolean known(String code) {
        return !em.createNativeQuery("SELECT 1 FROM xhandler.plan WHERE code = :c").setParameter("c", code).getResultList().isEmpty();
    }

    /** Sets a tenant's plan (from Lago, or by a platform admin). Unknown codes are refused, never mapped to free. */
    @Transactional(Transactional.TxType.MANDATORY)
    public void setPlan(UUID tenantId, String code, String source) {
        if (!known(code)) {
            throw ApiException.invalid("unknown_plan", "Plan desconocido: " + code + ".", null);
        }
        em.createNativeQuery("""
                        INSERT INTO xhandler.entitlement (tenant_id, plan_code, source, synced_at) VALUES (:t, :c, :s, now())
                        ON CONFLICT (tenant_id) DO UPDATE SET plan_code = excluded.plan_code, source = excluded.source, synced_at = now()""")
                .setParameter("t", tenantId).setParameter("c", plan(code).code()).setParameter("s", source).executeUpdate();
    }

    /** The plan allows a tenant SMTP server (free tenants send through the platform, §26.8). */
    @Transactional(Transactional.TxType.MANDATORY)
    public void requireCustomSmtp(Company company) {
        if (!usage(company.tenantId).plan().customSmtp()) {
            throw new ApiException(402, "plan_required", "Su plan no permite un servidor de correo propio.");
        }
    }

    /** 80% and 100% of the monthly quota, notified once per month each (§26.6). */
    private void notifyQuota(UUID tenantId, Usage usage, int percent) {
        String period = YearMonth.now(io.github.projectopenubl.xhandler.documents.Deadlines.LIMA).toString();
        String column = percent >= 100 ? "notified_100" : "notified_80";
        em.createNativeQuery("""
                        INSERT INTO xhandler.entitlement (tenant_id, plan_code) VALUES (:t, 'free') ON CONFLICT (tenant_id) DO NOTHING""")
                .setParameter("t", tenantId).executeUpdate();
        int updated = em.createNativeQuery("UPDATE xhandler.entitlement SET " + column + " = :p WHERE tenant_id = :t AND "
                        + column + " IS DISTINCT FROM :p")
                .setParameter("p", period).setParameter("t", tenantId).executeUpdate();
        if (updated == 1) {
            notifications.publish(tenantId, Notifications.QUOTA, "Uso del plan: " + percent + "%",
                    "Su empresa usó el " + percent + "% de los " + usage.plan().productionPerMonth() + " documentos del mes de su plan "
                            + usage.plan().name() + ".", null);
        }
    }
}
