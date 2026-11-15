package io.github.projectopenubl.xhandler.company;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** An issuer (RUC) of a tenant. A RUC exists once on the platform per environment. */
@Entity
@Table(name = "company")
public class Company extends PanacheEntityBase {

    @Id
    public UUID id;

    public UUID tenantId;
    public String ruc;
    public String razonSocial;
    public String nombreComercial;
    /** beta or production */
    public String sunatEnv = "beta";
    /** software or pse */
    public String emissionMode = "software";
    public Instant rucVerifiedAt;
    public boolean active = true;

    @CreationTimestamp
    public Instant createdAt;

    @UpdateTimestamp
    public Instant updatedAt;

    public Instant deletedAt;
}
