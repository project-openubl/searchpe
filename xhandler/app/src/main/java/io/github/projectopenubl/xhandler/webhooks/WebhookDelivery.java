package io.github.projectopenubl.xhandler.webhooks;

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

/** One event for one subscription, with its retry state and the last outcome (delivery log, §12). */
@Entity
@Table(name = "webhook_delivery")
public class WebhookDelivery extends PanacheEntityBase {

    @Id
    public UUID id;

    public UUID tenantId;
    public UUID subscriptionId;
    public UUID eventId;
    public String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public Map<String, Object> payload;

    /** pending, delivered or failed */
    public String status = "pending";
    public int attempts;
    public Instant nextAttemptAt;
    public Integer lastStatusCode;
    public String lastError;
    public Instant deliveredAt;
    public Instant createdAt;
    public Instant updatedAt;
}
