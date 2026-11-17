package io.github.projectopenubl.xhandler.documents;

import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.engine.Engine;
import io.github.projectopenubl.xhandler.engine.EngineException;
import io.github.projectopenubl.xhandler.engine.EngineModels;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.storage.FileStore;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * One SUNAT attempt for a document (DESIGN.md §9.2, §10.4, §11.2), created only by the reconciler.
 * The job holds {@code document:{id}}, so attempts on a document run one at a time.
 *
 * <ol>
 *   <li>Transaction 1: re-read the document; do nothing once it is definitive; increment {@code
 *       attempts} (to n), record {@code send_started_at}; commit.
 *   <li>Call SUNAT through the engine with no transaction open.
 *   <li>Transaction 2: write the outcome only if {@code attempts} is still n. A stale worker that
 *       wakes up after a newer attempt started writes nothing.
 * </ol>
 */
@ApplicationScoped
public class AttemptHandler implements JobHandler {

    private static final Logger LOG = Logger.getLogger(AttemptHandler.class);

    enum Action {
        SEND,
        VERIFY_TICKET,
        RESOLVE
    }

    /** What transaction 1 decided, as plain data (entities never leave their transaction). */
    record Plan(
            UUID documentId,
            UUID tenantId,
            int attempt,
            Action action,
            Routes.Target target,
            String xmlKey,
            String ticket,
            EngineModels.DocumentKey key,
            boolean facturaFamily,
            boolean summary,
            Instant sendStartedAt,
            String endpoint) {}

    private final TenantAccess access;
    private final Routes routes;
    private final Engine engine;
    private final FileStore files;
    private final EntityManager em;
    private final Event<DocumentStatusChanged> statusChanged;
    private final MeterRegistry metrics;
    private final Duration resolveSafety;
    private final Duration lease;

    public AttemptHandler(
            TenantAccess access,
            Routes routes,
            Engine engine,
            FileStore files,
            EntityManager em,
            Event<DocumentStatusChanged> statusChanged,
            MeterRegistry metrics,
            // SUNAT read timeout (120 s) + margin; nests inside the app → engine timeout (§10.8)
            @ConfigProperty(name = "xhandler.documents.resolve-safety", defaultValue = "160s") Duration resolveSafety,
            io.github.projectopenubl.xhandler.jobs.JobConfig jobs) {
        this.access = access;
        this.routes = routes;
        this.engine = engine;
        this.files = files;
        this.em = em;
        this.statusChanged = statusChanged;
        this.metrics = metrics;
        this.resolveSafety = resolveSafety;
        this.lease = jobs.lease();
    }

    @Override
    public String name() {
        return "document.attempt";
    }

    @Override
    public Map<String, Object> run(JobContext context) {
        UUID documentId = context.uuidArg("document_id");
        Object begun = QuarkusTransaction.requiringNew().call(() -> begin(context, documentId));
        if (begun instanceof String skipped) {
            return Map.of("skipped", skipped);
        }
        Plan plan = (Plan) begun;
        long started = System.nanoTime();
        Instant callStartedAt = Instant.now();
        Outcome outcome = execute(plan);
        metrics.timer("xhandler.sunat.attempt", "endpoint", plan.endpoint(), "outcome", outcome.kind().name().toLowerCase())
                .record(Duration.ofNanos(System.nanoTime() - started));
        try {
            // Store the CDR before transaction 2 (content-addressed: an orphan is harmless)
            FileStore.Stored cdr = null;
            if (outcome.cdrZip() != null) {
                cdr = files.put(plan.tenantId(), Base64.getDecoder().decode(outcome.cdrZip()));
            }
            FileStore.Stored storedCdr = cdr;
            boolean written = QuarkusTransaction.requiringNew().call(() -> finish(context, plan, outcome, storedCdr, callStartedAt));
            return Map.of("action", plan.action().name().toLowerCase(), "outcome", outcome.kind().name().toLowerCase(),
                    "written", written);
        } catch (RuntimeException e) {
            // SUNAT may have answered, but we could not record it: ask before anything else (§9.2.1)
            if (plan.action() != Action.VERIFY_TICKET) {
                markUnknown(context, plan);
            }
            throw e;
        }
    }

