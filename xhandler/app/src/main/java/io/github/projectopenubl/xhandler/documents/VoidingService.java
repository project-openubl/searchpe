package io.github.projectopenubl.xhandler.documents;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.audit.Auditor;
import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Voiding (DESIGN.md §9.3, §11.3). Facturas and their notes are voided by a comunicación de baja
 * (RA), boletas and their notes by a resumen diario line with estado 3 (RC), percepciones and
 * retenciones by a resumen de reversiones (RR). GRE can only be voided in SUNAT's SOL.
 *
 * <p>Allowed only for an accepted document that the buyer doesn't have (the request attests it),
 * within 7 calendar days after its issue date (2027 rules), or after the reversal was communicated
 * for RR. Otherwise the answer is 409 and a nota de crédito is the way.
 */
@ApplicationScoped
public class VoidingService {

    /** A void request. {@code reversalCommunicatedAt} is required for percepciones and retenciones. */
    public record VoidRequest(String motivo, Boolean notDelivered, LocalDate reversalCommunicatedAt) {}

    private final TenantAccess access;
    private final Summaries summaries;
    private final Auditor auditor;
    private final EntityManager em;
    private final Event<DocumentStatusChanged> statusChanged;

    public VoidingService(TenantAccess access, Summaries summaries, Auditor auditor, EntityManager em,
            Event<DocumentStatusChanged> statusChanged) {
        this.access = access;
        this.summaries = summaries;
        this.auditor = auditor;
        this.em = em;
        this.statusChanged = statusChanged;
    }

