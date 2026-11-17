package io.github.projectopenubl.xhandler.keys;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Ids;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The platform's own keys (DESIGN.md §6): the PSE signing key and the OSE CDR key. Separate,
 * permanent vault objects (an HSM in production), never mixed with tenant keys. Replacing one keeps
 * the old reference for retention, marked replaced.
 */
@ApplicationScoped
public class PlatformKeys {

    /** A platform key: a vault reference plus its certificate (DER). */
    public record Key(UUID id, String purpose, String keyRef, byte[] certificate, String subject, String ruc, Instant validUntil) {}

    private final EntityManager em;
    private final KeyVault vault;
    private final HsmExecutor hsm;

    public PlatformKeys(EntityManager em, KeyVault vault, HsmExecutor hsm) {
        this.em = em;
        this.vault = vault;
        this.hsm = hsm;
    }

    /** Imports a PKCS#12 as the platform's {@code purpose} key; the private key goes to the vault. */
    public Key importKey(String purpose, byte[] pkcs12, char[] password) {
        CertificateImporter.Imported imported;
        try {
            imported = CertificateImporter.read(pkcs12, password, Instant.now());
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(pkcs12, (byte) 0);
        }
        String keyRef = hsm.call(() -> vault.storePrivateKey(imported.privateKey(), imported.certificate()));
        try {
            return QuarkusTransaction.requiringNew().call(() -> {
                em.createNativeQuery("UPDATE xhandler.platform_key SET status = 'replaced' WHERE purpose = :p AND status = 'active'")
                        .setParameter("p", purpose).executeUpdate();
                UUID id = Ids.newId();
                em.createNativeQuery("""
                                INSERT INTO xhandler.platform_key (id, purpose, key_ref, certificate, subject, ruc, valid_from, valid_until)
                                VALUES (:id, :p, :ref, :cert, :subject, :ruc, :from, :until)""")
                        .setParameter("id", id).setParameter("p", purpose).setParameter("ref", keyRef)
                        .setParameter("cert", imported.certificate().getEncoded())
                        .setParameter("subject", imported.certificate().getSubjectX500Principal().getName())
                        .setParameter("ruc", imported.ruc().orElse(null))
                        .setParameter("from", imported.certificate().getNotBefore().toInstant())
                        .setParameter("until", imported.certificate().getNotAfter().toInstant())
                        .executeUpdate();
                return active(purpose).orElseThrow();
            });
        } catch (RuntimeException e) {
            // The transaction rolled back: don't leave the key in the vault
            hsm.call(() -> {
                vault.deletePrivateKey(keyRef);
                return null;
            });
            throw e;
        }
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public Optional<Key> active(String purpose) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT id, purpose, key_ref, certificate, subject, ruc, valid_until FROM xhandler.platform_key WHERE purpose = :p AND status = 'active'")
                .setParameter("p", purpose).getResultList();
        return rows.stream().findFirst().map(r -> new Key((UUID) r[0], (String) r[1], (String) r[2], (byte[]) r[3], (String) r[4],
                (String) r[5], io.github.projectopenubl.xhandler.status.StatusResource.toInstant(r[6])));
    }

    /** SHA256withRSA inside the vault, on the HSM executor. */
    public byte[] sign(Key key, byte[] data) {
        if (key.validUntil().isBefore(Instant.now())) {
            throw ApiException.invalid("platform_certificate_expired", "El certificado de la plataforma venció.", null);
        }
        return hsm.call(() -> vault.sign(key.keyRef(), data));
    }
}
