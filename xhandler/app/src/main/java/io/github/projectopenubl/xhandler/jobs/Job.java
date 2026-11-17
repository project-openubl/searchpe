package io.github.projectopenubl.xhandler.jobs;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A tenant's job, as the API shows it (row-level security limits it to the open tenant). Workers
 * never use this entity: they go through {@link JobStore}.
 */
@Entity
@Table(name = "job")
public class Job extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(insertable = false, updatable = false)
    public Long seq;

    public UUID tenantId;
    public String name;
    public String state = "waiting";
    public short priorityClass = 2;
    public Instant runAt;
    public Instant unblockedAt;

    public String[] reservedResources = new String[0];

    public String credentialKey;
    public Integer credentialSlots;
    public String endpoint;
    public int attempt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public Map<String, Object> args = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public Map<String, Object> result;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public Map<String, Object> error;

    public String worker;
    public Instant startedAt;
    public Instant finishedAt;
    public String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public List<String> createdResources;

    public Instant createdAt;
    public Instant updatedAt;
}
