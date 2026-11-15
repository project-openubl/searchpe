package io.github.projectopenubl.xhandler.audit;

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

/** Append-only (the app role can't update or delete it): never updated by Hibernate either. */
@Entity
@org.hibernate.annotations.Immutable
@Table(name = "audit_log")
public class AuditLog extends PanacheEntityBase {

    @Id
    public UUID id;

    public Instant at;
    public UUID tenantId;
    public String actor;
    public String action;
    public String objectType;
    public String objectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public Map<String, Object> details;
}
