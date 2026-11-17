package io.github.projectopenubl.xhandler.ose;

import io.github.projectopenubl.xhandler.engine.Engine;
import io.github.projectopenubl.xhandler.engine.EngineException;
import io.github.projectopenubl.xhandler.engine.EngineModels;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.secrets.PlatformCredentials;
import io.github.projectopenubl.xhandler.storage.FileStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/** The OSE's jobs (DESIGN.md §11.4 steps 6–7, §10.6). */
public final class OseJobs {

    private static final Logger LOG = Logger.getLogger(OseJobs.class);
    private static final Logger AUDIT = Logger.getLogger("xhandler.audit.platform");

    private OseJobs() {}

    /**
     * Forwards each CDR-OSE and its document to SUNAT within 1 hour of issuing the CDR (RS 117-2017
     * art. 7.5; SUNAT flags later ones with 4196). Runs every 30 seconds at priority 1. Each run
     * leases its batch for 2 minutes ({@code FOR UPDATE SKIP LOCKED}), calls SUNAT with no
     * transaction open, and records the outcome; ambiguous outcomes are retried (SUNAT acknowledges
     * a resend). The delay from {@code cdr_issued_at} is measured, and the oldest pending forward
     * is a gauge read from the database.
     */
    @ApplicationScoped
    public static class Forwarder implements JobHandler {

        static final Duration LEASE = Duration.ofMinutes(2);
        static final Duration WARN_AFTER = Duration.ofMinutes(45);

        private final jakarta.persistence.EntityManager em;
        private final Engine engine;
        private final FileStore files;
        private final PlatformCredentials credentials;
        private final Timer delay;
        private final String environment;
        private final Optional<String> url;

        public Forwarder(jakarta.persistence.EntityManager em, Engine engine, FileStore files, PlatformCredentials credentials,
                MeterRegistry metrics, @ConfigProperty(name = "xhandler.ose.environment", defaultValue = "fake") String environment,
                @ConfigProperty(name = "xhandler.ose.forward-url") Optional<String> url) {
            this.em = em;
            this.engine = engine;
            this.files = files;
            this.credentials = credentials;
            this.environment = environment;
            this.url = url;
            this.delay = Timer.builder("xhandler.ose.forward_delay").description("From CDR-OSE to SUNAT's acknowledgement (limit: 1 hour)")
                    .publishPercentiles(0.5, 0.99, 1.0).register(metrics);
            Gauge.builder("xhandler.ose.forward_oldest_seconds", this, Forwarder::oldestPending)
                    .description("Age of the oldest CDR-OSE not yet forwarded").register(metrics);
        }