    /**
     * Cancels a void whose RA/RC/RR is stuck in exception (not in flight): the original goes back to
     * its previous status. A rejected voiding does this by itself.
     */
    public Document cancelVoid(UUID tenantId, UUID documentId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            TenantScope scope = access.enter(tenantId, Role.OPERATOR);
            Document original = Document.findById(documentId, LockModeType.PESSIMISTIC_WRITE);
            if (original == null) {
                throw ApiException.notFound();
            }
            Document voiding = original.voidedByDocumentId == null ? null : Document.findById(original.voidedByDocumentId);
            if (!"voiding".equals(original.status) || voiding == null || !"exception".equals(voiding.status)) {
                throw ApiException.conflict("void_not_cancelable", "Solo se cancela una anulación cuyo resumen quedó con error.");
            }
            String previous = original.status;
            original.status = original.statusBeforeVoiding == null ? "accepted" : original.statusBeforeVoiding;
            original.statusBeforeVoiding = null;
            original.voidedByDocumentId = null;
            original.updatedAt = Instant.now();
            DocumentEvent.record(original, null, "voiding " + voiding.serieNumero() + " canceled", scope.actor().auditName(), null);
            auditor.record(scope, "document.void_canceled", "document", original.id);
            statusChanged.fire(new DocumentStatusChanged(original, previous, null));
            return original;
        });
    }

    /** Creates the RA/RC/RR and moves the original to voiding. Returns the voiding document. */
    public Document voidDocument(UUID tenantId, UUID documentId, VoidRequest request, String correlationId) {
        return QuarkusTransaction.requiringNew().call(() -> voidInTransaction(tenantId, documentId, request, correlationId, false));
    }

    /** The same, for an already-authenticated adapter client (Nubefact-compatible API). */
    public Document voidDocumentAsPlatform(UUID tenantId, UUID documentId, VoidRequest request, String correlationId) {
        return QuarkusTransaction.requiringNew().call(() -> voidInTransaction(tenantId, documentId, request, correlationId, true));
    }

    private Document voidInTransaction(UUID tenantId, UUID documentId, VoidRequest request, String correlationId, boolean platform) {
        TenantScope scope = platform ? access.enterAsPlatform(tenantId) : access.enter(tenantId, Role.OPERATOR);
        Document original = Document.findById(documentId);
        if (original == null) {
            throw ApiException.notFound();
        }
        // Lock order (§10.8 rule 4): company → daily counter → documents
        Company company = Company.findById(original.companyId, LockModeType.PESSIMISTIC_READ);
        check(original, request);
        String motivo = request.motivo().strip();
        LocalDate fecha = original.fechaEmision;
        Document voiding;
        if (original.isGuia()) {
            throw ApiException.conflict("void_in_sol_only", "Las guías de remisión solo se dan de baja en SUNAT Operaciones en Línea (SOL).");
        } else if ("20".equals(original.tipoComprobante) || "40".equals(original.tipoComprobante)) {
            // A later RR of the same type and day replaces the earlier one at SUNAT (RS 117-2017 art.
            // 38): each RR carries every reversal of that type, day and issue date made so far
            List<Document> lines = new java.util.ArrayList<>(sameDayReversals(original));
            lines.add(original);
            voiding = summaries.resumenReversiones(scope, company, original.sunatEnv, fecha, lines, motivo, correlationId);
            voiding.deadlineAt = Deadlines.endOfDay(request.reversalCommunicatedAt().plusDays(7));
        } else if (original.isBoletaFamily()) {
            voiding = summaries.resumenDiario(scope, company, original.sunatEnv, fecha, List.of(original), "3", correlationId);
            voiding.deadlineAt = Deadlines.endOfDay(fecha.plusDays(7));
        } else {
            voiding = summaries.comunicacionBaja(scope, company, original.sunatEnv, fecha, List.of(original), motivo, correlationId);
            voiding.deadlineAt = Deadlines.endOfDay(fecha.plusDays(7));
        }
        voiding.affectedDocumentId = original.id;
        // The conditional status change (§9.3): the original must still be accepted and undelivered
        Instant now = Instant.now();
        int moved = em.createNativeQuery("""
                        UPDATE xhandler.document SET status_before_voiding = status, status = 'voiding', voided_by_document_id = :voiding,
                            void_reason = :motivo, reversal_communicated_at = :reversal, updated_at = now()
                        WHERE id = :id AND status IN ('accepted', 'accepted_with_observations') AND delivered_at IS NULL
                          AND voided_by_document_id IS NULL""")
                .setParameter("voiding", voiding.id)
                .setParameter("motivo", motivo)
                .setParameter("reversal", request.reversalCommunicatedAt() == null ? null
                        : request.reversalCommunicatedAt().atStartOfDay(Deadlines.LIMA).toInstant())
                .setParameter("id", original.id)
                .executeUpdate();
        if (moved != 1) {
            // Rolls back the summary too: its number is not consumed
            throw ApiException.conflict("document_changed", "El documento cambió mientras se procesaba la anulación. Intente nuevamente.");
        }
        em.refresh(original);
        DocumentEvent.record(original, null, "voiding requested (" + voiding.serieNumero() + "): " + motivo, scope.actor().auditName(),
                correlationId);
        auditor.record(scope, "document.void_requested", "document", original.id, Map.of("voiding_document", voiding.serieNumero()));
        statusChanged.fire(new DocumentStatusChanged(original, original.statusBeforeVoiding, correlationId));
        voiding.updatedAt = now;
        return voiding;
    }

    /** Originals of today's RRs of the same company, environment, type and issue date (voided or voiding). */
    private static List<Document> sameDayReversals(Document original) {
        String serie = "RR-" + Summaries.BASIC.format(Deadlines.todayInLima());
        List<UUID> rrs = Document.<Document>list("companyId = ?1 and sunatEnv = ?2 and tipoComprobante = 'RR' and serie = ?3 "
                        + "and status not in ('rejected')", original.companyId, original.sunatEnv, serie).stream().map(d -> d.id).toList();
        if (rrs.isEmpty()) {
            return List.of();
        }
        return Document.<Document>list("voidedByDocumentId in ?1 and tipoComprobante = ?2 and fechaEmision = ?3 "
                + "and status in ('voided', 'voiding') order by id", rrs, original.tipoComprobante, original.fechaEmision);
    }

    private static void check(Document original, VoidRequest request) {
        if (request == null || request.motivo() == null || request.motivo().isBlank()) {
            throw ApiException.invalid("invalid_request", "Indique el motivo de la anulación.", null);
        }
        if (original.isSummary() || "ose_inbound".equals(original.origin) || "reception".equals(original.origin)) {
            throw ApiException.conflict("not_voidable", "Este documento no se anula por este medio.");
        }
        if (!Document.ACCEPTED.contains(original.status) || original.voidedByDocumentId != null) {
            throw ApiException.conflict("not_voidable", "Solo se pueden anular documentos aceptados por SUNAT.");
        }
        if (original.deliveredAt != null || !Boolean.TRUE.equals(request.notDelivered())) {
            throw ApiException.conflict("delivered_use_credit_note",
                    "El documento ya fue entregado al comprador (o no se confirmó que no lo fue): emita una nota de crédito.");
        }
        LocalDate today = Deadlines.todayInLima();
        if ("20".equals(original.tipoComprobante) || "40".equals(original.tipoComprobante)) {
            if (request.reversalCommunicatedAt() == null) {
                throw ApiException.invalid("invalid_request", "Indique la fecha en que se comunicó la reversión al proveedor o cliente.", null);
            }
            if (request.reversalCommunicatedAt().isAfter(today) || request.reversalCommunicatedAt().isBefore(original.fechaEmision)) {
                throw ApiException.invalid("invalid_request", "La fecha de comunicación de la reversión debe estar entre la emisión y hoy.", null);
            }
            if (!Deadlines.withinVoidingWindow(request.reversalCommunicatedAt(), today)) {
                throw ApiException.conflict("void_window_passed", "Pasaron más de 7 días desde que se comunicó la reversión.");
            }
        } else if (!Deadlines.withinVoidingWindow(original.fechaEmision, today)) {
            throw ApiException.conflict("void_window_passed", "Pasó el plazo de 7 días para anular: emita una nota de crédito.");
        }
    }
}
