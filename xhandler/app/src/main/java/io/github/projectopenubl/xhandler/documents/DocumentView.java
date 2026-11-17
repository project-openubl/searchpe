package io.github.projectopenubl.xhandler.documents;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A document as the API shows it. {@code accessUrl} (the buyer's capability link) is only present
 * when the link was just created: the token is never stored in clear.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentView(
        UUID id,
        String prn,
        UUID companyId,
        String sunatEnv,
        String kind,
        String tipoComprobante,
        String serie,
        long numero,
        String serieNumero,
        LocalDate fechaEmision,
        Instant deadlineAt,
        String status,
        String holdReason,
        Integer sunatCode,
        String sunatDescription,
        List<String> sunatNotes,
        String codeClass,
        String digestValue,
        String moneda,
        BigDecimal total,
        Customer customer,
        Map<String, String> labels,
        Instant deliveredAt,
        String deliveredSource,
        int attempts,
        Instant nextAttemptAt,
        Instant firstSentAt,
        Instant cdrReceivedAt,
        UUID affectedDocumentId,
        UUID summaryDocumentId,
        UUID voidedByDocumentId,
        Instant createdAt,
        String accessUrl,
        String qr) {

    public record Customer(String tipo, String numero, String nombre) {}

    public static DocumentView of(Document d) {
        return of(d, null, null);
    }

    public static DocumentView of(Document d, String accessUrl, String qr) {
        return new DocumentView(d.id, "xh:document:" + d.id, d.companyId, d.sunatEnv, d.kind, d.tipoComprobante, d.serie, d.numero,
                d.serieNumero(), d.fechaEmision, d.deadlineAt, d.status, d.holdReason, d.sunatCode, d.sunatDescription, d.sunatNotes,
                d.codeClass, d.digestValue, d.moneda, d.total,
                d.customerNumero == null ? null : new Customer(d.customerTipo, d.customerNumero, d.customerNombre), d.labels,
                d.deliveredAt, d.deliveredSource, d.attempts, d.nextAttemptAt, d.firstSentAt, d.cdrReceivedAt, d.affectedDocumentId,
                d.summaryDocumentId, d.voidedByDocumentId, d.createdAt, accessUrl, qr);
    }
}
