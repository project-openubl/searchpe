package io.github.projectopenubl.xhandler.documents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.audit.Auditor;
import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.engine.Engine;
import io.github.projectopenubl.xhandler.engine.EngineModels;
import io.github.projectopenubl.xhandler.keys.SignatureLog;
import io.github.projectopenubl.xhandler.keys.SigningService;
import io.github.projectopenubl.xhandler.storage.FileStore;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Emit (DESIGN.md §11.1): build, number, validate and sign inside one short transaction, reply
 * {@code 201 pending_send}, and let the reconciler send. Intake never depends on SUNAT (§24.1).
 *
 * <p>The transaction is the documented exception to "no remote call while holding row locks"
 * (§10.8 rule 3): the series row stays locked across the engine, the HSM and storage, so a series
 * emits one document at a time. Emits take a permit from a bounded semaphore first.
 */
@ApplicationScoped
public class EmitService {

    /** What a client sends: an xbuilder document plus options. */
    public record EmitRequest(JsonNode document, String serie, Boolean delivered, Map<String, String> labels) {}

    public record Emitted(int status, JsonNode body) {}

    private final TenantAccess access;
    private final Numbering numbering;
    private final Engine engine;
    private final SigningService signing;
    private final SignatureLog signatureLog;
    private final FileStore files;
    private final Routes routes;
    private final AccessLinks links;
    private final Idempotency idempotency;
    private final Auditor auditor;
    private final EntityManager em;
    private final ObjectMapper json;
    private final Instance<EmitQuota> quota;
    private final io.github.projectopenubl.xhandler.rates.ExchangeRates rates;
    private final Semaphore permits;
    private final long permitTimeoutMillis;
    private final String lockTimeout;

    public EmitService(
            TenantAccess access,
            Numbering numbering,
            Engine engine,
            SigningService signing,
            SignatureLog signatureLog,
            FileStore files,
            Routes routes,
            AccessLinks links,
            Idempotency idempotency,
            Auditor auditor,
            EntityManager em,
            ObjectMapper json,
            Instance<EmitQuota> quota,
            io.github.projectopenubl.xhandler.rates.ExchangeRates rates,
            @ConfigProperty(name = "xhandler.emit.permits", defaultValue = "32") int permits,
            @ConfigProperty(name = "xhandler.emit.permit-timeout", defaultValue = "5s") Duration permitTimeout,
            @ConfigProperty(name = "xhandler.emit.lock-timeout", defaultValue = "10s") Duration lockTimeout) {
        this.access = access;
        this.numbering = numbering;
        this.engine = engine;
        this.signing = signing;
        this.signatureLog = signatureLog;
        this.files = files;
        this.routes = routes;
        this.links = links;
        this.idempotency = idempotency;
        this.auditor = auditor;
        this.em = em;
        this.json = json;
        this.quota = quota;
        this.rates = rates;
        this.permits = new Semaphore(permits);
        this.permitTimeoutMillis = permitTimeout.toMillis();
        this.lockTimeout = lockTimeout.toMillis() + "ms";
    }

    public Emitted emit(UUID tenantId, UUID companyId, String idempotencyKey, EmitRequest request, String correlationId) {
        return emit(tenantId, companyId, idempotencyKey, request, correlationId, false, null, "api");
    }

    /** Emits as the platform (its own invoices, §14), not as a tenant member. */
    public Emitted emitAsPlatform(UUID tenantId, UUID companyId, String idempotencyKey, EmitRequest request, String correlationId) {
        return emit(tenantId, companyId, idempotencyKey, request, correlationId, true, null, "api");
    }

    /**
     * An adapter's document with the client's own number (the serie comes from the request, §5). The
     * caller already authenticated: {@code platform} opens the tenant without a member (token-based adapters).
     */
    public Emitted emitNumbered(UUID tenantId, UUID companyId, String idempotencyKey, EmitRequest request, long numero, String correlationId,
            boolean platform) {
        return emit(tenantId, companyId, idempotencyKey, request, correlationId, platform, numero, "adapter");
    }

