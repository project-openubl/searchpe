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
    /** fake (the sandbox every company starts on, DESIGN.md §26.2), beta or production */
    public String sunatEnv = "fake";
    /** software or pse */
    public String emissionMode = "software";
    public Instant rucVerifiedAt;
    public String email;
    /** Default of {@code delivered} on emit (usually true for POS, DESIGN.md §9.3) */
    public boolean deliveredOnEmitDefault = true;
    public boolean active = true;
    /** Holds SUNAT attempts for all its documents (DESIGN.md §24.1) */
    public boolean sendingPaused;

    @CreationTimestamp
    public Instant createdAt;

    @UpdateTimestamp
    public Instant updatedAt;

    public Instant deletedAt;
}
