package io.github.projectopenubl.xhandler.ose;

import io.github.projectopenubl.xhandler.api.XmlText;
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
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/** The OSE's jobs (DESIGN.md §11.4 steps 6–7, §10.6). */
public final class OseJobs {

    private static final Logger LOG = Logger.getLogger(OseJobs.class);
    private static final Logger AUDIT = Logger.getLogger("xhandler.audit.platform");

    private OseJobs() {}

    /**
     * Forwards each CDR-OSE and its document to SUNAT within 1 hour of issuing the CDR (RS 117-2017
     * art. 7.5; SUNAT flags later ones with 4196). Runs every 30 seconds at priority 1 and keeps
     * going, 8 sends at a time, until the queue is empty or its 25-second budget is spent. Each batch
     * is leased for 2 minutes ({@code FOR UPDATE SKIP LOCKED}); SUNAT is called with no transaction
     * open; outcomes are compare-and-set on the lease.
     *
     * <ul>
     *   <li>accepted: forwarded, the delay measured from {@code cdr_issued_at};
     *   <li>unknown / not delivered: retried with backoff (SUNAT acknowledges a resend);
     *   <li>rejected (2000–3999): SUNAT refused the document; an operator decides, never purged;
     *   <li>blocked (credentials, package, 1032/1033, unknown codes): not the document's fault;
     *       the row stays queued and the whole run stops, alerting, until a person fixes it.
     * </ul>
     */
    @ApplicationScoped
    public static class Forwarder implements JobHandler {

        static final Duration LEASE = Duration.ofMinutes(2);
        static final Duration BUDGET = Duration.ofSeconds(25);
        static final Duration WARN_AFTER = Duration.ofMinutes(45);
        static final int PARALLEL = 8;

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
                    .publishPercentileHistogram().serviceLevelObjectives(Duration.ofMinutes(30), Duration.ofMinutes(45), Duration.ofHours(1))
                    .register(metrics);
            Gauge.builder("xhandler.ose.forward_oldest_seconds", this, Forwarder::oldestPending)
                    .description("Age of the oldest CDR-OSE not yet forwarded").register(metrics);
        }