    private Emitted emit(UUID tenantId, UUID companyId, String idempotencyKey, EmitRequest request, String correlationId,
            boolean platform, Long numeroDado, String origin) {
        if (request == null || request.document() == null || !request.document().hasNonNull("kind")
                || !request.document().path("spec").isObject()) {
            throw ApiException.invalid("invalid_request", "Envíe el documento como {\"document\": {\"kind\": ..., \"spec\": {...}}}.", null);
        }
        // Authorize before touching the idempotency table
        QuarkusTransaction.requiringNew().run(() -> {
            if (platform) {
                access.enterAsPlatform(tenantId);
            } else {
                access.enter(tenantId, Role.OPERATOR);
            }
        });
        UUID claim = null;
        if (idempotencyKey != null) {
            Idempotency.Begun begun = idempotency.begin(tenantId, idempotencyKey, hash(companyId, request));
            if (begun.replay() != null) {
                return new Emitted(begun.replay().status(), begun.replay().body());
            }
            claim = begun.claim();
        }
        UUID owned = claim;
        try {
            UUID documentId = Ids.newId();
            JsonNode body = admitted(tenantId, companyId, platform, !platform || "adapter".equals(origin), () -> QuarkusTransaction.requiringNew()
                    .call(() -> emitInTransaction(tenantId, companyId, documentId, idempotencyKey, owned, request, correlationId, platform,
                            numeroDado, origin)));
            return new Emitted(201, body);
        } catch (RuntimeException e) {
            abandon(tenantId, idempotencyKey, owned);
            throw e;
        }
    }

    /**
     * The gates every new document passes, whatever its input (API, adapters, UBL upload, §5): the
     * quota (a replay never gets here; the platform's own invoices don't count, §14), then an emit
     * permit. Waits on a series row beyond the lock timeout are answered as busy.
     */
    public <T> T admitted(UUID tenantId, UUID companyId, boolean platform, boolean countQuota, java.util.function.Supplier<T> work) {
        if (countQuota) {
            QuarkusTransaction.requiringNew().run(() -> {
                if (platform) {
                    access.enterAsPlatform(tenantId);
                } else {
                    access.enter(tenantId, Role.OPERATOR);
                }
                Company company = Company.findById(companyId);
                if (company == null || company.deletedAt != null) {
                    throw ApiException.notFound();
                }
                for (EmitQuota q : quota) {
                    q.checkEmit(tenantId, company.sunatEnv);
                }
            });
        }
        boolean acquired = false;
        try {
            acquired = permits.tryAcquire(permitTimeoutMillis, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new ApiException(503, "busy", "El servicio está ocupado. Intente nuevamente en unos segundos.");
            }
            return work.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(503, "busy", "El servicio está ocupado. Intente nuevamente en unos segundos.");
        } catch (RuntimeException e) {
            if (lockTimeout(e)) {
                throw new ApiException(503, "busy", "La serie está ocupada por otras emisiones. Intente nuevamente en unos segundos.");
            }
            throw e;
        } finally {
            if (acquired) {
                permits.release();
            }
        }
    }

    /** Inside an emit transaction, before locking the company: waits on the series row are bounded. */
    public void boundLockWaits() {
        em.createNativeQuery("SELECT set_config('lock_timeout', :timeout, true)").setParameter("timeout", lockTimeout).getSingleResult();
    }

    /** The company may emit this document type now: active, and authorized when it emits as PSE (§3.2). */
    public void checkCompany(Company company, String tipo) {
        io.github.projectopenubl.xhandler.tenancy.Tenant tenant = io.github.projectopenubl.xhandler.tenancy.Tenant.findById(company.tenantId);
        if (tenant != null && "exited".equals(tenant.status)) {
            // Readable until its data is deleted, but it left: no new documents (§13)
            throw ApiException.conflict("tenant_exited", "La organización dejó la plataforma: no puede emitir comprobantes.");
        }
        if (!company.active) {
            throw ApiException.conflict("company_inactive", "La empresa está desactivada.");
        }
        if ("pse".equals(company.emissionMode) && "production".equals(company.sunatEnv) && !"09".equals(tipo) && !"31".equals(tipo)
                && !pseAuthorized(company)) {
            throw ApiException.conflict("pse_not_authorized",
                    "La empresa no tiene registrada su autorización a la plataforma como PSE en SUNAT Operaciones en Línea.");
        }
    }

    private void abandon(UUID tenantId, String key, UUID claim) {
        if (key != null && claim != null) {
            idempotency.abandon(tenantId, key, claim);
        }
    }

