package io.github.projectopenubl.xhandler.support;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "support_session")
public class SupportSession extends PanacheEntityBase {

    @Id
    public UUID id;

    public UUID tenantId;
    public String operator;
    public String reason;
    public Instant requestedAt;
    public String approvedBy;
    public Instant approvedAt;
    public Instant expiresAt;
    public Instant revokedAt;
}
