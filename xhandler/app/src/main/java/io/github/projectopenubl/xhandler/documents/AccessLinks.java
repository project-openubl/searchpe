package io.github.projectopenubl.xhandler.documents;

import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.storage.FileStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Buyer capability links (DESIGN.md §12): a random 128-bit token per document, stored hashed. The
 * link opens the document's XML, CDR and PDF for the legal access period (1 year, §3.3).
 */
@ApplicationScoped
public class AccessLinks {

    /** A link resolved to its document. */
    public record Resolved(UUID tenantId, UUID documentId) {}

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EntityManager em;
    private final String publicBaseUrl;
    private final Duration validity;

    public AccessLinks(
            EntityManager em,
            @ConfigProperty(name = "xhandler.public-url", defaultValue = "http://localhost:8080") String publicBaseUrl,
            @ConfigProperty(name = "xhandler.access-link.validity", defaultValue = "366d") Duration validity) {
        this.em = em;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.validity = validity;
    }

    /** Creates a link; the token is only ever returned here. Runs with the document's tenant open. */
    @Transactional(Transactional.TxType.MANDATORY)
    public String create(Document document) {
        byte[] raw = new byte[16];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        em.createNativeQuery("""
                        INSERT INTO xhandler.access_token (id, tenant_id, document_id, token_hash, expires_at)
                        VALUES (:id, :tenant, :document, :hash, :expires)""")
                .setParameter("id", Ids.newId())
                .setParameter("tenant", document.tenantId)
                .setParameter("document", document.id)
                .setParameter("hash", hash(token))
                .setParameter("expires", Instant.now().plus(validity))
                .executeUpdate();
        return token;
    }

    public String url(String token) {
        return publicBaseUrl + "/api/v1/public/documents/" + token;
    }

    /** The document a token opens, if valid (cross-tenant lookup through a SECURITY DEFINER function). */
    @Transactional(Transactional.TxType.MANDATORY)
    public Optional<Resolved> resolve(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{22}")) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT tenant_id, document_id FROM xhandler.access_token_document(:hash)")
                .setParameter("hash", hash(token))
                .getResultList();
        return rows.stream().findFirst().map(r -> new Resolved((UUID) r[0], (UUID) r[1]));
    }

    static String hash(String token) {
        return FileStore.sha256(token.getBytes(StandardCharsets.US_ASCII));
    }
}
