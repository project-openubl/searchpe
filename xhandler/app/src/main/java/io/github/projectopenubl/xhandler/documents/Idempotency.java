package io.github.projectopenubl.xhandler.documents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code Idempotency-Key} on creates (DESIGN.md §11.1). The first request inserts the key in its
 * own committed transaction, so concurrent retries see it:
 *
 * <ul>
 *   <li>same hash and a stored response: the stored response is returned;
 *   <li>same key while the first request runs: 409;
 *   <li>a different request body: 422.
 * </ul>
 *
 * A request that fails removes its key, so the client can retry. A key left "running" by a crashed
 * process is taken over after two minutes (longer than any emit).
 */
@ApplicationScoped
public class Idempotency {

    /** A response stored for replay. */
    public record Stored(int status, JsonNode body) {}

    private final EntityManager em;
    private final TenantAccess access;
    private final ObjectMapper json;

    public Idempotency(EntityManager em, TenantAccess access, ObjectMapper json) {
        this.em = em;
        this.access = access;
        this.json = json;
    }

    /** Claims the key. Empty: go ahead. A value: replay it. Throws 409/422 as described above. */
    public Optional<Stored> begin(UUID tenantId, String key, String requestHash) {
        return QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(tenantId);
            int inserted = em.createNativeQuery("""
                            INSERT INTO xhandler.idempotency_key (tenant_id, key, request_hash) VALUES (:tenant, :key, :hash)
                            ON CONFLICT (tenant_id, key) DO NOTHING""")
                    .setParameter("tenant", tenantId)
                    .setParameter("key", key)
                    .setParameter("hash", requestHash)
                    .executeUpdate();
            if (inserted == 1) {
                return Optional.<Stored>empty();
            }
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery("""
                            SELECT request_hash, status_code, CAST(response AS text), created_at < now() - interval '2 minutes'
                            FROM xhandler.idempotency_key WHERE tenant_id = :tenant AND key = :key FOR UPDATE""")
                    .setParameter("tenant", tenantId)
                    .setParameter("key", key)
                    .getResultList();
            if (rows.isEmpty()) {
                // Removed by a failing first request in the meantime
                throw ApiException.conflict("idempotency_in_progress", "Una solicitud con la misma Idempotency-Key está en proceso.");
            }
            Object[] row = rows.get(0);
            if (!requestHash.equals(row[0])) {
                throw new ApiException(422, "idempotency_key_reused",
                        "La Idempotency-Key ya se usó con una solicitud diferente.");
            }
            if (row[1] == null) {
                if (Boolean.TRUE.equals(row[3])) {
                    em.createNativeQuery("UPDATE xhandler.idempotency_key SET created_at = now() WHERE tenant_id = :tenant AND key = :key")
                            .setParameter("tenant", tenantId)
                            .setParameter("key", key)
                            .executeUpdate();
                    return Optional.<Stored>empty();
                }
                throw ApiException.conflict("idempotency_in_progress", "Una solicitud con la misma Idempotency-Key está en proceso.");
            }
            return Optional.of(new Stored(((Number) row[1]).intValue(), json.readTree((String) row[2])));
        });
    }

    /** Stores the response, in the business transaction (it commits with the document). */
    @Transactional(Transactional.TxType.MANDATORY)
    public void complete(UUID tenantId, String key, int status, Object body) {
        em.createNativeQuery("""
                        UPDATE xhandler.idempotency_key SET status_code = :status, response = CAST(:body AS jsonb), completed_at = now()
                        WHERE tenant_id = :tenant AND key = :key""")
                .setParameter("status", status)
                .setParameter("body", write(body))
                .setParameter("tenant", tenantId)
                .setParameter("key", key)
                .executeUpdate();
    }

    /** The request failed: free the key so the client can retry. */
    public void abandon(UUID tenantId, String key) {
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenantId);
            em.createNativeQuery("DELETE FROM xhandler.idempotency_key WHERE tenant_id = :tenant AND key = :key AND status_code IS NULL")
                    .setParameter("tenant", tenantId)
                    .setParameter("key", key)
                    .executeUpdate();
        });
    }

    private String write(Object body) {
        try {
            return json.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
