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
@Table(name = "tenant_member")
public class TenantMember extends PanacheEntityBase {

    @Id
    public UUID id;

    public UUID tenantId;
    /** user or client */
    public String kind;
    public String subject;
    public String role;

    @CreationTimestamp
    public Instant createdAt;

    @UpdateTimestamp
    public Instant updatedAt;
}
