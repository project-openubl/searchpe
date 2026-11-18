package io.github.projectopenubl.xhandler.documents;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A document being edited for manual issuing (DESIGN.md §9.1, §11.10): an xbuilder document with
 * no number. Issuing it emits a {@link Document} and marks the draft {@code issued} in the same
 * transaction. Soft-deleted.
 */
@Entity
@Table(name = "document_draft")
public class DocumentDraft extends PanacheEntityBase {

    @Id
    public UUID id;

    public UUID tenantId;
    public UUID companyId;
    /** xbuilder kind: Invoice, CreditNote, DebitNote… */
    public String kind;
    public String tipoComprobante;
    /** The series to number it in; null = the default series when issued */
    public String serie;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public Map<String, Object> spec = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public Map<String, String> labels = Map.of();

    /** draft or issued */
    public String status = "draft";
    /** Incremented on every change: issuing and edits compare it (§10.8 rule 2) */
    public int version = 1;
    /** The issued document */
    public UUID documentId;
    public String createdBy;
    public Instant createdAt;
    public Instant updatedAt;
    public Instant deletedAt;
}
