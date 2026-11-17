package io.github.projectopenubl.xhandler.jobs;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The pool covers concurrent emits (two connections each: the emit transaction and the signature
 * log), concurrent jobs and headroom for reads (DESIGN.md §10.8). The worker's lock and listener
 * connections are outside the pool. Refuses to start otherwise: an undersized pool deadlocks under
 * a burst instead of degrading.
 */
@ApplicationScoped
public class PoolSizeCheck {

    static final int HEADROOM = 8;

    private final int maxSize;
    private final int emitPermits;
    private final JobConfig jobs;

    public PoolSizeCheck(
            @ConfigProperty(name = "quarkus.datasource.jdbc.max-size", defaultValue = "20") int maxSize,
            @ConfigProperty(name = "xhandler.emit.permits", defaultValue = "16") int emitPermits,
            JobConfig jobs) {
        this.maxSize = maxSize;
        this.emitPermits = emitPermits;
        this.jobs = jobs;
    }

    void onStart(@Observes StartupEvent event) {
        int needed = required(emitPermits, jobs.slots());
        if (maxSize < needed) {
            throw new IllegalStateException("quarkus.datasource.jdbc.max-size=" + maxSize + " is below " + needed
                    + " (2 × xhandler.emit.permits + xhandler.jobs.slots + " + HEADROOM + "): raise the pool or lower the permits");
        }
    }

    static int required(int emitPermits, int jobSlots) {
        return 2 * emitPermits + jobSlots + HEADROOM;
    }
}
