package io.github.projectopenubl.xhandler.storage;

import io.quarkus.runtime.LaunchMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.nio.file.Path;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Chooses the file store: S3 in production, a local directory in development and tests. */
public class FileStoreProducer {

    @Produces
    @ApplicationScoped
    FileStore fileStore(
            @ConfigProperty(name = "xhandler.storage.kind", defaultValue = "s3") String kind,
            @ConfigProperty(name = "xhandler.storage.dir", defaultValue = "target/files") String dir,
            S3Config s3) {
        return switch (kind) {
            case "filesystem" -> {
                if (LaunchMode.current() == LaunchMode.NORMAL && !s3.allowFilesystemInProduction()) {
                    throw new IllegalStateException("xhandler.storage.kind=filesystem is for development and tests only");
                }
                yield new FilesystemFileStore(Path.of(dir));
            }
            case "s3" -> new S3FileStore(s3);
            default -> throw new IllegalStateException("unknown xhandler.storage.kind: " + kind);
        };
    }
}
