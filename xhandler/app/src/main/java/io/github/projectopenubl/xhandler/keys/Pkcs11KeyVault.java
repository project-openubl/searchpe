package io.github.projectopenubl.xhandler.keys;

import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.UUID;
import javax.crypto.SecretKey;

/**
 * HSM vault through PKCS#11 (SunPKCS11). Signing keys are token objects ({@code p11:<alias>}) and
 * never leave the HSM; master keys are AES-256 token objects ({@code master-*}) used for envelope
 * encryption inside the HSM (DESIGN.md §6).
 *
 * <p>Keys are created non-extractable and sensitive. Master keys are created out of band (e.g.
 * with the HSM's tooling) and must exist at startup: generating them here would race between
 * replicas and silently accept a mistyped id.
 *
 * <p>Tenant keys are stored one object each for now. Wrapping them under an HSM master key (to
 * avoid object limits at thousands of tenants, DESIGN.md §25) can replace this without changing
 * {@link KeyVault}.
 */
final class Pkcs11KeyVault implements KeyVault {

    private static final String PREFIX = "p11:";

    private final Provider provider;
    private final KeyStore keyStore;
    private final String current;
    /** Key handles: KeyStore lookups are serialized inside SunPKCS11, signing isn't. */
    private final java.util.concurrent.ConcurrentHashMap<String, Key> handles = new java.util.concurrent.ConcurrentHashMap<>();

    Pkcs11KeyVault(String library, int slotListIndex, char[] pin, String currentMasterKey) {
        try {
            String config = "--name=xhandler\nlibrary=" + library + "\nslotListIndex=" + slotListIndex + "\n"
                    // Keys never leave the HSM, whatever the token's defaults
                    + "attributes(*,CKO_PRIVATE_KEY,*) = {\n  CKA_SENSITIVE = true\n  CKA_EXTRACTABLE = false\n  CKA_TOKEN = true\n}\n"
                    + "attributes(*,CKO_SECRET_KEY,*) = {\n  CKA_SENSITIVE = true\n  CKA_EXTRACTABLE = false\n  CKA_TOKEN = true\n}\n";
            this.provider = Security.getProvider("SunPKCS11").configure(config);
            this.keyStore = KeyStore.getInstance("PKCS11", provider);
            this.keyStore.load(null, pin);
            this.current = currentMasterKey;
            master(currentMasterKey); // fails now if it doesn't exist
        } catch (GeneralSecurityException | java.io.IOException e) {
            throw new IllegalStateException("could not open the PKCS#11 token", e);
        }
    }

    @Override
    public synchronized String storePrivateKey(PrivateKey key, X509Certificate certificate) {
        String alias = "key-" + UUID.randomUUID();
        try {
            keyStore.setKeyEntry(alias, key, null, new Certificate[] {certificate});
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("could not store the key in the HSM", e);
        }
        return PREFIX + alias;
    }

    @Override
    public byte[] sign(String keyRef, byte[] data) {
        try {
            PrivateKey key = (PrivateKey) handle(alias(keyRef));
            Signature signature = Signature.getInstance("SHA256withRSA", provider);
            signature.initSign(key);
            signature.update(data);
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HSM signing failed", e);
        }
    }

    @Override
    public synchronized void deletePrivateKey(String keyRef) {
        try {
            handles.remove(alias(keyRef));
            keyStore.deleteEntry(alias(keyRef));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("could not delete the key from the HSM", e);
        }
    }

    @Override
    public String currentMasterKeyId() {
        return current;
    }

    @Override
    public byte[] wrapDataKey(String masterKeyId, byte[] dataKey) {
        return Aes.encrypt(master(masterKeyId), dataKey, provider);
    }

    @Override
    public byte[] unwrapDataKey(String masterKeyId, byte[] wrappedDataKey) {
        return Aes.decrypt(master(masterKeyId), wrappedDataKey, provider);
    }

    @Override
    public String rewrapPrivateKey(String keyRef) {
        return keyRef; // token objects don't depend on a master key
    }

    private SecretKey master(String id) {
        if (!(handle(id) instanceof SecretKey secretKey)) {
            throw new IllegalStateException("master key " + id + " is not an AES key");
        }
        return secretKey;
    }

    private Key handle(String alias) {
        return handles.computeIfAbsent(alias, a -> {
            try {
                Key key = keyStore.getKey(a, null);
                if (key == null) {
                    throw new IllegalStateException("key " + a + " not found in the HSM");
                }
                return key;
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("could not read key " + a + " from the HSM", e);
            }
        });
    }

    private static String alias(String keyRef) {
        if (!keyRef.startsWith(PREFIX)) {
            throw new IllegalArgumentException("not a PKCS#11 key reference");
        }
        return keyRef.substring(PREFIX.length());
    }
}
