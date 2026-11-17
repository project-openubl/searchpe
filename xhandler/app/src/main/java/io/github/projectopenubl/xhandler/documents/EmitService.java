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
import java.util.Optional;
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
    private final Semaphore permits;
    private final long permitTimeoutMillis;

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
            @ConfigProperty(name = "xhandler.emit.permits", defaultValue = "32") int permits,
            @ConfigProperty(name = "xhandler.emit.permit-timeout", defaultValue = "5s") Duration permitTimeout) {
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
        this.permits = new Semaphore(permits);
        this.permitTimeoutMillis = permitTimeout.toMillis();
    }

    public Emitted emit(UUID tenantId, UUID companyId, String idempotencyKey, EmitRequest request, String correlationId) {
        if (request == null || request.document() == null || !request.document().hasNonNull("kind")
                || !request.document().path("spec").isObject()) {
            throw ApiException.invalid("invalid_request", "Envíe el documento como {\"document\": {\"kind\": ..., \"spec\": {...}}}.", null);
        }
        // Authorize before touching the idempotency table
        QuarkusTransaction.requiringNew().run(() -> access.enter(tenantId, Role.OPERATOR));
        if (idempotencyKey != null) {
            Optional<Idempotency.Stored> stored = idempotency.begin(tenantId, idempotencyKey, hash(companyId, request));
            if (stored.isPresent()) {
                return new Emitted(stored.get().status(), stored.get().body());
            }
        }
        boolean acquired = false;
        try {
            for (EmitQuota q : quota) {
                q.checkEmit(tenantId);
            }
            acquired = permits.tryAcquire(permitTimeoutMillis, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new ApiException(503, "busy", "El servicio está ocupado. Intente nuevamente en unos segundos.");
            }
            UUID documentId = Ids.newId();
            JsonNode body = QuarkusTransaction.requiringNew().call(() ->
                    emitInTransaction(tenantId, companyId, documentId, idempotencyKey, request, correlationId));
            return new Emitted(201, body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(503, "busy", "El servicio está ocupado. Intente nuevamente en unos segundos.");
        } catch (RuntimeException e) {
            if (idempotencyKey != null) {
                idempotency.abandon(tenantId, idempotencyKey);
            }
            throw e;
        } finally {
            if (acquired) {
                permits.release();
            }
        }
    }

    private JsonNode emitInTransaction(UUID tenantId, UUID companyId, UUID documentId, String idempotencyKey, EmitRequest request,
            String correlationId) {
        TenantScope scope = access.enter(tenantId, Role.OPERATOR);
        // Lock order: company → series → documents (§10.8 rule 4). FOR SHARE: an environment
        // switch (FOR UPDATE) waits for running emits and blocks new ones.
        Company company = Company.findById(companyId, LockModeType.PESSIMISTIC_READ);
        if (company == null || company.deletedAt != null) {
            throw ApiException.notFound();
        }
        if (!company.active) {
            throw ApiException.conflict("company_inactive", "La empresa está desactivada.");
        }
        ObjectNode document = request.document().deepCopy();
        String kind = document.get("kind").asText();
        ObjectNode spec = (ObjectNode) document.get("spec");
        String tipo = DocumentTypes.tipoComprobante(kind, spec);
        String sunatEnv = company.sunatEnv;

        Optional<UUID> credential = routes.credentialFor(company, tipo);
        if ("production".equals(sunatEnv) && credential.isEmpty()) {
            throw ApiException.invalid("no_credentials",
                    "La empresa no tiene credenciales SOL" + ("09".equals(tipo) || "31".equals(tipo) ? "/GRE" : "") + " para enviar a SUNAT.", null);
        }

        fillProveedor(spec, company);
        LocalDate fecha = spec.hasNonNull("fecha_emision") ? LocalDate.parse(spec.get("fecha_emision").asText()) : Deadlines.todayInLima();
        spec.put("fecha_emision", fecha.toString());
        String serie = chooseSerie(request, spec, company, tipo);
        long numero = numbering.reserve(company.id, sunatEnv, tipo, serie);
        spec.put("serie_numero", serie + "-" + numero);

        EngineModels.BuildResponse built = engine.build(document);
        EngineModels.ValidateResponse validation = engine.validate(built.xml());
        if (!validation.valid()) {
            // Rolls back: the number is not consumed (§8)
            String detail = validation.errors().stream().map(f -> f.code() + ": " + f.message()).collect(Collectors.joining("; "));
            throw ApiException.invalid("invalid_document", "El documento no cumple las validaciones de SUNAT.", detail);
        }
        SigningService.Signed signed = signing.sign(company, built.xml());
        signatureLog.record(tenantId, documentId, serie + "-" + numero, signed.signingKeyId(), signed.digestValue(),
                scope.actor().auditName());
        EngineModels.InspectResponse identity = engine.inspect(signed.xml());

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
        doc.status = "pending_send";
        doc.digestValue = signed.digestValue();
        doc.endpoint = Routes.endpoint(sunatEnv, tipo);
        doc.credentialId = credential.orElse(null);
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

        DocumentView view = DocumentView.of(doc, links.url(token), Qr.of(company.ruc, doc, built.enrichedJson()));
        JsonNode body = json.valueToTree(view);
        if (idempotencyKey != null) {
            idempotency.complete(tenantId, idempotencyKey, 201, body);
        }
        return body;
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
        long numero;
        try {
            numero = Long.parseLong(affected.substring(affected.indexOf('-') + 1));
        } catch (NumberFormatException e) {
            return null;
        }
        return Document.<Document>find("companyId = ?1 and sunatEnv = ?2 and serie = ?3 and numero = ?4 and origin <> 'ose_inbound'",
                        companyId, sunatEnv, serie, numero)
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

    private String hash(UUID companyId, EmitRequest request) {
        try {
            return FileStore.sha256((companyId + ":" + json.writeValueAsString(request)).getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
