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

    @CreationTimestamp
    public Instant createdAt;

    @UpdateTimestamp
    public Instant updatedAt;

    public Instant deletedAt;
}
