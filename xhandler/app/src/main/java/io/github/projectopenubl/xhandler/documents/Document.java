package io.github.projectopenubl.xhandler.documents;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An issued document (DESIGN.md §7, §9). Never deleted; once issued only its status columns
 * change. The table is partitioned by {@code issueYear}; ids are unique by construction (UUIDv7).
 */
@Entity
@Table(name = "document")
public class Document extends PanacheEntityBase {

    /** No definitive answer yet: the reconciler keeps attempting these (§24.2). */
    public static final Set<String> RETRYABLE = Set.of("pending_send", "in_progress", "unknown");

    public static final Set<String> ACCEPTED = Set.of("accepted", "accepted_with_observations");

    @Id
    public UUID id;

    public short issueYear;
    public UUID tenantId;
    public UUID companyId;
    /** beta, production or fake */
    public String sunatEnv;
    public String origin = "api";
    /** xbuilder kind: Invoice, CreditNote, DebitNote, DespatchAdvice, Perception, Retention, SummaryDocuments, VoidedDocuments */
    public String kind;
    /** Catalog 01 code, or RC/RA/RR for summaries */
    public String tipoComprobante;
    public String serie;
    public long numero;
    public LocalDate fechaEmision;
    public Instant deadlineAt;
    public String customerTipo;
    public String customerNumero;
    public String customerNombre;
    public String moneda;
    public BigDecimal tipoCambio;
    public BigDecimal total;
    public String status;
    public String statusBeforeVoiding;
    public Integer sunatCode;
    public String sunatDescription;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public List<String> sunatNotes;

    public String codeClass;
    public String ticket;
    public String digestValue;
    /** Where it is sent: {@code <env>:<service>} or {@code ose:<id>} (§24.2) */
    public String endpoint;
    /** SOL credential used to send (credential slots, §10.2); null = the environment's shared user */
    public UUID credentialId;
    public UUID affectedDocumentId;
    public UUID voidedByDocumentId;
    public UUID summaryDocumentId;
    /** null, needs_attention or operator */
    public String holdReason;
    public int attempts;
    public Instant nextAttemptAt;
    public UUID attemptJobId;
    public Instant sendStartedAt;
    public Instant firstSentAt;
    public Instant cdrReceivedAt;
    public Instant reversalCommunicatedAt;
    public String lastOutcome;
    public int sameOutcomeCount;
    public Instant progressAt;
    public Instant deliveredAt;
    public Instant deadlineAlertedAt;
    public String deliveredSource;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public Map<String, String> labels = Map.of();

    public String deviceId;
    public Instant createdAt;
    public Instant updatedAt;

    public String serieNumero() {
        return serie + "-" + numero;
    }

    public boolean definitive() {
        return !RETRYABLE.contains(status) && !"draft".equals(status);
    }

    public boolean isSummary() {
        return "RC".equals(tipoComprobante) || "RA".equals(tipoComprobante) || "RR".equals(tipoComprobante);
    }

    /** Boletas and their notes (series starting with B) */
    public boolean isBoletaFamily() {
        return "03".equals(tipoComprobante) || (("07".equals(tipoComprobante) || "08".equals(tipoComprobante)) && serie.startsWith("B"));
    }

    /** Facturas and their notes (series starting with F) */
    public boolean isFacturaFamily() {
        return "01".equals(tipoComprobante) || (("07".equals(tipoComprobante) || "08".equals(tipoComprobante)) && serie.startsWith("F"));
    }

    public boolean isGuia() {
        return "09".equals(tipoComprobante) || "31".equals(tipoComprobante);
    }
}
