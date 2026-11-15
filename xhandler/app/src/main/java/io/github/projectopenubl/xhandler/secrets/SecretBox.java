package io.github.projectopenubl.xhandler.secrets;

import io.github.projectopenubl.xhandler.keys.KeyVault;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Envelope encryption (DESIGN.md §6): each secret gets its own random AES-256 data key; the secret
 * is encrypted with it (AES-GCM) and the data key is wrapped by a master key in the {@link
 * KeyVault}. Rotating master keys only re-wraps data keys.
 */
@ApplicationScoped
public class SecretBox {

    /** A sealed secret. {@code ciphertext} is {@code iv || ciphertext+tag}. */
    public record Sealed(byte[] ciphertext, byte[] wrappedDataKey, String masterKeyId) {}

    private static final SecureRandom RANDOM = new SecureRandom();

    private final KeyVault vault;

    public SecretBox(KeyVault vault) {
        this.vault = vault;
    }

    public Sealed seal(byte[] plaintext) {
        byte[] dataKey = new byte[32];
        RANDOM.nextBytes(dataKey);
        try {
            String master = vault.currentMasterKeyId();
            return new Sealed(encrypt(dataKey, plaintext), vault.wrapDataKey(master, dataKey), master);
        } finally {
            Arrays.fill(dataKey, (byte) 0);
        }
    }

    public byte[] open(Sealed sealed) {
        byte[] dataKey = vault.unwrapDataKey(sealed.masterKeyId(), sealed.wrappedDataKey());
        try {
            return decrypt(dataKey, sealed.ciphertext());
        } finally {
            Arrays.fill(dataKey, (byte) 0);
        }
    }

    /** Re-wraps the data key with the current master key; the secret itself is untouched. */
    public Sealed rewrap(Sealed sealed) {
        String current = vault.currentMasterKeyId();
        if (current.equals(sealed.masterKeyId())) {
            return sealed;
        }
        byte[] dataKey = vault.unwrapDataKey(sealed.masterKeyId(), sealed.wrappedDataKey());
        try {
            return new Sealed(sealed.ciphertext(), vault.wrapDataKey(current, dataKey), current);
        } finally {
            Arrays.fill(dataKey, (byte) 0);
        }
    }

    private static byte[] encrypt(byte[] key, byte[] plaintext) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("could not encrypt the secret", e);
        }
    }

    private static byte[] decrypt(byte[] key, byte[] ivAndCiphertext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, ivAndCiphertext, 0, 12));
            return cipher.doFinal(ivAndCiphertext, 12, ivAndCiphertext.length - 12);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("could not decrypt the secret", e);
        }
    }
}
