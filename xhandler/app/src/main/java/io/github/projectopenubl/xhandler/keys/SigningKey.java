package io.github.projectopenubl.xhandler.keys;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A company's signing key: a reference into the {@link KeyVault} plus its certificate. */
@Entity
@Table(name = "signing_key")
public class SigningKey extends PanacheEntityBase {

    @Id
    public UUID id;

    public UUID tenantId;
    public UUID companyId;
    public String keyRef;
    /** X.509, DER */
    public byte[] certificate;
    public String subject;
    public String issuer;
    public String serial;
    public String ruc;
    public Instant validFrom;
    public Instant validUntil;
    /** active, replaced or revoked */
    public String status = "active";

    @CreationTimestamp
    public Instant createdAt;

    @UpdateTimestamp
    public Instant updatedAt;

    public static Optional<SigningKey> active(UUID companyId) {
        return find("companyId = ?1 and status = 'active'", companyId).firstResultOptional();
    }
}
