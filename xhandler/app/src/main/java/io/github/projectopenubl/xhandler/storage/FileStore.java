package io.github.projectopenubl.xhandler.storage;

import java.util.HexFormat;
import java.util.UUID;

/**
 * Immutable, content-addressed file storage (DESIGN.md §13): files live under {@code
 * tenant/{tenantId}/{sha256}} and are never modified. Storing the same bytes twice is free, and an
 * orphan left by a rolled-back transaction is harmless. Implementations are thread-safe.
 */
public interface FileStore {

    record Stored(String key, String sha256, long size) {}

    Stored put(UUID tenantId, byte[] content);

    byte[] get(String key);

    /** Retention and exit deletions only (§13). */
    void delete(String key);

    /** Every key under a prefix (a tenant's {@code tenant/{id}/}): exit deletions find even unreferenced files. */
    java.util.List<String> list(String prefix);

    /** Whether the store answers (for /api/v1/status). */
    boolean healthy();

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String keyOf(UUID tenantId, String sha256) {
        return "tenant/" + tenantId + "/" + sha256;
    }
}
