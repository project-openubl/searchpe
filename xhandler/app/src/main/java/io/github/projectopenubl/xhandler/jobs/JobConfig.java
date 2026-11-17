package io.github.projectopenubl.xhandler.jobs;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.Map;

/** Job system settings (DESIGN.md §10). */
@ConfigMapping(prefix = "xhandler.jobs")
public interface JobConfig {

    /** Jobs run at once by one worker pod. The unblocker never hands out more. */
    @WithDefault("16")
    int slots();

    @WithDefault("5s")
    Duration heartbeatInterval();

    /** A worker whose heartbeat is older than this is dead (if its locks can be taken). */
    @WithDefault("30s")
    Duration workerTtl();

    /** Longest sleep between passes when no NOTIFY arrives. */
    @WithDefault("1s")
    Duration pollInterval();

    @WithDefault("1s")
    Duration reconcileInterval();

    @WithDefault("5s")
    Duration scheduleInterval();

    @WithDefault("10s")
    Duration cleanupInterval();

    /** How long the reconciler's lease keeps a document from looking due (DESIGN.md §24.2). */
    @WithDefault("5m")
    Duration lease();

    /** Finished jobs are kept this long (DESIGN.md §10.4). */
    @WithDefault("7d")
    Duration purgeAge();

    /** Exit the process when the lock connection breaks, so Kubernetes restarts it (§10.4). */
    @WithDefault("true")
    boolean exitOnLockLoss();

    /**
     * Running jobs per SUNAT credential, by environment (DESIGN.md §10.2). Production's value is an
     * assumption to measure: SUNAT publishes no limit. Defaults in application.properties.
     */
    Map<String, Integer> credentialSlots();

    Breaker breaker();

    interface Breaker {
        /** Calls in the last 30 seconds before the breaker may open. */
        @WithDefault("5")
        int minCalls();

        @WithDefault("30s")
        Duration baseOpen();

        @WithDefault("5m")
        Duration maxOpen();
    }
}
