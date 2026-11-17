package io.github.projectopenubl.xhandler.jobs;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

/**
 * The worker role (DESIGN.md §10): claims and runs jobs, and runs the singleton activities
 * (unblocker, reconciler, schedules, cleanup) when it wins their advisory lock.
 *
 * <p>This is the one stateful component of the app: it owns threads and two direct connections
 * (lock and listener). All shared state is thread-safe (atomics, semaphores, the mutex-guarded
 * {@link LockConnection}); job data lives only in PostgreSQL.
 */
@ApplicationScoped
public class WorkerRuntime {

    private static final Logger LOG = Logger.getLogger(WorkerRuntime.class);

    private final JobConfig config;
    private final JobStore store;
    private final Singletons singletons;
    private final DirectConnections connections;
    private final Map<String, JobHandler> handlers;
    private final String role;
    private final Timer queueWait;

    private final String name;
    private final Semaphore slots;
    private final Semaphore wakeups = new Semaphore(0);
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean claiming = new AtomicBoolean();
    private final AtomicBoolean reconcileRequested = new AtomicBoolean(true);
    private final AtomicReference<LockConnection> lock = new AtomicReference<>();
    /** Each start is a new generation; loops of an older one exit. */
    private final java.util.concurrent.atomic.AtomicInteger generation = new java.util.concurrent.atomic.AtomicInteger();
    private final AtomicReference<java.util.concurrent.ScheduledFuture<?>> heartbeatTask = new AtomicReference<>();
    private final ExecutorService jobThreads = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("job-", 0).factory());
    private final ScheduledExecutorService heartbeats =
            Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("job-heartbeat").daemon().factory());
    private final Map<String, Instant> lastRun = new java.util.concurrent.ConcurrentHashMap<>();

    public WorkerRuntime(
            JobConfig config,
            JobStore store,
            Singletons singletons,
            DirectConnections connections,
            Instance<JobHandler> handlers,
            MeterRegistry metrics,
            @ConfigProperty(name = "xhandler.role", defaultValue = "all") String role) {
        this.config = config;
        this.store = store;
        this.singletons = singletons;
        this.connections = connections;
        Map<String, JobHandler> byName = new HashMap<>();
        for (JobHandler handler : handlers) {
            if (byName.put(handler.name(), handler) != null) {
                throw new IllegalStateException("two handlers for job " + handler.name());
            }
        }
        this.handlers = Map.copyOf(byName);
        this.role = role;
        this.slots = new Semaphore(config.slots());
        this.name = hostName() + "-" + UUID.randomUUID().toString().substring(0, 8);
        this.queueWait = Timer.builder("xhandler.jobs.queue.wait")
                .description("Time from claim to run (unblocked, waiting for a slot)")
                .register(metrics);
    }

    public String name() {
        return name;
    }

    public boolean enabled() {
        return "worker".equals(role) || "all".equals(role);
    }

    void onStart(@Observes @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 100) StartupEvent event) {
        if (enabled()) {
            start();
        }
    }

    void onStop(@Observes ShutdownEvent event) {
        stop(Duration.ofSeconds(120));
    }

    /** Starts claiming jobs. */
    public synchronized void start() {
        if (running.get()) {
            return;
        }
        try {
            LockConnection connection = new LockConnection(connections.open("xhandler-lock-" + name), name);
            if (!connection.heartbeat(role)) {
                throw new IllegalStateException("could not register worker " + name);
            }
            lock.set(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("could not open the lock connection", e);
        }
        running.set(true);
        claiming.set(true);
        int current = generation.incrementAndGet();
        long interval = config.heartbeatInterval().toMillis();
        heartbeatTask.set(heartbeats.scheduleWithFixedDelay(this::heartbeat, interval, interval, TimeUnit.MILLISECONDS));
        Thread.ofPlatform().name("job-dispatcher").daemon().start(() -> loop(current));
        Thread.ofPlatform().name("job-listener").daemon().start(() -> listen(current));
        LOG.infof("worker %s started with %d slots", name, config.slots());
    }

    /** Stops claiming, lets running jobs finish (up to {@code grace}), and releases the connections. */
    public synchronized void stop(Duration grace) {
        if (!running.getAndSet(false)) {
            return;
        }
        claiming.set(false);
        generation.incrementAndGet();
        cancelHeartbeat();
        wakeups.release();
        awaitRunningJobs(grace);
        LockConnection connection = lock.getAndSet(null);
        if (connection != null) {
            connection.close();
        }
        LOG.infof("worker %s stopped", name);
    }

    /** Wakes the dispatcher now (after enqueueing work in this process). */
    public void wakeUp() {
        reconcileRequested.set(true);
        wakeups.release();
    }

    /** Simulates losing the lock connection (chaos tests). */
    public void breakLockConnection() {
        LockConnection connection = lock.get();
        if (connection != null) {
            connection.breakConnection();
        }
    }

    public boolean claimingJobs() {
        return claiming.get();
    }

    public boolean running() {
        return running.get();
    }

    private void heartbeat() {
        LockConnection connection = lock.get();
        if (connection != null && !connection.heartbeat(role)) {
            onLockLost();
        }
    }

    private boolean live(int current) {
        return running.get() && generation.get() == current;
    }

    private void cancelHeartbeat() {
        var task = heartbeatTask.getAndSet(null);
        if (task != null) {
            task.cancel(false);
        }
    }

    private void loop(int current) {
        while (live(current)) {
            try {
                LockConnection connection = lock.get();
                if (connection == null || connection.lost()) {
                    onLockLost();
                    return;
                }
                singletonPass();
                claim(connection);
                if (wakeups.tryAcquire(config.pollInterval().toMillis(), TimeUnit.MILLISECONDS)) {
                    wakeups.drainPermits();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                LOG.error("job dispatcher pass failed", e);
                sleep(config.pollInterval());
            }
        }
    }

    private void singletonPass() {
        if (due("reconcile", config.reconcileInterval()) || reconcileRequested.getAndSet(false)) {
            QuarkusTransaction.requiringNew().run(() -> {
                if (singletons.tryLock(Singletons.RECONCILE)) {
                    JobConfig.Breaker breaker = config.breaker();
                    store.updateBreakers(breaker.minCalls(), breaker.baseOpen(), breaker.maxOpen());
                    store.reconcile(config.lease(), config.credentialSlots());
                }
            });
        }
        if (due("schedules", config.scheduleInterval())) {
            QuarkusTransaction.requiringNew().run(() -> {
                if (singletons.tryLock(Singletons.SCHEDULE_DISPATCH)) {
                    store.dispatchSchedules();
                }
            });
        }
        if (due("cleanup", config.cleanupInterval())) {
            cleanup();
        }
        QuarkusTransaction.requiringNew().run(() -> {
            if (singletons.tryLock(Singletons.UNBLOCK)) {
                store.unblock();
            }
        });
    }

    /**
     * Recovery (§10.4): fails the running jobs of workers whose heartbeat is stale and whose job
     * locks can be taken, i.e. whose lock connection is gone.
     */
    private void cleanup() {
        QuarkusTransaction.requiringNew().run(() -> {
            if (!singletons.tryLock(Singletons.WORKER_CLEANUP)) {
                return;
            }
            for (JobStore.DeadJob job : store.jobsOfDeadWorkers(config.workerTtl())) {
                if (store.tryJobLockForTransaction(job.seq()) && store.recover(job.id())) {
                    LOG.warnf("recovered job %s of dead worker %s", job.id(), job.worker());
                }
            }
            store.deleteDeadWorkers(config.workerTtl().multipliedBy(4));
        });
    }

    private void claim(LockConnection connection) {
        if (!claiming.get()) {
            return;
        }
        int free = slots.availablePermits();
        if (free <= 0) {
            return;
        }
        List<JobStore.Started> started = new ArrayList<>();
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                for (JobStore.Candidate candidate : store.claimable(free)) {
                    // The ownership lock comes before the compare-and-set, as in pulpcore
                    if (!connection.tryLock(candidate.seq())) {
                        continue;
                    }
                    var job = store.start(candidate, name);
                    if (job.isPresent()) {
                        started.add(job.get());
                    } else {
                        connection.unlock(candidate.seq());
                    }
                }
            });
        } catch (RuntimeException e) {
            // The transaction rolled back: the jobs are still waiting, release their locks
            started.forEach(job -> connection.unlock(job.seq()));
            throw e;
        }
        for (JobStore.Started job : started) {
            slots.acquireUninterruptibly();
            Instant claimedAt = Instant.now();
            jobThreads.submit(() -> run(job, connection, claimedAt));
        }
    }

    private void run(JobStore.Started job, LockConnection connection, Instant claimedAt) {
        queueWait.record(Duration.between(claimedAt, Instant.now()));
        MDC.put("job_id", job.id().toString());
        if (job.correlationId() != null) {
            MDC.put("correlation_id", job.correlationId());
        }
        try {
            JobHandler handler = handlers.get(job.name());
            Map<String, Object> result;
            if (handler == null) {
                throw new IllegalStateException("no handler for job " + job.name());
            }
            JobContext context = new JobContext(job.id(), job.tenantId(), job.name(), job.attempt(), job.args(),
                    job.correlationId(), () -> QuarkusTransaction.requiringNew().call(() -> store.cancelRequested(job.id())));
            result = handler.run(context);
            finish(job, "completed", result, null);
        } catch (Throwable e) {
            LOG.errorf(e, "job %s (%s) failed", job.id(), job.name());
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            finish(job, "failed", null, Map.of("kind", e.getClass().getSimpleName(), "message", message));
        } finally {
            connection.unlock(job.seq());
            slots.release();
            wakeups.release();
            MDC.remove("job_id");
            MDC.remove("correlation_id");
        }
    }

    private void finish(JobStore.Started job, String state, Map<String, Object> result, Map<String, Object> error) {
        try {
            QuarkusTransaction.requiringNew().run(() -> store.finish(job.id(), name, state, result, error));
        } catch (RuntimeException e) {
            // The job stays running; recovery fails it once this worker is gone
            LOG.errorf(e, "could not record the end of job %s", job.id());
        }
    }

    /**
     * The locks are gone (§10.4): stop claiming, let running jobs finish and write their (fenced)
     * outcomes, then exit so Kubernetes restarts the pod.
     */
    private void onLockLost() {
        if (!claiming.getAndSet(false)) {
            return;
        }
        LOG.error("lock connection lost: stopping job claims");
        cancelHeartbeat();
        Thread.ofPlatform().name("job-lock-lost").start(() -> {
            awaitRunningJobs(Duration.ofSeconds(130));
            synchronized (this) {
                running.set(false);
                generation.incrementAndGet();
                LockConnection connection = lock.getAndSet(null);
                if (connection != null) {
                    connection.close();
                }
            }
            if (config.exitOnLockLoss()) {
                LOG.error("exiting after losing the lock connection");
                Quarkus.asyncExit(1);
            }
        });
    }

    private void awaitRunningJobs(Duration grace) {
        try {
            if (slots.tryAcquire(config.slots(), grace.toMillis(), TimeUnit.MILLISECONDS)) {
                slots.release(config.slots());
            } else {
                LOG.warnf("jobs still running after %s", grace);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** LISTEN for wake-ups on a dedicated connection; reconnects on failure (polling covers gaps). */
    private void listen(int current) {
        while (live(current)) {
            try (Connection connection = connections.open("xhandler-listen-" + name)) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("LISTEN xhandler_job_wakeup; LISTEN xhandler_job_cancel; LISTEN xhandler_reconcile");
                }
                PGConnection pg = connection.unwrap(PGConnection.class);
                while (live(current)) {
                    PGNotification[] notifications = pg.getNotifications(1000);
                    if (notifications == null) {
                        continue;
                    }
                    for (PGNotification notification : notifications) {
                        if ("xhandler_reconcile".equals(notification.getName())) {
                            reconcileRequested.set(true);
                        }
                    }
                    if (notifications.length > 0) {
                        wakeups.release();
                    }
                }
            } catch (SQLException e) {
                if (live(current)) {
                    LOG.warn("listener connection failed; reconnecting", e);
                    sleep(Duration.ofSeconds(2));
                }
            }
        }
    }

    private boolean due(String activity, Duration interval) {
        Instant now = Instant.now();
        Instant last = lastRun.get(activity);
        if (last == null || !now.isBefore(last.plus(interval))) {
            lastRun.put(activity, now);
            return true;
        }
        return false;
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String hostName() {
        String host = System.getenv("HOSTNAME");
        if (host != null && !host.isBlank()) {
            return host;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (java.io.IOException e) {
            return "worker";
        }
    }
}
