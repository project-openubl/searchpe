package io.github.projectopenubl.xhandler.keys;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** AES-256-GCM: output is {@code iv (12 bytes) || ciphertext+tag}. */
final class Aes {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Aes() {}

    static byte[] encrypt(SecretKey key, byte[] plaintext, Provider provider) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = cipher(provider);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    static byte[] decrypt(SecretKey key, byte[] ivAndCiphertext, Provider provider) {
        try {
            Cipher cipher = cipher(provider);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, ivAndCiphertext, 0, IV_BYTES));
            return cipher.doFinal(ivAndCiphertext, IV_BYTES, ivAndCiphertext.length - IV_BYTES);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM decryption failed", e);
        }
    }

    private static Cipher cipher(Provider provider) throws GeneralSecurityException {
        return provider == null ? Cipher.getInstance("AES/GCM/NoPadding") : Cipher.getInstance("AES/GCM/NoPadding", provider);
    }
}
