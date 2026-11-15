package io.github.projectopenubl.xhandler.keys;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * Where private keys and master keys live (DESIGN.md §6). Private keys are used inside the vault
 * and never handed out. Implementations must be thread-safe.
 *
 * <p>Production uses {@link Pkcs11KeyVault} (an HSM). {@link SoftwareKeyVault} is for development
 * and tests only.
 */
public interface KeyVault {

    /** Stores a tenant's signing key. Returns a reference to keep in the database. */
    String storePrivateKey(PrivateKey key, X509Certificate certificate);

    /** RSA PKCS#1 v1.5 with SHA-256 ({@code SHA256withRSA}) over {@code data}, inside the vault. */
    byte[] sign(String keyRef, byte[] data);

    /** Removes a signing key that is no longer used. */
    void deletePrivateKey(String keyRef);

    /** Id of the master key new secrets are wrapped with. */
    String currentMasterKeyId();

    /** Encrypts a data key with a master key (envelope encryption). */
    byte[] wrapDataKey(String masterKeyId, byte[] dataKey);

    byte[] unwrapDataKey(String masterKeyId, byte[] wrappedDataKey);

    /**
     * Re-protects a stored signing key with the current master key, if it depends on one. Returns
     * the new reference, or the same one when nothing changes.
     */
    String rewrapPrivateKey(String keyRef);
}
