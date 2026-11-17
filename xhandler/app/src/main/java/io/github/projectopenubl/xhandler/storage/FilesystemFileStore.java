package io.github.projectopenubl.xhandler.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** Files in a local directory: development and tests only (production uses S3). */
public class FilesystemFileStore implements FileStore {

    private final Path root;

    public FilesystemFileStore(Path root) {
        this.root = root;
    }

    @Override
    public Stored put(UUID tenantId, byte[] content) {
        String sha256 = FileStore.sha256(content);
        String key = FileStore.keyOf(tenantId, sha256);
        Path target = path(key);
        try {
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                Path temp = Files.createTempFile(target.getParent(), ".upload", null);
                Files.write(temp, content);
                try {
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (FileAlreadyExistsException | AtomicMoveNotSupportedException e) {
                    Files.deleteIfExists(temp);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Stored(key, sha256, content.length);
    }

    @Override
    public byte[] get(String key) {
        try {
            return Files.readAllBytes(path(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public java.util.List<String> list(String prefix) {
        if (!prefix.matches("tenant/[0-9a-f-]{36}/")) {
            throw new IllegalArgumentException("invalid storage prefix");
        }
        Path dir = root.resolve(prefix).normalize();
        if (!Files.isDirectory(dir)) {
            return java.util.List.of();
        }
        try (java.util.stream.Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).filter(f -> !f.getFileName().toString().startsWith(".upload"))
                    .map(f -> root.relativize(f).toString().replace('\\', '/')).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(path(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean healthy() {
        return Files.isWritable(root) || !Files.exists(root);
    }

    private Path path(String key) {
        if (!key.matches("tenant/[0-9a-f-]{36}/[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid storage key");
        }
        return root.resolve(key);
    }
}
