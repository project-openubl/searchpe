package io.github.projectopenubl.xhandler.storage;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.Optional;

/** S3-compatible storage (AWS S3, MinIO, Ceph...). Path-style URLs: {@code endpoint/bucket/key}. */
@ConfigMapping(prefix = "xhandler.storage.s3")
public interface S3Config {

    @WithDefault("http://localhost:9000")
    String endpoint();

    @WithDefault("xhandler")
    String bucket();

    @WithDefault("us-east-1")
    String region();

    Optional<String> accessKey();

    Optional<String> secretKey();

    @WithDefault("10s")
    Duration timeout();

    /** e2e only: lets a packaged (production-mode) build use the filesystem store. */
    @WithDefault("false")
    boolean allowFilesystemInProduction();
}