        double oldestPending() {
            try {
                List<?> age = QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery("""
                                SELECT extract(epoch FROM now() - min(cdr_issued_at)) FROM xhandler.ose_submission
                                WHERE forward_status IN ('pending', 'blocked')""").getResultList());
                return age.isEmpty() || age.get(0) == null ? 0.0 : ((Number) age.get(0)).doubleValue();
            } catch (RuntimeException e) {
                return Double.NaN;
            }
        }

        @Override
        public String name() {
            return "ose.forward";
        }

        record Due(UUID id, String name, String tipo, String xmlRef, String cdrRef, Instant issuedAt, int attempts, UUID lease) {}

        @Override
        public Map<String, Object> run(JobContext context) {
            Instant deadline = Instant.now().plus(BUDGET);
            // Blocked rows come back every 10 minutes: once the cause is fixed they flow again
            QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                            UPDATE xhandler.ose_submission SET forward_status = 'pending'
                            WHERE forward_status = 'blocked' AND next_attempt_at <= now()""").executeUpdate());
            EngineModels.Environment env = switch (environment) {
                case "production" -> EngineModels.Environment.PRODUCTION;
                case "beta" -> EngineModels.Environment.BETA;
                default -> EngineModels.Environment.FAKE;
            };
            EngineModels.OseCredentials creds = null;
            AtomicInteger forwarded = new AtomicInteger();
            AtomicBoolean blocked = new AtomicBoolean();
            int seen = 0;
            while (Instant.now().isBefore(deadline) && !blocked.get()) {
                List<Due> due = lease();
                if (due.isEmpty()) {
                    break;
                }
                seen += due.size();
                if (creds == null) {
                    creds = QuarkusTransaction.requiringNew().call(() -> credentials.pick("ose_sunat")
                            .map(id -> credentials.read(id, "ose_sunat", "ose-forward"))
                            .map(f -> new EngineModels.OseCredentials(f.get("username"), f.get("password")))
                            .orElse(null));
                    if (creds == null && env != EngineModels.Environment.FAKE) {
                        AUDIT.error("OSE forwarding has no SUNAT credentials (platform credential ose_sunat): CDRs are waiting");
                        release(due);
                        return Map.of("forwarded", 0, "error", "no credentials");
                    }
                    if (creds == null) {
                        creds = new EngineModels.OseCredentials("OSEFAKE", "fake");
                    }
                }
                EngineModels.OseCredentials use = creds;
                try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
                    java.util.concurrent.Semaphore permits = new java.util.concurrent.Semaphore(PARALLEL);
                    List<Future<?>> sends = new ArrayList<>();
                    for (Due d : due) {
                        sends.add(pool.submit(() -> {
                            permits.acquireUninterruptibly();
                            try {
                                if (blocked.get()) {
                                    release(List.of(d));
                                    return;
                                }
                                String status = forward(d, env, use);
                                if ("accepted".equals(status)) {
                                    forwarded.incrementAndGet();
                                } else if ("blocked".equals(status)) {
                                    blocked.set(true);
                                }
                            } catch (RuntimeException e) {
                                // One row's problem (a missing file...) doesn't stop the others
                                LOG.warnf("OSE forward of %s failed: %s", d.name(), e.toString());
                                retry(d, e.toString());
                            } finally {
                                permits.release();
                            }
                        }));
                    }
                    for (Future<?> send : sends) {
                        try {
                            send.get();
                        } catch (Exception e) {
                            LOG.warnf("OSE forward task failed: %s", e.toString());
                        }
                    }
                }
            }
            return Map.of("forwarded", forwarded.get(), "seen", seen, "blocked", blocked.get());
        }

        private List<Due> lease() {
            UUID lease = UUID.randomUUID();
            return QuarkusTransaction.requiringNew().call(() -> {
                @SuppressWarnings("unchecked")
                List<Object[]> rows = em.createNativeQuery("""
                                WITH due AS (
                                    SELECT id FROM xhandler.ose_submission
                                    WHERE forward_status = 'pending' AND next_attempt_at <= now()
                                    ORDER BY cdr_issued_at LIMIT 50 FOR UPDATE SKIP LOCKED)
                                UPDATE xhandler.ose_submission s SET next_attempt_at = now() + interval '2 minutes', forward_lease = :lease
                                FROM due WHERE s.id = due.id
                                RETURNING s.id, s.name, s.tipo, s.xml_ref, s.cdr_ref, s.cdr_issued_at, s.forward_attempts""")
                        .setParameter("lease", lease).getResultList();
                return rows.stream().map(r -> new Due((UUID) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4],
                        io.github.projectopenubl.xhandler.status.StatusResource.toInstant(r[5]), ((Number) r[6]).intValue(), lease)).toList();
            });
        }

        /** Gives a leased row back at once (the run stopped before sending it). */
        private void release(List<Due> due) {
            for (Due d : due) {
                QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                                UPDATE xhandler.ose_submission SET next_attempt_at = now(), forward_lease = NULL
                                WHERE id = :id AND forward_status = 'pending' AND forward_lease = :lease""")
                        .setParameter("id", d.id()).setParameter("lease", d.lease()).executeUpdate());
            }
        }

        private void retry(Due d, String error) {
            QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                            UPDATE xhandler.ose_submission SET forward_attempts = forward_attempts + 1, last_error = :e,
                                next_attempt_at = now() + cast(:wait AS interval)
                            WHERE id = :id AND forward_status = 'pending' AND forward_lease = :lease""")
                    .setParameter("e", truncate(error)).setParameter("wait", backoff(d.attempts() + 1)).setParameter("id", d.id())
                    .setParameter("lease", d.lease()).executeUpdate());
        }

        private String forward(Due d, EngineModels.Environment env, EngineModels.OseCredentials creds) {
            // The stored bytes, in their declared encoding: the engine zips exactly what the issuer signed
            String xml = XmlText.decode(files.get(d.xmlRef()));
            String cdr = XmlText.decode(files.get(d.cdrRef()));
            String operation = List.of("RC", "RA", "RR").contains(d.tipo()) ? "send_summary" : "send_bill";
            EngineModels.OseForwardResponse response;
            try {
                response = engine.oseForward(new EngineModels.OseForwardRequest(env, url.orElse(null), creds, d.name(), xml, cdr, operation));
            } catch (EngineException e) {
                response = new EngineModels.OseForwardResponse("unknown", null, e.getMessage());
            }
            EngineModels.OseForwardResponse outcome = response;
            String detail = ((outcome.code() == null ? "" : outcome.code()) + " " + (outcome.description() == null ? "" : outcome.description())).strip();
            int changed = QuarkusTransaction.requiringNew().call(() -> switch (outcome.status()) {
                case "accepted" -> em.createNativeQuery("""
                                UPDATE xhandler.ose_submission SET forward_status = 'forwarded', forwarded_at = now(), sunat_ack = :ack,
                                    forward_attempts = forward_attempts + 1, next_attempt_at = NULL, last_error = NULL
                                WHERE id = :id AND forward_status = 'pending' AND forward_lease = :lease""")
                        .setParameter("ack", truncate(detail)).setParameter("id", d.id()).setParameter("lease", d.lease()).executeUpdate();
                case "rejected" -> em.createNativeQuery("""
                                UPDATE xhandler.ose_submission SET forward_status = 'rejected', forward_attempts = forward_attempts + 1,
                                    next_attempt_at = NULL, last_error = :e
                                WHERE id = :id AND forward_status = 'pending' AND forward_lease = :lease""")
                        .setParameter("e", truncate(detail)).setParameter("id", d.id()).setParameter("lease", d.lease()).executeUpdate();
                case "blocked" -> em.createNativeQuery("""
                                UPDATE xhandler.ose_submission SET forward_status = 'blocked', forward_attempts = forward_attempts + 1,
                                    next_attempt_at = now() + interval '10 minutes', last_error = :e
                                WHERE id = :id AND forward_status = 'pending' AND forward_lease = :lease""")
                        .setParameter("e", truncate(detail)).setParameter("id", d.id()).setParameter("lease", d.lease()).executeUpdate();
                default -> em.createNativeQuery("""
                                UPDATE xhandler.ose_submission SET forward_attempts = forward_attempts + 1, last_error = :e,
                                    next_attempt_at = now() + cast(:wait AS interval)
                                WHERE id = :id AND forward_status = 'pending' AND forward_lease = :lease""")
                        .setParameter("e", truncate(detail)).setParameter("wait", backoff(d.attempts() + 1)).setParameter("id", d.id())
                        .setParameter("lease", d.lease()).executeUpdate();
            });
            if (changed == 0) {
                // Another run took the row over (its lease expired): its outcome stands
                return "superseded";
            }
            switch (outcome.status()) {
                case "accepted" -> delay.record(Duration.between(d.issuedAt(), Instant.now()));
                case "rejected" -> AUDIT.errorf("SUNAT rejected the OSE forward of %s: %s", d.name(), detail);
                case "blocked" -> AUDIT.errorf("OSE forwarding blocked by SUNAT (%s) at %s: fix it; documents stay queued", detail, d.name());
                default -> {
                    if (Duration.between(d.issuedAt(), Instant.now()).compareTo(WARN_AFTER) > 0) {
                        AUDIT.errorf("OSE forward of %s is %d minutes old: the 1-hour limit is close", d.name(),
                                Duration.between(d.issuedAt(), Instant.now()).toMinutes());
                    }
                }
            }
            return outcome.status();
        }

        /** 30 s, 1, 2, 4 minutes, then every 5: well inside the hour. */
        static String backoff(int attempts) {
            long seconds = Math.min(300, 30L << Math.min(attempts - 1, 4));
            return seconds + " seconds";
        }

        private static String truncate(String text) {
            return text == null ? null : text.length() > 500 ? text.substring(0, 500) : text;
        }
    }

    /**
     * The 1-month retention duty (RS 117-2017 art. 7.12): forwarded submissions and inconsistency
     * notices older than {@code purge_after} are deleted with their files. Rejected forwards are
     * never purged here (an operator decides). A file is deleted only when no remaining submission
     * points at it (identical resends share files).
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
                List<Object[]> gone = em.createNativeQuery("""
                                DELETE FROM xhandler.ose_submission
                                WHERE purge_after < now() AND (result = 'inconsistency' OR forward_status = 'forwarded')
                                RETURNING xml_ref, cdr_ref""").getResultList();
                List<String> refs = new ArrayList<>();
                for (Object[] r : gone) {
                    for (Object ref : r) {
                        if (ref != null && !refs.contains(ref)) {
                            refs.add((String) ref);
                        }
                    }
                }
                if (refs.isEmpty()) {
                    return List.<String>of();
                }
                // A second statement: it sees the deletion above (a CTE would not)
                @SuppressWarnings("unchecked")
                List<String> still = em.createNativeQuery("""
                                SELECT ref FROM unnest(cast(:refs AS text[])) ref
                                WHERE EXISTS (SELECT 1 FROM xhandler.ose_submission s WHERE s.xml_ref = ref OR s.cdr_ref = ref)""")
                        .setParameter("refs", refs.toArray(String[]::new)).getResultList();
                refs.removeAll(still);
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
     * authenticates the OSE users; symbolic links disabled) writes ZIPs to
     * {@code {root}/{username}/in/}; this job checks each like {@code sendBill} and writes
     * {@code R-{name}.zip} (the CDR-OSE) or {@code {name}.error.txt} to {@code out/}, moving the
     * input to {@code done/}. Every path must resolve inside the root without symbolic links;
     * files are read through no-follow handles, answers written to a temporary file and moved into
     * place. Files younger than 10 seconds may still be uploading and wait for the next run; an
     * engine failure leaves the file for the next run.
     */
    @ApplicationScoped
    public static class SftpIntake implements JobHandler {
        static final Duration SETTLE = Duration.ofSeconds(10);

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
            try {
                Path base = root.get().toRealPath();
                try (DirectoryStream<Path> users = Files.newDirectoryStream(base)) {
                    for (Path user : users) {
                        if (!realDirectory(base, user)) {
                            continue;
                        }
                        Optional<OseClients.Client> client = clients.byUsername(user.getFileName().toString());
                        if (client.isEmpty()) {
                            continue;
                        }
                        try {
                            processed += drain(base, client.get(), user);
                        } catch (IOException | RuntimeException e) {
                            LOG.warnf("OSE SFTP intake of %s: %s", user.getFileName(), e.toString());
                        }
                    }
                }
            } catch (IOException e) {
                LOG.warnf("OSE SFTP intake: %s", e.toString());
            }
            return Map.of("processed", processed);
        }

        /** A directory under the root, reached without symbolic links. */
        static boolean realDirectory(Path base, Path path) throws IOException {
            return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)
                    && path.toRealPath().startsWith(base) && path.toRealPath().equals(path.toAbsolutePath().normalize());
        }

        private Path directory(Path base, Path user, String name) throws IOException {
            Path dir = user.resolve(name);
            if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectory(dir);
            }
            if (!realDirectory(base, dir)) {
                throw new IOException(dir + " is not a plain directory under the root");
            }
            return dir;
        }

        private int drain(Path base, OseClients.Client client, Path user) throws IOException {
            Path in = directory(base, user, "in");
            Path out = directory(base, user, "out");
            Path done = directory(base, user, "done");
            int processed = 0;
            try (DirectoryStream<Path> zips = Files.newDirectoryStream(in, "*.{zip,ZIP}")) {
                for (Path zip : zips) {
                    BasicFileAttributes attributes = Files.readAttributes(zip, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (!attributes.isRegularFile() || attributes.size() > ZipGuard.MAX_ZIP_BYTES
                            || attributes.lastModifiedTime().toInstant().isAfter(Instant.now().minus(SETTLE))) {
                        continue;
                    }
                    String fileName = zip.getFileName().toString();
                    String base64Safe = fileName.replaceFirst("(?i)\\.zip$", "").replaceAll("[^A-Za-z0-9._-]", "_");
                    byte[] bytes;
                    // No-follow read: a symlink swapped in after the check fails here
                    try (FileChannel channel = FileChannel.open(zip, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                        bytes = new byte[(int) Math.min(channel.size(), ZipGuard.MAX_ZIP_BYTES + 1L)];
                        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
                        while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
                            // read fully
                        }
                    }
                    String answerName;
                    byte[] answer;
                    try {
                        OseIntake.Cdr cdr = intake.sendBill(client, "sftp", fileName, bytes);
                        answerName = "R-" + cdr.name() + ".zip";
                        answer = cdr.zip();
                    } catch (OseIntake.Fault e) {
                        if ("0109".equals(e.code)) {
                            // The service failed, not the document: try again next run
                            continue;
                        }
                        answerName = base64Safe + ".error.txt";
                        answer = (e.code + "|" + e.getMessage() + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    } catch (ZipGuard.Refused e) {
                        answerName = base64Safe + ".error.txt";
                        answer = (e.code + "|" + e.getMessage() + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    }
                    Path temp = Files.createTempFile(out, ".answer-", ".tmp");
                    Files.write(temp, answer, StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS);
                    Files.move(temp, out.resolve(answerName), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    Files.move(zip, done.resolve(base64Safe + ".zip"), StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
                    processed++;
                }
            }
            return processed;
        }
    }
}
