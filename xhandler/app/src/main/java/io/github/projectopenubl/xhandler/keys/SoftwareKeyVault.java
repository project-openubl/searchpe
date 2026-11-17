package io.github.projectopenubl.xhandler.keys;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Development and test vault: master keys come from configuration, and signing keys are stored
 * encrypted (wrapped) under a master key inside their reference ({@code sw:<master>:<base64>}).
 * Private keys exist in clear text in the JVM while signing, which an HSM avoids, so this vault is
 * refused in production (see {@link KeyVaultProducer}).
 */
final class SoftwareKeyVault implements KeyVault {

    private static final String PREFIX = "sw:";

    private final Map<String, SecretKey> masterKeys;
    private final String current;

    SoftwareKeyVault(Map<String, String> masterKeysBase64, String current) {
        this.masterKeys = masterKeysBase64.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> aesKey(e.getKey(), e.getValue())));
        if (!masterKeys.containsKey(current)) {
            throw new IllegalArgumentException("xhandler.vault.software: unknown current master key " + current);
        }
        this.current = current;
    }

    @Override
    public String storePrivateKey(PrivateKey key, X509Certificate certificate) {
        return wrapKey(current, key.getEncoded());
    }

    @Override
    public byte[] sign(String keyRef, byte[] data) {
        try {
            PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(unwrapKey(keyRef)));
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(key);
            signature.update(data);
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("signing failed", e);
        }
    }

    @Override
    public void deletePrivateKey(String keyRef) {
        // Nothing stored outside the reference
    }

    @Override
    public String currentMasterKeyId() {
        return current;
    }

    @Override
    public byte[] wrapDataKey(String masterKeyId, byte[] dataKey) {
        return Aes.encrypt(master(masterKeyId), dataKey, null);
    }

    @Override
    public byte[] unwrapDataKey(String masterKeyId, byte[] wrappedDataKey) {
        return Aes.decrypt(master(masterKeyId), wrappedDataKey, null);
    }

    @Override
    public String rewrapPrivateKey(String keyRef) {
        if (keyRef.startsWith(PREFIX + current + ":")) {
            return keyRef;
        }
        return wrapKey(current, unwrapKey(keyRef));
    }

    private String wrapKey(String masterKeyId, byte[] pkcs8) {
        byte[] wrapped = Aes.encrypt(master(masterKeyId), pkcs8, null);
        return PREFIX + masterKeyId + ":" + Base64.getEncoder().encodeToString(wrapped);
    }

    private byte[] unwrapKey(String keyRef) {
        if (!keyRef.startsWith(PREFIX)) {
            throw new IllegalArgumentException("not a software vault key reference");
        }
        String[] parts = keyRef.substring(PREFIX.length()).split(":", 2);
        return Aes.decrypt(master(parts[0]), Base64.getDecoder().decode(parts[1]), null);
    }

    private SecretKey master(String id) {
        SecretKey key = masterKeys.get(id);
        if (key == null) {
            throw new IllegalStateException("unknown master key " + id);
        }
        return key;
    }

    private static SecretKey aesKey(String id, String base64) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        if (bytes.length != 32) {
            throw new IllegalArgumentException("master key " + id + " must be 32 bytes (AES-256)");
        }
        return new SecretKeySpec(bytes, "AES");
    }
}
