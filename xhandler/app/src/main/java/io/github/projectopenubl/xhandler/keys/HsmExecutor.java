package io.github.projectopenubl.xhandler.keys;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Runs key-vault calls on a bounded pool of platform threads sized to the HSM session pool
 * (DESIGN.md §10.8). SunPKCS11 is JNI, which pins virtual threads; keeping HSM work here means a
 * slow HSM degrades signing only, never the request threads. Calls time out.
 */
@ApplicationScoped
public class HsmExecutor {

    private final ExecutorService pool;
    private final long timeoutMillis;

    public HsmExecutor(
            @ConfigProperty(name = "xhandler.hsm.sessions", defaultValue = "8") int sessions,
            @ConfigProperty(name = "xhandler.hsm.timeout", defaultValue = "10s") java.time.Duration timeout) {
        this.pool = new ThreadPoolExecutor(sessions, sessions, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(sessions * 64),
                Thread.ofPlatform().name("hsm-", 0).daemon().factory());
        this.timeoutMillis = timeout.toMillis();
    }

    public <T> T call(Callable<T> work) {
        Future<T> future;
        try {
            future = pool.submit(work);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            throw new ApiException(503, "busy", "El servicio de firma está ocupado. Intente nuevamente.");
        }
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ApiException(503, "hsm_timeout", "El servicio de firma no respondió a tiempo. Intente nuevamente.");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    void onStop(@Observes ShutdownEvent event) {
        pool.shutdown();
    }
}
