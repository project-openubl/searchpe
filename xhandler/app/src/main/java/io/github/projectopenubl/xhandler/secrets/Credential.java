package io.github.projectopenubl.xhandler.secrets;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A company's secret (SOL user, GRE client, SMTP...), sealed by {@link SecretBox}. */
@Entity
@Table(name = "credential")
public class Credential extends PanacheEntityBase {

    @Id
    public UUID id;

    public UUID tenantId;
    public UUID companyId;
    /** sol, gre, sire, validity, smtp, lookup */
    public String kind;
    /** Non-secret label shown in the API, e.g. the masked user */
    public String label;
    public byte[] ciphertext;
    public byte[] wrappedDataKey;
    public String masterKeyId;

    @CreationTimestamp
    public Instant createdAt;

    @UpdateTimestamp
    public Instant updatedAt;

    SecretBox.Sealed sealed() {
        return new SecretBox.Sealed(ciphertext, wrappedDataKey, masterKeyId);
    }

    void seal(SecretBox.Sealed sealed) {
        ciphertext = sealed.ciphertext();
        wrappedDataKey = sealed.wrappedDataKey();
        masterKeyId = sealed.masterKeyId();
    }
}