    /** PostgreSQL's lock_not_available (55P03): another emit held the series row too long. */
    private static boolean lockTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException sql && "55P03".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private JsonNode emitInTransaction(UUID tenantId, UUID companyId, UUID documentId, String idempotencyKey, UUID claim,
            EmitRequest request, String correlationId, boolean platform, Long numeroDado, String origin) {
        TenantScope scope = platform ? access.enterAsPlatform(tenantId) : access.enter(tenantId, Role.OPERATOR);
        // Waits on the series row (another emit holds it across engine, HSM and storage) are bounded
        boundLockWaits();
        // Lock order: company → series → documents (§10.8 rule 4). FOR SHARE: an environment
        // switch (FOR UPDATE) waits for running emits and blocks new ones.
        Company company = Company.findById(companyId, LockModeType.PESSIMISTIC_READ);
        if (company == null || company.deletedAt != null) {
            throw ApiException.notFound();
        }
        ObjectNode document = request.document().deepCopy();
        String kind = document.get("kind").asText();
        ObjectNode spec = (ObjectNode) document.get("spec");
        String tipo = DocumentTypes.tipoComprobante(kind, spec);
        String sunatEnv = company.sunatEnv;

        checkCompany(company, tipo);
        Routes.Assignment route = routes.assign(company, sunatEnv, tipo);

        fillProveedor(spec, company);
        LocalDate fecha = issueDate(spec);
        spec.put("fecha_emision", fecha.toString());
        String serie = chooseSerie(request, spec, company, tipo);
        long numero = numeroDado != null
                ? numbering.claim(tenantId, company.id, sunatEnv, tipo, serie, numeroDado)
                : numbering.reserve(company.id, sunatEnv, tipo, serie);
        spec.put("serie_numero", serie + "-" + numero);

        EngineModels.BuildResponse built = engine.build(document);
        EngineModels.ValidateResponse validation = engine.validate(built.xml());
        if (!validation.valid()) {
            // Rolls back: the number is not consumed (§8)
            String detail = validation.errors().stream().map(f -> f.code() + ": " + f.message()).collect(Collectors.joining("; "));
            throw ApiException.invalid("invalid_document", "El documento no cumple las validaciones de SUNAT.", detail);
        }
        SigningService.Signed signed = signing.sign(company, sunatEnv, built.xml(), tipo);
        signatureLog.record(tenantId, documentId, serie + "-" + numero, signed.signingKeyId(), signed.platformKeyId(), signed.digestValue(),
                scope.actor().auditName());
        EngineModels.InspectResponse identity = engine.inspect(signed.xml());
        checkBoletaBuyer(tipo, spec, identity, fecha);

        byte[] xml = signed.xml().getBytes(StandardCharsets.UTF_8);
        FileStore.Stored xmlFile = files.put(tenantId, xml);
        FileStore.Stored input = files.put(tenantId, bytes(request.document()));
        FileStore.Stored enriched = files.put(tenantId, bytes(built.enrichedJson()));

        Instant now = Instant.now();
        Document doc = new Document();
        doc.id = documentId;
        doc.tenantId = tenantId;
        doc.companyId = company.id;
        doc.sunatEnv = sunatEnv;
        doc.kind = kind;
        doc.origin = origin;
        doc.tipoComprobante = tipo;
        doc.serie = serie;
        doc.numero = numero;
        doc.fechaEmision = fecha;
        doc.issueYear = (short) fecha.getYear();
        doc.deadlineAt = Deadlines.sendDeadline(tipo, serie, fecha);
        doc.customerTipo = identity.customerTipo();
        doc.customerNumero = identity.customerNumero();
        doc.customerNombre = DocumentTypes.customerName(spec);
        doc.moneda = identity.moneda();
        doc.total = identity.total() == null ? null : new BigDecimal(identity.total());
        doc.amounts = Document.amountsOf(identity.amounts());
        doc.paymentTerms = paymentTerms(tipo, spec);
        doc.status = "pending_send";
        doc.digestValue = signed.digestValue();
        doc.endpoint = route.endpoint();
        doc.credentialId = route.credentialId();
        // GRE is always signed and sent by the issuer itself, never by the PSE
        doc.emissionMode = doc.isGuia() ? "software" : company.emissionMode;
        doc.affectedDocumentId = affectedDocument(company.id, sunatEnv, spec);
        doc.nextAttemptAt = now;
        doc.progressAt = now;
        boolean delivered = request.delivered() != null ? request.delivered() : company.deliveredOnEmitDefault;
        if (delivered) {
            doc.deliveredAt = now;
            doc.deliveredSource = "client";
        }
        doc.labels = request.labels() == null ? Map.of() : Map.copyOf(request.labels());
        doc.createdAt = now;
        doc.updatedAt = now;
        try {
            doc.persistAndFlush();
        } catch (jakarta.persistence.PersistenceException e) {
            if (io.github.projectopenubl.xhandler.api.Constraints.violated(e).filter(c -> c.startsWith("document")).isPresent()) {
                throw ApiException.conflict("number_taken", "El número ya fue usado en esta serie. Revise el número siguiente de la serie.");
            }
            throw e;
        }
        DocumentFile.record(doc, "xml_signed", xmlFile);
        DocumentFile.record(doc, "input", input);
        DocumentFile.record(doc, "enriched", enriched);
        DocumentEvent.record(doc, null, "emitted", scope.actor().auditName(), correlationId);
        auditor.record(scope, "document.emitted", "document", doc.id, Map.of("serie_numero", doc.serieNumero()));
        String token = links.create(doc);
        em.createNativeQuery("SELECT pg_notify('xhandler_reconcile', '')").getSingleResult();

        String qr = Qr.of(company.ruc, doc, built.enrichedJson());
        JsonNode body = json.valueToTree(DocumentView.of(doc, links.url(token), qr));
        if (idempotencyKey != null) {
            // Stored without the buyer link: its token is never stored in clear (§12); a replay
            // gets everything else, and a new link can be created
            idempotency.complete(tenantId, idempotencyKey, claim, 201, json.valueToTree(DocumentView.of(doc, null, qr)));
        }
        return body;
    }

