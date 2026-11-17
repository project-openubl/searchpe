package io.github.projectopenubl.xhandler.tenancy;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "tenant")
public class Tenant extends PanacheEntityBase {

    @Id
    public UUID id;

    public UUID partnerId;
    public String name;
    public String plan = "free";
    public String status = "active";
    public int retentionYears = 10;
    public boolean auditHold;
    public String createdBy;
    /** Holds SUNAT attempts for all its documents (DESIGN.md §24.1) */
    public boolean sendingPaused;
    /** Where tenant notifications are emailed (DESIGN.md §26.6) */
    public String notificationEmail;
    public String billingRuc;
    public String billingName;
    public String billingEmail;
    /** Exit (DESIGN.md §13): kept past the exit period instead of deleted */
    public boolean archivePlan;
    public Instant exitRequestedAt;
    public Instant deleteAfter;

    @CreationTimestamp
    public Instant createdAt;

    @UpdateTimestamp
    public Instant updatedAt;

    public Instant deletedAt;
}
