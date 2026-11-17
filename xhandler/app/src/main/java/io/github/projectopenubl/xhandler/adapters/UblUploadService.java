package io.github.projectopenubl.xhandler.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.audit.Auditor;
import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.documents.AccessLinks;
import io.github.projectopenubl.xhandler.documents.Deadlines;
import io.github.projectopenubl.xhandler.documents.Document;
import io.github.projectopenubl.xhandler.documents.DocumentEvent;
import io.github.projectopenubl.xhandler.documents.DocumentFile;
import io.github.projectopenubl.xhandler.documents.DocumentView;
import io.github.projectopenubl.xhandler.documents.Numbering;
import io.github.projectopenubl.xhandler.documents.Routes;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * UBL XML upload (DESIGN.md §5): a document built elsewhere, validated here, signed if unsigned
 * (software or PSE), stored and sent like any other. It keeps its own number. The XML is untrusted
 * input: size-capped here, and the engine refuses DTDs and entities.
 */
@ApplicationScoped
public class UblUploadService {

    static final int MAX_BYTES = 5 * 1024 * 1024;
    static final Set<String> TIPOS = Set.of("01", "03", "07", "08", "09", "31", "20", "40");

    private final TenantAccess access;
    private final Engine engine;
    private final SigningService signing;
    private final SignatureLog signatureLog;
    private final FileStore files;
    private final Numbering numbering;
    private final Routes routes;
    private final AccessLinks links;
    private final Auditor auditor;
    private final EntityManager em;
    private final ObjectMapper json;
    private final io.github.projectopenubl.xhandler.documents.EmitService emit;

    public UblUploadService(TenantAccess access, Engine engine, SigningService signing, SignatureLog signatureLog, FileStore files,
            Numbering numbering, Routes routes, AccessLinks links, Auditor auditor, EntityManager em, ObjectMapper json,
            io.github.projectopenubl.xhandler.documents.EmitService emit) {
        this.emit = emit;
        this.access = access;
        this.engine = engine;
        this.signing = signing;
        this.signatureLog = signatureLog;
        this.files = files;
        this.numbering = numbering;
        this.routes = routes;
        this.links = links;
        this.auditor = auditor;
        this.em = em;
        this.json = json;
    }

