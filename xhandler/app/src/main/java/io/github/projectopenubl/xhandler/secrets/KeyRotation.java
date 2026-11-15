package io.github.projectopenubl.xhandler.secrets;

import io.github.projectopenubl.xhandler.keys.KeyVault;
import io.github.projectopenubl.xhandler.keys.SigningKey;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import io.quarkus.narayana.jta.QuarkusTransaction;
import java.util.List;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Re-wraps everything protected by an old master key with the current one, tenant by tenant, without
 * downtime: old master keys stay readable until nothing references them (DESIGN.md §6). Exposed
 * through xhandler-admin (P2).
 */
@ApplicationScoped
public class KeyRotation {

    private static final Logger LOG = Logger.getLogger(KeyRotation.class);

    public record Result(int tenants, int credentials, int signingKeys) {}

    private final EntityManager em;
    private final TenantAccess access;
    private final SecretBox box;
    private final KeyVault vault;

    public KeyRotation(EntityManager em, TenantAccess access, SecretBox box, KeyVault vault) {
        this.em = em;
        this.access = access;
        this.box = box;
        this.vault = vault;
    }

    public Result rewrapAll() {
        int credentials = 0;
        int keys = 0;
        List<UUID> tenants = QuarkusTransaction.requiringNew().call(this::tenantIds);
        for (UUID tenantId : tenants) {
            // One transaction per tenant, so a failure leaves the others done
            int[] counts = QuarkusTransaction.requiringNew().call(() -> rewrapTenant(tenantId));
            credentials += counts[0];
            keys += counts[1];
        }
        LOG.infof("key rotation: %d tenants, %d credentials and %d signing keys re-wrapped with %s",
                tenants.size(), credentials, keys, vault.currentMasterKeyId());
        return new Result(tenants.size(), credentials, keys);
    }

    private List<UUID> tenantIds() {
        @SuppressWarnings("unchecked")
        List<UUID> ids = em.createNativeQuery("SELECT id FROM xhandler.all_tenant_ids()").getResultList();
        return ids;
    }

    private int[] rewrapTenant(UUID tenantId) {
        access.enterAsPlatform(tenantId);
        String current = vault.currentMasterKeyId();
        List<Credential> credentials = Credential.list("masterKeyId <> ?1", current);
        for (Credential credential : credentials) {
            credential.seal(box.rewrap(credential.sealed()));
        }
        int keys = 0;
        for (SigningKey key : SigningKey.<SigningKey>list("status = 'active'")) {
            String rewrapped = vault.rewrapPrivateKey(key.keyRef);
            if (!rewrapped.equals(key.keyRef)) {
                key.keyRef = rewrapped;
                keys++;
            }
        }
        return new int[] {credentials.size(), keys};
    }
}
