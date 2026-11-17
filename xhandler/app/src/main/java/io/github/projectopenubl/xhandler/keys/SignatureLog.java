package io.github.projectopenubl.xhandler.keys;

import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.util.UUID;

/**
 * Logs every signature (DESIGN.md §6): who, document, key, time. Each entry commits on its own, so a
 * signature made in an emit that rolls back stays logged; its document id then matches no
 * document, which marks it discarded (§8).
 */
@ApplicationScoped
public class SignatureLog {

    private final EntityManager em;
    private final TenantAccess access;

    public SignatureLog(EntityManager em, TenantAccess access) {
        this.em = em;
        this.access = access;
    }

    public void record(UUID tenantId, UUID documentId, String serieNumero, UUID signingKeyId, String digestValue, String actor) {
        record(tenantId, documentId, serieNumero, signingKeyId, null, digestValue, actor);
    }

    public void record(UUID tenantId, UUID documentId, String serieNumero, UUID signingKeyId, UUID platformKeyId, String digestValue,
            String actor) {
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenantId);
            em.createNativeQuery("""
                            INSERT INTO xhandler.signature_log (id, tenant_id, document_id, serie_numero, signing_key_id, platform_key_id,
                                digest_value, actor)
                            VALUES (:id, :tenant, :document, :serie, :key, :platformKey, :digest, :actor)""")
                    .setParameter("platformKey", platformKeyId)
                    .setParameter("id", Ids.newId())
                    .setParameter("tenant", tenantId)
                    .setParameter("document", documentId)
                    .setParameter("serie", serieNumero)
                    .setParameter("key", signingKeyId)
                    .setParameter("digest", digestValue)
                    .setParameter("actor", actor)
                    .executeUpdate();
        });
    }
}
