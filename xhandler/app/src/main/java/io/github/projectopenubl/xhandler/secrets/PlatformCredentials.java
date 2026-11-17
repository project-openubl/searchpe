package io.github.projectopenubl.xhandler.secrets;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Ids;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.jboss.logging.Logger;

/**
 * The platform's own credentials (DESIGN.md §7.1): the pool of PSE SOL users, the OSE's SUNAT
 * credentials, the validity API. Sealed like tenant secrets; every read is logged (no tenant audit
 * log owns them). PSE documents spread over the pool, so no single SOL user is a bottleneck (§10.2).
 */
@ApplicationScoped
public class PlatformCredentials {

    /** A pool member without its secret. */
    public record Entry(UUID id, String purpose, String label, boolean active) {}

    static final Logger AUDIT = Logger.getLogger("xhandler.audit.platform");

    private final EntityManager em;
    private final SecretBox box;
    private final ObjectMapper json;

    public PlatformCredentials(EntityManager em, SecretBox box, ObjectMapper json) {
        this.em = em;
        this.box = box;
        this.json = json;
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public Entry add(String purpose, String label, Map<String, String> fields) {
        UUID id = Ids.newId();
        SecretBox.Sealed sealed;
        try {
            sealed = box.seal(json.writeValueAsBytes(fields), context(id, purpose));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        em.createNativeQuery("""
                        INSERT INTO xhandler.platform_credential (id, purpose, label, ciphertext, wrapped_data_key, master_key_id)
                        VALUES (:id, :p, :l, :c, :w, :m)""")
                .setParameter("id", id).setParameter("p", purpose).setParameter("l", label).setParameter("c", sealed.ciphertext())
                .setParameter("w", sealed.wrappedDataKey()).setParameter("m", sealed.masterKeyId()).executeUpdate();
        return new Entry(id, purpose, label, true);
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public List<Entry> list(String purpose) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT id, purpose, label, active FROM xhandler.platform_credential WHERE purpose = :p ORDER BY label")
                .setParameter("p", purpose).getResultList();
        return rows.stream().map(r -> new Entry((UUID) r[0], (String) r[1], (String) r[2], (Boolean) r[3])).toList();
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public boolean setActive(UUID id, boolean active) {
        return em.createNativeQuery("UPDATE xhandler.platform_credential SET active = :a, updated_at = now() WHERE id = :id")
                .setParameter("a", active).setParameter("id", id).executeUpdate() == 1;
    }

    /** An active pool member, chosen at random so documents spread over the pool. */
    @Transactional(Transactional.TxType.MANDATORY)
    public Optional<UUID> pick(String purpose) {
        @SuppressWarnings("unchecked")
        List<UUID> ids = em.createNativeQuery("SELECT id FROM xhandler.platform_credential WHERE purpose = :p AND active")
                .setParameter("p", purpose).getResultList();
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(ThreadLocalRandom.current().nextInt(ids.size())));
    }

    /** The secret fields of a credential; an inactive one is replaced by another active member. */
    @Transactional(Transactional.TxType.MANDATORY)
    public Map<String, String> read(UUID id, String purpose, String why) {
        // The id must exist for this purpose; only an inactive (retired) member is replaced
        boolean exists = !em.createNativeQuery("SELECT 1 FROM xhandler.platform_credential WHERE id = :id AND purpose = :p")
                .setParameter("id", id).setParameter("p", purpose).getResultList().isEmpty();
        if (!exists) {
            throw new IllegalStateException("platform credential " + id + " is not a " + purpose + " credential");
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT id, ciphertext, wrapped_data_key, master_key_id, active FROM xhandler.platform_credential
                        WHERE purpose = :p AND (id = :id OR active) ORDER BY (id = :id AND active) DESC, random() LIMIT 1""")
                .setParameter("p", purpose).setParameter("id", id).getResultList();
        if (rows.isEmpty() || !Boolean.TRUE.equals(rows.get(0)[4])) {
            throw ApiException.invalid("no_credentials", "La plataforma no tiene credenciales activas para " + purpose + ".", null);
        }
        Object[] r = rows.get(0);
        AUDIT.infof("platform credential %s (%s) read for %s", r[0], purpose, why);
        try {
            return json.readValue(box.open(new SecretBox.Sealed((byte[]) r[1], (byte[]) r[2], (String) r[3]), context((UUID) r[0], purpose)),
                    new TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static String context(UUID id, String purpose) {
        return "platform-credential:" + id + ":" + purpose;
    }
}
