package io.github.projectopenubl.xhandler.admin;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.keys.PlatformKeys;
import io.github.projectopenubl.xhandler.secrets.PlatformCredentials;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * The platform as PSE (DESIGN.md §3.2, §21 P7): its certificate, its pool of SOL users, and the
 * rejected-CDR rate SUNAT requires (≤ 10% in the first year, then ≤ 5%, measured per half-year).
 */
@Path("/api/admin/v1/pse")
public class PseAdminResource {

    public record Rate(String period, long accepted, long rejected, BigDecimal rate, BigDecimal limit, boolean breached) {}

    private final PlatformAdmin admin;
    private final PlatformKeys keys;
    private final PlatformCredentials credentials;
    private final EntityManager em;
    private final Optional<LocalDate> registeredOn;

    public PseAdminResource(PlatformAdmin admin, PlatformKeys keys, PlatformCredentials credentials, EntityManager em,
            @ConfigProperty(name = "xhandler.pse.registered-on") Optional<LocalDate> registeredOn) {
        this.admin = admin;
        this.keys = keys;
        this.credentials = credentials;
        this.em = em;
        this.registeredOn = registeredOn;
    }

    @POST
    @Path("/certificate")
    public Map<String, Object> certificate(@RestForm("file") FileUpload file, @RestForm("password") String password) throws IOException {
        String who = admin.require();
        if (file == null || password == null) {
            throw ApiException.invalid("invalid_request", "Envíe el archivo .pfx y su contraseña.", null);
        }
        PlatformKeys.Key key = keys.importKey("pse", Files.readAllBytes(file.uploadedFile()), password.toCharArray());
        AdminResource.PLATFORM_AUDIT.infof("admin %s imported the PSE certificate %s", who, key.subject());
        return Map.of("subject", key.subject(), "ruc", String.valueOf(key.ruc()), "valid_until", key.validUntil().toString());
    }

    @POST
    @Path("/sol-users")
    @Transactional
    public PlatformCredentials.Entry addSolUser(Map<String, String> body) {
        String who = admin.require();
        String username = body == null ? null : body.get("username");
        String password = body == null ? null : body.get("password");
        if (username == null || password == null) {
            throw ApiException.invalid("invalid_request", "Indique username y password.", null);
        }
        PlatformCredentials.Entry entry = credentials.add("pse_sol", body.getOrDefault("label", username),
                Map.of("username", username, "password", password));
        AdminResource.PLATFORM_AUDIT.infof("admin %s added PSE SOL user %s", who, entry.label());
        return entry;
    }

    @GET
    @Path("/sol-users")
    @Transactional
    public List<PlatformCredentials.Entry> solUsers() {
        admin.require();
        return credentials.list("pse_sol");
    }

    @POST
    @Path("/sol-users/{id}/deactivate")
    @Transactional
    public Map<String, Object> deactivate(@PathParam("id") UUID id) {
        String who = admin.require();
        if (!credentials.setActive(id, false)) {
            throw ApiException.notFound();
        }
        AdminResource.PLATFORM_AUDIT.infof("admin %s deactivated PSE SOL user %s", who, id);
        return Map.of("id", id, "active", false);
    }

    /** The rejected-CDR rate of a half-year ({@code 2027-H1}); the current one by default. */
    @GET
    @Path("/rejected-rate")
    @Transactional
    public Rate rejectedRate(@QueryParam("period") String period) {
        admin.require();
        return rate(em, period == null ? Report.periodOf(LocalDate.now()) : period, registeredOn);
    }

    static Rate rate(EntityManager em, String period, Optional<LocalDate> registeredOn) {
        if (!period.matches("\\d{4}-H[12]")) {
            throw ApiException.invalid("invalid_request", "El periodo debe ser AAAA-H1 o AAAA-H2.", null);
        }
        int year = Integer.parseInt(period.substring(0, 4));
        LocalDate from = period.endsWith("H1") ? LocalDate.of(year, Month.JANUARY, 1) : LocalDate.of(year, Month.JULY, 1);
        LocalDate to = from.plusMonths(6);
        Object[] r = (Object[]) em.createNativeQuery("SELECT accepted, rejected FROM xhandler.pse_outcomes(:f, :t)")
                .setParameter("f", from.atStartOfDay(io.github.projectopenubl.xhandler.documents.Deadlines.LIMA).toInstant())
                .setParameter("t", to.atStartOfDay(io.github.projectopenubl.xhandler.documents.Deadlines.LIMA).toInstant())
                .getSingleResult();
        long accepted = ((Number) r[0]).longValue();
        long rejected = ((Number) r[1]).longValue();
        BigDecimal rate = accepted + rejected == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(rejected).divide(BigDecimal.valueOf(accepted + rejected), 4, RoundingMode.HALF_UP);
        // RS 199-2015 art. 7: 10% during the first year as PSE, 5% afterwards
        boolean firstYear = registeredOn.map(d -> from.isBefore(d.plusYears(1))).orElse(true);
        BigDecimal limit = firstYear ? new BigDecimal("0.10") : new BigDecimal("0.05");
        return new Rate(period, accepted, rejected, rate, limit, rate.compareTo(limit) > 0);
    }

    /** Computes the current and previous half-years, keeps them, and exposes the rate as a metric. */
    @ApplicationScoped
    public static class Report implements JobHandler {

        private final EntityManager em;
        private final Optional<LocalDate> registeredOn;
        private final AtomicReference<Double> current = new AtomicReference<>(0.0);

        public Report(EntityManager em, MeterRegistry metrics,
                @ConfigProperty(name = "xhandler.pse.registered-on") Optional<LocalDate> registeredOn) {
            this.em = em;
            this.registeredOn = registeredOn;
            metrics.gauge("xhandler.pse.rejected_rate", current, AtomicReference::get);
        }

        @Override
        public String name() {
            return "platform.pse-report";
        }

        @Override
        public Map<String, Object> run(JobContext context) {
            LocalDate today = LocalDate.now();
            String now = periodOf(today);
            String previous = periodOf(today.minusMonths(6));
            for (String period : List.of(previous, now)) {
                QuarkusTransaction.requiringNew().run(() -> {
                    Rate rate = rate(em, period, registeredOn);
                    em.createNativeQuery("""
                                    INSERT INTO xhandler.pse_report (period, accepted, rejected, rate) VALUES (:p, :a, :r, :rate)
                                    ON CONFLICT (period) DO UPDATE SET accepted = excluded.accepted, rejected = excluded.rejected,
                                        rate = excluded.rate, computed_at = now()""")
                            .setParameter("p", period).setParameter("a", rate.accepted()).setParameter("r", rate.rejected())
                            .setParameter("rate", rate.rate()).executeUpdate();
                    if (period.equals(now)) {
                        current.set(rate.rate().doubleValue());
                    }
                    if (rate.breached()) {
                        AdminResource.PLATFORM_AUDIT.errorf("PSE rejected-CDR rate %s in %s is above the %s limit", rate.rate(), period,
                                rate.limit());
                    }
                });
            }
            return Map.of("period", now, "rate", current.get(), "at", Instant.now().toString());
        }

        static String periodOf(LocalDate day) {
            return day.getYear() + (day.getMonthValue() <= 6 ? "-H1" : "-H2");
        }
    }
}