        double oldestPending() {
            try {
                List<?> age = QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                                "SELECT extract(epoch FROM now() - min(cdr_issued_at)) FROM xhandler.ose_submission WHERE forward_status = 'pending'")
                        .getResultList());
                return age.isEmpty() || age.get(0) == null ? 0.0 : ((Number) age.get(0)).doubleValue();
            } catch (RuntimeException e) {
                return Double.NaN;
            }
        }

        @Override
        public String name() {
            return "ose.forward";
        }

        @Override
        public Map<String, Object> run(JobContext context) {
            record Due(UUID id, String name, String tipo, String xmlRef, String cdrRef, Instant issuedAt, int attempts) {}
            List<Due> due = QuarkusTransaction.requiringNew().call(() -> {
                @SuppressWarnings("unchecked")
                List<Object[]> rows = em.createNativeQuery("""
                                SELECT id, name, tipo, xml_ref, cdr_ref, cdr_issued_at, forward_attempts FROM xhandler.ose_submission
                                WHERE forward_status = 'pending' AND next_attempt_at <= now()
                                ORDER BY cdr_issued_at LIMIT 50 FOR UPDATE SKIP LOCKED""").getResultList();
                for (Object[] r : rows) {
                    em.createNativeQuery("UPDATE xhandler.ose_submission SET next_attempt_at = now() + interval '2 minutes' WHERE id = :id")
                            .setParameter("id", r[0]).executeUpdate();
                }
                return rows.stream().map(r -> new Due((UUID) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4],
                        io.github.projectopenubl.xhandler.status.StatusResource.toInstant(r[5]), ((Number) r[6]).intValue())).toList();
            });
            if (due.isEmpty()) {
                return Map.of("forwarded", 0);
            }
            EngineModels.Environment env = switch (environment) {
                case "production" -> EngineModels.Environment.PRODUCTION;
                case "beta" -> EngineModels.Environment.BETA;
                default -> EngineModels.Environment.FAKE;
            };
            EngineModels.OseCredentials creds = QuarkusTransaction.requiringNew().call(() -> credentials.pick("ose_sunat")
                    .map(id -> credentials.read(id, "ose_sunat", "ose-forward"))
                    .map(f -> new EngineModels.OseCredentials(f.get("username"), f.get("password")))
                    .orElse(null));
            if (creds == null && env != EngineModels.Environment.FAKE) {
                AUDIT.error("OSE forwarding has no SUNAT credentials (platform credential ose_sunat): CDRs are piling up");
                return Map.of("forwarded", 0, "error", "no credentials");
            }
            EngineModels.OseCredentials use = creds != null ? creds : new EngineModels.OseCredentials("OSEFAKE", "fake");
            int forwarded = 0;
            for (Due d : due) {
                String xml = new String(files.get(d.xmlRef()), StandardCharsets.UTF_8);
                String cdr = new String(files.get(d.cdrRef()), StandardCharsets.UTF_8);
                String operation = List.of("RC", "RA", "RR").contains(d.tipo()) ? "send_summary" : "send_bill";
                EngineModels.OseForwardResponse response;
                try {
                    response = engine.oseForward(new EngineModels.OseForwardRequest(env, url.orElse(null), use, d.name(), xml, cdr, operation));
                } catch (EngineException e) {
                    response = new EngineModels.OseForwardResponse("unknown", null, e.getMessage());
                }
                EngineModels.OseForwardResponse outcome = response;
                QuarkusTransaction.requiringNew().run(() -> {
                    switch (outcome.status()) {
                        case "accepted" -> em.createNativeQuery("""
                                        UPDATE xhandler.ose_submission SET forward_status = 'forwarded', forwarded_at = now(), sunat_ack = :ack,
                                            forward_attempts = forward_attempts + 1, next_attempt_at = NULL, last_error = NULL WHERE id = :id""")
                                .setParameter("ack", ((outcome.code() == null ? "" : outcome.code()) + " " + (outcome.description() == null ? ""
                                        : outcome.description())).strip())
                                .setParameter("id", d.id()).executeUpdate();
                        case "rejected" -> em.createNativeQuery("""
                                        UPDATE xhandler.ose_submission SET forward_status = 'rejected', forward_attempts = forward_attempts + 1,
                                            next_attempt_at = NULL, last_error = :e WHERE id = :id""")
                                .setParameter("e", outcome.code() + " " + outcome.description()).setParameter("id", d.id()).executeUpdate();
                        default -> em.createNativeQuery("""
                                        UPDATE xhandler.ose_submission SET forward_attempts = forward_attempts + 1, last_error = :e,
                                            next_attempt_at = now() + cast(:wait AS interval) WHERE id = :id""")
                                .setParameter("e", String.valueOf(outcome.description())).setParameter("wait", backoff(d.attempts() + 1))
                                .setParameter("id", d.id()).executeUpdate();
                    }
                });
                if ("accepted".equals(outcome.status())) {
                    forwarded++;
                    delay.record(Duration.between(d.issuedAt(), Instant.now()));
                } else if ("rejected".equals(outcome.status())) {
                    // SUNAT refused a document the OSE accepted: an operator must look at it
                    AUDIT.errorf("SUNAT rejected the OSE forward of %s: %s %s", d.name(), outcome.code(), outcome.description());
                }
                if (Duration.between(d.issuedAt(), Instant.now()).compareTo(WARN_AFTER) > 0 && !"accepted".equals(outcome.status())) {
                    AUDIT.errorf("OSE forward of %s is %d minutes old: the 1-hour limit is close", d.name(),
                            Duration.between(d.issuedAt(), Instant.now()).toMinutes());
                }
            }
            return Map.of("forwarded", forwarded, "due", due.size());
        }

        /** 30 s, 1, 2, 4 minutes, then every 5: well inside the hour. */
        static String backoff(int attempts) {
            long seconds = Math.min(300, 30L << Math.min(attempts - 1, 4));
            return seconds + " seconds";
        }
    }

    /**
     * The 1-month retention duty (RS 117-2017 art. 7.12): forwarded (or rejected) submissions and
     * inconsistency notices older than {@code purge_after} are deleted with their files. A file is
     * deleted only when no other submission points at it (identical resends share files).
     */
    @ApplicationScoped
    public static class Purge implements JobHandler {
        private final jakarta.persistence.EntityManager em;
        private final FileStore files;

        public Purge(jakarta.persistence.EntityManager em, FileStore files) {
            this.em = em;
            this.files = files;
        }

        @Override
        public String name() {
            return "ose.purge";
        }

        @Override
        public Map<String, Object> run(JobContext context) {
            List<String> orphaned = QuarkusTransaction.requiringNew().call(() -> {
                @SuppressWarnings("unchecked")
                List<String> refs = em.createNativeQuery("""
                                WITH gone AS (
                                    DELETE FROM xhandler.ose_submission
                                    WHERE purge_after < now() AND (result = 'inconsistency' OR forward_status IN ('forwarded', 'rejected'))
                                    RETURNING xml_ref, cdr_ref)
                                SELECT DISTINCT ref FROM (SELECT xml_ref AS ref FROM gone UNION SELECT cdr_ref FROM gone) refs
                                WHERE ref IS NOT NULL
                                  AND NOT EXISTS (SELECT 1 FROM xhandler.ose_submission s WHERE s.xml_ref = refs.ref OR s.cdr_ref = refs.ref)""")
                        .getResultList();
                return refs;
            });
            int deleted = 0;
            for (String ref : orphaned) {
                try {
                    files.delete(ref);
                    deleted++;
                } catch (RuntimeException e) {
                    LOG.warnf("OSE purge could not delete %s: %s", ref, e.toString());
                }
            }
            return Map.of("files_deleted", deleted);
        }
    }

    /**
     * The SFTP channel (DESIGN.md §11.4 step 1): an SFTP server (deployed beside the app, it
     * authenticates the OSE users) writes ZIPs to {@code {root}/{username}/in/}; this job checks
     * each one like {@code sendBill} and writes {@code R-{name}.zip} (the CDR-OSE) or
     * {@code {name}.error.txt} to {@code out/}, moving the input to {@code done/}. Symbolic links
     * and oversized files are skipped.
     */
    @ApplicationScoped
    public static class SftpIntake implements JobHandler {
        private final OseClients clients;
        private final OseIntake intake;
        private final Optional<Path> root;

        public SftpIntake(OseClients clients, OseIntake intake, @ConfigProperty(name = "xhandler.ose.sftp-root") Optional<Path> root) {
            this.clients = clients;
            this.intake = intake;
            this.root = root;
        }

        @Override
        public String name() {
            return "ose.sftp";
        }

        @Override
        public Map<String, Object> run(JobContext context) {
            if (root.isEmpty() || !Files.isDirectory(root.get(), LinkOption.NOFOLLOW_LINKS)) {
                return Map.of("enabled", false);
            }
            int processed = 0;
            try (DirectoryStream<Path> users = Files.newDirectoryStream(root.get())) {
                for (Path user : users) {
                    if (!Files.isDirectory(user, LinkOption.NOFOLLOW_LINKS)) {
                        continue;
                    }
                    Optional<OseClients.Client> client = clients.byUsername(user.getFileName().toString());
                    Path in = user.resolve("in");
                    if (client.isEmpty() || !Files.isDirectory(in, LinkOption.NOFOLLOW_LINKS)) {
                        continue;
                    }
                    processed += drain(client.get(), user, in);
                }
            } catch (IOException e) {
                LOG.warnf("OSE SFTP intake: %s", e.toString());
            }
            return Map.of("processed", processed);
        }

        private int drain(OseClients.Client client, Path user, Path in) throws IOException {
            Path out = Files.createDirectories(user.resolve("out"));
            Path done = Files.createDirectories(user.resolve("done"));
            int processed = 0;
            try (DirectoryStream<Path> zips = Files.newDirectoryStream(in, "*.{zip,ZIP}")) {
                for (Path zip : zips) {
                    if (!Files.isRegularFile(zip, LinkOption.NOFOLLOW_LINKS) || Files.size(zip) > ZipGuard.MAX_ZIP_BYTES) {
                        continue;
                    }
                    String fileName = zip.getFileName().toString();
                    String base = fileName.replaceFirst("(?i)\\.zip$", "");
                    try {
                        OseIntake.Cdr cdr = intake.sendBill(client, "sftp", fileName, Files.readAllBytes(zip));
                        Files.write(out.resolve("R-" + cdr.name() + ".zip"), cdr.zip());
                    } catch (OseIntake.Fault e) {
                        Files.writeString(out.resolve(base + ".error.txt"), e.code + "|" + e.getMessage() + "\n");
                    } catch (ZipGuard.Refused e) {
                        Files.writeString(out.resolve(base + ".error.txt"), e.code + "|" + e.getMessage() + "\n");
                    }
                    Files.move(zip, done.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                    processed++;
                }
            }
            return processed;
        }
    }
}