    public DocumentView upload(UUID tenantId, UUID companyId, byte[] xmlBytes, String correlationId) {
        if (xmlBytes == null || xmlBytes.length == 0 || xmlBytes.length > MAX_BYTES) {
            throw ApiException.invalid("invalid_document", "El XML debe tener entre 1 byte y 5 MB.", null);
        }
        // Authorize before the engine parses anything
        QuarkusTransaction.requiringNew().run(() -> access.enter(tenantId, Role.OPERATOR));
        String xml = new String(xmlBytes, StandardCharsets.UTF_8);
        EngineModels.InspectResponse identity = engine.inspect(xml);
        if (identity.tipoComprobante() == null || !TIPOS.contains(identity.tipoComprobante()) || identity.numero() == null
                || identity.serie() == null) {
            throw ApiException.invalid("invalid_document", "El XML no es un comprobante que se pueda emitir por este medio.", null);
        }
        EngineModels.ValidateResponse validation = engine.validate(xml);
        if (!validation.valid()) {
            String detail = validation.errors().stream().map(f -> f.code() + ": " + f.message()).collect(Collectors.joining("; "));
            throw ApiException.invalid("invalid_document", "El documento no cumple las validaciones de SUNAT.", detail);
        }
        UUID documentId = Ids.newId();
        // The same gates as any emit (§5): quota, permits, bounded series waits, company checks, issue date
        return emit.admitted(tenantId, companyId, false, true, () -> QuarkusTransaction.requiringNew().call(() -> {
            TenantScope scope = access.enter(tenantId, Role.OPERATOR);
            emit.boundLockWaits();
            Company company = Company.findById(companyId, LockModeType.PESSIMISTIC_READ);
            if (company == null || company.deletedAt != null) {
                throw ApiException.notFound();
            }
            if (!company.ruc.equals(identity.ruc())) {
                throw ApiException.invalid("invalid_document", "El RUC del emisor del XML no es el de la empresa.", null);
            }
            String tipo = identity.tipoComprobante();
            boolean guia = "09".equals(tipo) || "31".equals(tipo);
            emit.checkCompany(company, tipo);
            boolean pse = !guia && "pse".equals(company.emissionMode);
            if (identity.signed() && pse) {
                // As PSE the platform signs: it doesn't send what someone else signed in its name (§3.2)
                throw ApiException.invalid("invalid_document", "La empresa emite con la plataforma como PSE: envíe el XML sin firmar.", null);
            }
            LocalDate fecha = io.github.projectopenubl.xhandler.documents.EmitService.checkIssueDate(
                    identity.fechaEmision() == null ? Deadlines.todayInLima() : LocalDate.parse(identity.fechaEmision()));
            Routes.Assignment route = routes.assign(company, company.sunatEnv, tipo);
            long numero = numbering.claim(tenantId, company.id, company.sunatEnv, tipo, identity.serie(), identity.numero());
            String signedXml = xml;
            String digest = identity.digestValue();
            if (!identity.signed()) {
                SigningService.Signed signed = signing.sign(company, company.sunatEnv, xml, tipo);
                signatureLog.record(tenantId, documentId, identity.serie() + "-" + numero, signed.signingKeyId(), signed.platformKeyId(), signed.digestValue(),
                        scope.actor().auditName());
                signedXml = signed.xml();
                digest = signed.digestValue();
            }
            FileStore.Stored input = files.put(tenantId, xmlBytes);
            FileStore.Stored stored = files.put(tenantId, signedXml.getBytes(StandardCharsets.UTF_8));
            Instant now = Instant.now();
            Document doc = new Document();
            doc.id = documentId;
            doc.tenantId = tenantId;
            doc.companyId = company.id;
            doc.sunatEnv = company.sunatEnv;
            doc.origin = "adapter";
            doc.kind = identity.documentType();
            doc.tipoComprobante = tipo;
            doc.serie = identity.serie();
            doc.numero = numero;
            doc.fechaEmision = fecha;
            doc.issueYear = (short) fecha.getYear();
            doc.deadlineAt = Deadlines.sendDeadline(tipo, identity.serie(), fecha);
            doc.customerTipo = identity.customerTipo();
            doc.customerNumero = identity.customerNumero();
            doc.moneda = identity.moneda();
            doc.total = identity.total() == null ? null : new BigDecimal(identity.total());
            doc.amounts = Document.amountsOf(identity.amounts());
            doc.paymentTerms = "01".equals(tipo) ? (identity.paymentTerms() == null ? "contado" : identity.paymentTerms()) : null;
            doc.status = "pending_send";
            doc.digestValue = digest;
            doc.endpoint = route.endpoint();
            doc.credentialId = route.credentialId();
            // Signed by the issuer before upload: its own signature, never the platform's as PSE
            doc.emissionMode = pse && !identity.signed() ? "pse" : "software";
            doc.nextAttemptAt = now;
            doc.progressAt = now;
            doc.createdAt = now;
            doc.updatedAt = now;
            try {
                doc.persistAndFlush();
            } catch (jakarta.persistence.PersistenceException e) {
                if (io.github.projectopenubl.xhandler.api.Constraints.violated(e)
                        .filter(name -> name.startsWith("document_number") || name.contains("tipo_comprobante_serie")).isPresent()) {
                    throw ApiException.conflict("number_taken", "Ya existe un documento con ese número.");
                }
                throw e;
            }
            DocumentFile.record(doc, "input", input);
            DocumentFile.record(doc, "xml_signed", stored);
            DocumentEvent.record(doc, null, identity.signed() ? "uploaded (UBL XML, signed by the issuer)" : "uploaded (UBL XML)",
                    scope.actor().auditName(), correlationId);
            auditor.record(scope, "document.uploaded", "document", doc.id, Map.of("serie_numero", doc.serieNumero()));
            String token = links.create(doc);
            em.createNativeQuery("SELECT pg_notify('xhandler_reconcile', '')").getSingleResult();
            return DocumentView.of(doc, links.url(token), null);
        }));
    }
}