    /** After a failure past the send: the document goes to unknown (fenced), so the next attempt resolves first. */
    private void markUnknown(JobContext context, Plan plan) {
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                access.enterAsPlatform(plan.tenantId());
                Document doc = Document.findById(plan.documentId(), LockModeType.PESSIMISTIC_WRITE);
                if (doc == null || doc.attempts != plan.attempt() || !Document.RETRYABLE.contains(doc.status)) {
                    return;
                }
                String previous = doc.status;
                doc.status = "unknown";
                doc.attemptJobId = null;
                doc.nextAttemptAt = plan.sendStartedAt().plus(resolveSafety);
                doc.updatedAt = Instant.now();
                DocumentEvent.record(doc, null, "unknown: the outcome could not be recorded", "platform:worker", context.correlationId());
                if (!previous.equals(doc.status)) {
                    statusChanged.fire(new DocumentStatusChanged(doc, previous, context.correlationId()));
                }
            });
        } catch (RuntimeException e) {
            // Recovery (lease expiry → resolve) still covers it
            LOG.errorf(e, "could not move document %s to unknown", plan.documentId());
        }
    }

    private Object begin(JobContext context, UUID documentId) {
        TenantScope scope = access.enterAsPlatform(context.tenantId());
        Document doc = Document.findById(documentId, LockModeType.PESSIMISTIC_WRITE);
        if (doc == null) {
            return "missing";
        }
        if (!Document.RETRYABLE.contains(doc.status)) {
            return "definitive";
        }
        if (doc.summaryDocumentId != null) {
            // It follows its summary's outcome: no retry state of its own
            doc.nextAttemptAt = null;
            return "in_summary";
        }
        if ("operator".equals(doc.holdReason)) {
            return "held";
        }
        Instant now = Instant.now();
        Action action;
        if ("in_progress".equals(doc.status) && doc.ticket != null) {
            action = Action.VERIFY_TICKET;
        } else if ("unknown".equals(doc.status)) {
            // A stale call may still be in flight until its timeout passes (§9.2.1)
            Instant notBefore = doc.sendStartedAt == null ? now : doc.sendStartedAt.plus(resolveSafety);
            if (now.isBefore(notBefore)) {
                doc.nextAttemptAt = notBefore;
                return "waiting_for_stale_call";
            }
            action = Action.RESOLVE;
        } else {
            action = Action.SEND;
        }
        DocumentFile xml = DocumentFile.of(doc.id, "xml_signed").orElse(null);
        if (xml == null) {
            // Can't happen through emit; a person must look instead of re-leasing it forever
            doc.nextAttemptAt = null;
            doc.holdReason = "needs_attention";
            DocumentEvent.record(doc, null, "no signed XML stored: needs attention", "platform:worker", context.correlationId());
            return "no_signed_xml";
        }
        Company company = Company.findById(doc.companyId);
        Routes.Target target;
        try {
            target = routes.target(scope, company, doc);
        } catch (Routes.MissingCredentials e) {
            // The user must add credentials; the document waits in exception, resendable later
            applyAndRecord(doc, OutcomePolicy.decide(state(doc), Outcome.of(Outcome.Kind.PERMANENT, null, e.getMessage()),
                    now, 1.0, null), Outcome.of(Outcome.Kind.PERMANENT, null, e.getMessage()), context.correlationId());
            return "missing_credentials";
        }
        doc.attempts++;
        doc.attemptJobId = context.id();
        if (action != Action.VERIFY_TICKET) {
            doc.sendStartedAt = now;
        }
        // Renew the lease: the attempt may have waited in the queue (§24.2)
        doc.nextAttemptAt = now.plus(lease);
        doc.updatedAt = now;
        return new Plan(doc.id, doc.tenantId, doc.attempts, action, target, xml.storageKey, doc.ticket,
                new EngineModels.DocumentKey(company.ruc, doc.tipoComprobante, doc.serie, doc.numero), doc.isFacturaFamily(),
                doc.isSummary(), doc.sendStartedAt, doc.endpoint);
    }

    private Outcome execute(Plan plan) {
        String xml = new String(files.get(plan.xmlKey()), StandardCharsets.UTF_8);
        Routes.Target t = plan.target();
        try {
            return switch (plan.action()) {
                case SEND -> afterSend(plan, xml, engine.send(xml, t.environment(), t.credentials(), t.urls()));
                case VERIFY_TICKET -> Outcome.ofSend(engine.verifyTicket(xml, plan.ticket(), t.environment(), t.credentials(), t.urls()));
                case RESOLVE -> resolve(plan, xml);
            };
        } catch (EngineException e) {
            if (e.status() == 422) {
                return Outcome.of(Outcome.Kind.LOCAL_ERROR, null, e.getMessage());
            }
            LOG.warnf("engine failed during %s of document %s: %s", plan.action(), plan.documentId(), e.getMessage());
            return Outcome.of(Outcome.Kind.ENGINE_UNAVAILABLE, null, "Servicio interno no disponible: " + e.kind());
        }
    }

    /**
     * Resolves an ambiguous send per document type (§9.2.2). Production facturas ask SUNAT's consult
     * service; summaries with a ticket check it; everything else resends the same bytes (a 1033
     * then goes through the digest check).
     */
    private Outcome resolve(Plan plan, String xml) {
        Routes.Target t = plan.target();
        if (plan.summary() && plan.ticket() != null) {
            return Outcome.ofSend(engine.verifyTicket(xml, plan.ticket(), t.environment(), t.credentials(), t.urls()));
        }
        if (plan.facturaFamily() && t.environment().hasConsultServices()) {
            EngineModels.StatusCdrResponse status = engine.statusCdr(t.environment(), t.credentials(), plan.key());
            return switch (status.status()) {
                case "accepted" -> new Outcome(Outcome.Kind.ACCEPTED, 0, null, status.description(), List.of(), null, status.cdrZip());
                case "rejected" -> Outcome.of(Outcome.Kind.REJECTED, null, status.description());
                case "voided" -> Outcome.of(Outcome.Kind.VOIDED, null, status.description());
                // SUNAT doesn't have it: the same bytes go again
                case "not_found" -> afterSend(plan, xml, engine.send(xml, t.environment(), t.credentials(), t.urls()));
                case "not_delivered" -> Outcome.of(Outcome.Kind.NOT_DELIVERED, null, status.description());
                default -> Outcome.of(Outcome.Kind.AMBIGUOUS, null, status.description());
            };
        }
        return afterSend(plan, xml, engine.send(xml, t.environment(), t.credentials(), t.urls()));
    }

    private Outcome afterSend(Plan plan, String xml, EngineModels.SendResponse response) {
        Outcome outcome = Outcome.ofSend(response);
        try {
            return switch (outcome.kind()) {
                case ALREADY_REGISTERED -> digestCheck(plan, xml, outcome);
                case ALREADY_FINAL -> alreadyFinal(plan, xml, outcome);
                default -> outcome;
            };
        } catch (EngineException e) {
            // The send happened: a failing follow-up can't make the document a local error
            LOG.warnf("follow-up of %s for document %s failed: %s", outcome.kind(), plan.documentId(), e.getMessage());
            return Outcome.of(Outcome.Kind.AMBIGUOUS, outcome.code(), outcome.description());
        }
    }

    /**
     * 1033 "registrado previamente con otros datos": does SUNAT hold exactly our bytes? (§9.2.1)
     * The validity service compares the file. Beta has no such service: a first attempt that gets
     * 1033 collides (someone used the number), a later one is taken as our earlier send.
     */
    private Outcome digestCheck(Plan plan, String xml, Outcome sunat) {
        Routes.Target t = plan.target();
        if (!t.environment().hasConsultServices()) {
            return plan.attempt() > 1
                    ? new Outcome(Outcome.Kind.ACCEPTED, 0, null, "Registrado previamente (SUNAT beta, sin verificación)", List.of(),
                            null, null)
                    : collision(sunat);
        }
        if (plan.facturaFamily()) {
            // Facturas: SUNAT's record comes with its CDR, which we keep (§9.2.2)
            EngineModels.StatusCdrResponse status = engine.statusCdr(t.environment(), t.credentials(), plan.key());
            if ("accepted".equals(status.status()) && status.cdrZip() != null) {
                EngineModels.ValidityResponse same = engine.validityFile(xml, t.environment(), t.credentials());
                if ("matches".equals(same.state())) {
                    return new Outcome(Outcome.Kind.ACCEPTED, 0, null, status.description(), List.of(), null, status.cdrZip());
                }
                if ("differs".equals(same.state())) {
                    return collision(sunat);
                }
            }
        }
        EngineModels.ValidityResponse validity = engine.validityFile(xml, t.environment(), t.credentials());
        return switch (validity.state()) {
            case "matches" -> new Outcome(Outcome.Kind.ACCEPTED, 0, null, "Registrado previamente con los mismos datos", List.of(),
                    null, null);
            case "differs" -> collision(sunat);
            case "not_delivered" -> Outcome.of(Outcome.Kind.NOT_DELIVERED, sunat.code(), validity.description());
            default -> Outcome.of(Outcome.Kind.AMBIGUOUS, sunat.code(), validity.description());
        };
    }

    /** 1032: already reported, voided or rejected. Production facturas can ask; otherwise it ends here. */
    private Outcome alreadyFinal(Plan plan, String xml, Outcome sunat) {
        Routes.Target t = plan.target();
        if (plan.facturaFamily() && t.environment().hasConsultServices()) {
            EngineModels.StatusCdrResponse status = engine.statusCdr(t.environment(), t.credentials(), plan.key());
            return switch (status.status()) {
                case "accepted" -> new Outcome(Outcome.Kind.ACCEPTED, 0, null, status.description(), List.of(), null, status.cdrZip());
                case "rejected" -> Outcome.of(Outcome.Kind.REJECTED, sunat.code(), status.description());
                case "voided" -> Outcome.of(Outcome.Kind.VOIDED, sunat.code(), status.description());
                default -> sunat;
            };
        }
        if (t.environment().hasConsultServices()) {
            EngineModels.ValidityResponse validity = engine.validityFile(xml, t.environment(), t.credentials());
            if ("matches".equals(validity.state())) {
                return new Outcome(Outcome.Kind.ACCEPTED, 0, null, sunat.description(), List.of(), null, null);
            }
        }
        return new Outcome(Outcome.Kind.PERMANENT, sunat.code(), sunat.codeClass(), sunat.description(), sunat.notes(), null, null);
    }

    private static Outcome collision(Outcome sunat) {
        return new Outcome(Outcome.Kind.COLLISION, sunat.code(), sunat.codeClass(),
                "Colisión de numeración: SUNAT ya tiene este número con otros datos. " + sunat.description(), sunat.notes(), null, null);
    }

    private boolean finish(JobContext context, Plan plan, Outcome outcome, FileStore.Stored cdr, Instant callStartedAt) {
        access.enterAsPlatform(plan.tenantId());
        Document doc = Document.findById(plan.documentId(), LockModeType.PESSIMISTIC_WRITE);
        if (doc == null || doc.attempts != plan.attempt()) {
            LOG.infof("stale attempt %d of document %s: a newer attempt started, nothing written", plan.attempt(), plan.documentId());
            return false;
        }
        if (outcome.kind() != Outcome.Kind.LOCAL_ERROR && outcome.kind() != Outcome.Kind.ENGINE_UNAVAILABLE) {
            em.createNativeQuery("INSERT INTO xhandler.sunat_call (endpoint, healthy, started_at) VALUES (:endpoint, :healthy, :started)")
                    .setParameter("endpoint", plan.endpoint())
                    .setParameter("healthy", outcome.healthy())
                    .setParameter("started", callStartedAt)
                    .executeUpdate();
        }
        Instant now = Instant.now();
        if (cdr != null && DocumentFile.of(doc.id, "cdr_zip").isEmpty()) {
            DocumentFile.record(doc, "cdr_zip", cdr);
            doc.cdrReceivedAt = now;
        }
        if (plan.action() != Action.VERIFY_TICKET && doc.firstSentAt == null && outcome.kind() != Outcome.Kind.NOT_DELIVERED
                && outcome.kind() != Outcome.Kind.LOCAL_ERROR) {
            doc.firstSentAt = plan.sendStartedAt();
        }
        if (outcome.kind() == Outcome.Kind.TICKET) {
            doc.ticket = outcome.ticket();
        } else if (outcome.kind() == Outcome.Kind.TICKET_LOST) {
            // Never check a lost ticket again: resolution takes the lost-ticket path (§9.2.2)
            doc.ticket = null;
        }
        // The outcome is written: recovery must not turn it into unknown
        doc.attemptJobId = null;
        double jitter = 0.8 + ThreadLocalRandom.current().nextDouble() * 0.4;
        Instant resolveNotBefore = plan.sendStartedAt() == null ? null : plan.sendStartedAt().plus(resolveSafety);
        applyAndRecord(doc, OutcomePolicy.decide(state(doc), outcome, now, jitter, resolveNotBefore), outcome, context.correlationId());
        return true;
    }

    private void applyAndRecord(Document doc, OutcomePolicy.Decision decision, Outcome outcome, String correlationId) {
        String previous = doc.status;
        Instant now = Instant.now();
        doc.status = decision.status();
        doc.nextAttemptAt = decision.nextAttemptAt();
        if (!"operator".equals(doc.holdReason)) {
            doc.holdReason = decision.holdReason();
        }
        doc.lastOutcome = decision.outcomeKey();
        doc.sameOutcomeCount = decision.sameOutcomeCount();
        if (decision.progress()) {
            doc.progressAt = now;
        }
        if (outcome.code() != null || outcome.description() != null) {
            doc.sunatCode = outcome.code();
            doc.sunatDescription = outcome.description();
            doc.sunatNotes = outcome.notes();
            doc.codeClass = outcome.kind() == Outcome.Kind.ROUTE_CHANGE ? "route_change" : outcome.codeClass();
        }
        doc.updatedAt = now;
        DocumentEvent.record(doc, outcome.code(), describe(outcome), "platform:worker", correlationId);
        if (!doc.status.equals(previous)) {
            statusChanged.fire(new DocumentStatusChanged(doc, previous, correlationId));
        }
    }

    private static String describe(Outcome outcome) {
        String kind = outcome.kind().name().toLowerCase();
        return outcome.description() == null ? kind : kind + ": " + outcome.description();
    }

    private static OutcomePolicy.State state(Document doc) {
        return new OutcomePolicy.State(doc.status, doc.lastOutcome, doc.sameOutcomeCount, doc.progressAt, doc.holdReason);
    }
}
