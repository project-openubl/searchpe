package io.github.projectopenubl.xhandler.webhooks;

import io.github.projectopenubl.xhandler.secrets.SecretBox;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A tenant's webhook endpoint. Its signing secret is sealed like any other secret (§6). */
@Entity
@Table(name = "webhook_subscription")
public class WebhookSubscription extends PanacheEntityBase {

    @Id
    public UUID id;

    public UUID tenantId;
    public String url;

    public String[] events;

    public byte[] ciphertext;
    public byte[] wrappedDataKey;
    public String masterKeyId;
    public boolean active = true;
    public Instant createdAt;
    public Instant updatedAt;
    public Instant deletedAt;

    String context() {
        return "webhook:" + id + ":" + tenantId;
    }

    SecretBox.Sealed sealed() {
        return new SecretBox.Sealed(ciphertext, wrappedDataKey, masterKeyId);
    }

    void seal(SecretBox.Sealed sealed) {
        ciphertext = sealed.ciphertext();
        wrappedDataKey = sealed.wrappedDataKey();
        masterKeyId = sealed.masterKeyId();
    }

    /** {@code *} subscribes to everything; {@code document.*} to every document event. */
    boolean wants(String type) {
        for (String pattern : events) {
            if (pattern.equals("*") || pattern.equals(type) || (pattern.endsWith(".*") && type.startsWith(pattern.substring(0, pattern.length() - 1)))) {
                return true;
            }
        }
        return false;
    }
}