    /**
     * A boleta above S/ 700 must identify its buyer (validator 2514, §3.1). The engine checks PEN;
     * foreign-currency boletas are checked here at the document's tipo_cambio, or SUNAT's rate for D.
     */
    private void checkBoletaBuyer(String tipo, ObjectNode spec, EngineModels.InspectResponse identity, LocalDate fecha) {
        if (!"03".equals(tipo) || identity.total() == null || identity.moneda() == null || "PEN".equals(identity.moneda())) {
            return;
        }
        BigDecimal rate;
        if (spec.hasNonNull("tipo_cambio")) {
            try {
                rate = new BigDecimal(spec.get("tipo_cambio").asText());
            } catch (NumberFormatException e) {
                throw ApiException.invalid("invalid_document", "tipo_cambio debe ser un número.", null);
            }
            if (rate.signum() <= 0) {
                throw ApiException.invalid("invalid_document", "tipo_cambio debe ser mayor que cero.", null);
            }
        } else {
            rate = rates.on(fecha).map(io.github.projectopenubl.xhandler.rates.ExchangeRates.Rate::venta).orElse(null);
        }
        if (rate == null) {
            throw ApiException.invalid("invalid_document",
                    "No hay tipo de cambio para " + fecha + ": indique tipo_cambio en la boleta.", null);
        }
        BigDecimal pen = new BigDecimal(identity.total()).multiply(rate);
        String numero = identity.customerNumero() == null ? "" : identity.customerNumero().strip();
        boolean anonymous = identity.customerTipo() == null || "0".equals(identity.customerTipo()) || "-".equals(identity.customerTipo())
                || numero.isEmpty() || numero.chars().allMatch(c -> c == '0' || c == '-');
        if (pen.compareTo(new BigDecimal("700")) > 0 && anonymous) {
            throw ApiException.invalid("invalid_document", "Una boleta mayor a S/ 700 debe identificar al comprador.",
                    "2514: equivalente S/ " + pen.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        }
    }

    /** Facturas on credit (cuotas, or forma_pago.tipo Credito) are facturas negociables (§11.8). */
    static String paymentTerms(String tipo, ObjectNode spec) {
        if (!"01".equals(tipo)) {
            return null;
        }
        JsonNode forma = spec.path("forma_pago");
        boolean credito = forma.path("cuotas").isArray() && !forma.path("cuotas").isEmpty()
                || "credito".equalsIgnoreCase(forma.path("tipo").asText(""));
        return credito ? "credito" : "contado";
    }

    private boolean pseAuthorized(Company company) {
        return !em.createNativeQuery("SELECT 1 FROM xhandler.pse_authorization WHERE company_id = :c AND revoked_at IS NULL AND approved_at IS NOT NULL")
                .setParameter("c", company.id).getResultList().isEmpty();
    }

    /** D defaults to today in Lima; SUNAT refuses more than 2 days ahead (2329) and late documents lose their validity. */
    private static LocalDate issueDate(ObjectNode spec) {
        LocalDate today = Deadlines.todayInLima();
        if (!spec.hasNonNull("fecha_emision")) {
            return today;
        }
        LocalDate fecha;
        try {
            fecha = LocalDate.parse(spec.get("fecha_emision").asText());
        } catch (java.time.format.DateTimeParseException e) {
            throw ApiException.invalid("invalid_document", "fecha_emision debe tener el formato AAAA-MM-DD.", null);
        }
        return checkIssueDate(fecha);
    }

    /** SUNAT refuses more than 2 days ahead (2329); older than 7 days is too late to send. */
    public static LocalDate checkIssueDate(LocalDate fecha) {
        LocalDate today = Deadlines.todayInLima();
        if (fecha.isAfter(today.plusDays(2)) || fecha.isBefore(today.minusDays(7))) {
            throw ApiException.invalid("invalid_document",
                    "fecha_emision debe estar entre " + today.minusDays(7) + " y " + today.plusDays(2) + " (hora de Lima).", "2329");
        }
        return fecha;
    }

    private String chooseSerie(EmitRequest request, ObjectNode spec, Company company, String tipo) {
        if (request.serie() != null) {
            return request.serie();
        }
        if (spec.hasNonNull("serie_numero")) {
            String given = spec.get("serie_numero").asText();
            int dash = given.indexOf('-');
            return dash > 0 ? given.substring(0, dash) : given;
        }
        String prefix = null;
        if ("07".equals(tipo) || "08".equals(tipo)) {
            String affected = spec.path("comprobante_afectado_serie_numero").asText("");
            prefix = affected.startsWith("B") ? "B" : "F";
        } else if ("01".equals(tipo)) {
            prefix = "F";
        } else if ("03".equals(tipo)) {
            prefix = "B";
        }
        return numbering.defaultSerie(company.id, company.sunatEnv, tipo, prefix);
    }

    private static void fillProveedor(ObjectNode spec, Company company) {
        ObjectNode proveedor = spec.hasNonNull("proveedor") && spec.get("proveedor").isObject()
                ? (ObjectNode) spec.get("proveedor")
                : spec.putObject("proveedor");
        if (proveedor.hasNonNull("ruc") && !company.ruc.equals(proveedor.get("ruc").asText())) {
            throw ApiException.invalid("invalid_document", "El RUC del proveedor no coincide con el de la empresa.", null);
        }
        proveedor.put("ruc", company.ruc);
        if (!proveedor.hasNonNull("razon_social")) {
            proveedor.put("razon_social", company.razonSocial);
        }
    }

    /** The document a note affects, if it was issued here (same company and environment). */
    private UUID affectedDocument(UUID companyId, String sunatEnv, ObjectNode spec) {
        String affected = spec.path("comprobante_afectado_serie_numero").asText(null);
        if (affected == null || !affected.contains("-")) {
            return null;
        }
        String serie = affected.substring(0, affected.indexOf('-'));
        String tipoAfectado = spec.path("comprobante_afectado_tipo").asText(serie.startsWith("B") ? "03" : "01");
        long numero;
        try {
            numero = Long.parseLong(affected.substring(affected.indexOf('-') + 1));
        } catch (NumberFormatException e) {
            return null;
        }
        return Document.<Document>find(
                        "companyId = ?1 and sunatEnv = ?2 and tipoComprobante = ?3 and serie = ?4 and numero = ?5 and origin <> 'ose_inbound'",
                        companyId, sunatEnv, tipoAfectado, serie, numero)
                .firstResultOptional()
                .map(d -> d.id)
                .orElse(null);
    }

    private byte[] bytes(JsonNode node) {
        try {
            return json.writeValueAsBytes(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Hash of the request in canonical JSON (keys sorted), so reordered fields are the same request. */
    private String hash(UUID companyId, EmitRequest request) {
        try {
            JsonNode canonical = canonical(json.valueToTree(request));
            return FileStore.sha256((companyId + ":" + json.writeValueAsString(canonical)).getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            java.util.List<String> names = new java.util.ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            java.util.Collections.sort(names);
            for (String name : names) {
                sorted.set(name, canonical(node.get(name)));
            }
            return sorted;
        }
        if (node.isArray()) {
            com.fasterxml.jackson.databind.node.ArrayNode array = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
            node.forEach(item -> array.add(canonical(item)));
            return array;
        }
        return node;
    }
}
