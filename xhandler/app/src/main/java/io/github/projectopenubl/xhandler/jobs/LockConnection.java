package io.github.projectopenubl.xhandler.jobs;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.jboss.logging.Logger;

/**
 * The worker pod's lock connection (DESIGN.md §10.4, §10.8). It holds the session advisory locks
 * of every job running in the pod and is never returned to a pool. The heartbeat runs on it, so a
 * live heartbeat proves the locks are held, and a broken connection stops the heartbeat.
 *
 * <p>Calls are serialized by one mutex, and only non-blocking {@code pg_try_advisory_*} calls are
 * made: a blocking call would stall every job behind the mutex.
 */
final class LockConnection implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(LockConnection.class);

    private final Connection connection;
    private final String worker;
    private final ReentrantLock mutex = new ReentrantLock();
    private final AtomicBoolean lost = new AtomicBoolean();

    LockConnection(Connection connection, String worker) {
        this.connection = connection;
        this.worker = worker;
    }

    boolean lost() {
        return lost.get();
    }

    /**
     * Registers the worker or refreshes its heartbeat. False when the connection broke: the locks
     * are gone. (Cleanup deletes a worker's row only when it runs nothing, so upserting is safe.)
     */
    boolean heartbeat(String role) {
        if (lost.get()) {
            return false;
        }
        mutex.lock();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO xhandler.worker (name, role) VALUES (?, ?)
                ON CONFLICT (name) DO UPDATE SET last_heartbeat = now()""")) {
            statement.setString(1, worker);
            statement.setString(2, role);
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            markLost(e);
            return false;
        } finally {
            mutex.unlock();
        }
    }

    /** Takes the job's ownership lock; false if someone else holds it or the connection is lost. */
    boolean tryLock(long seq) {
        if (lost.get()) {
            return false;
        }
        mutex.lock();
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            statement.setLong(1, seq);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getBoolean(1);
            }
        } catch (SQLException e) {
            markLost(e);
            return false;
        } finally {
            mutex.unlock();
        }
    }

    void unlock(long seq) {
        if (lost.get()) {
            return;
        }
        mutex.lock();
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            statement.setLong(1, seq);
            statement.executeQuery().close();
        } catch (SQLException e) {
            markLost(e);
        } finally {
            mutex.unlock();
        }
    }

    /** Closes the connection abruptly, as a network failure would (tests). */
    void breakConnection() {
        try {
            connection.abort(Runnable::run);
        } catch (SQLException e) {
            LOG.debug("abort failed", e);
        }
        lost.set(true);
    }

    private void markLost(SQLException e) {
        if (lost.compareAndSet(false, true)) {
            LOG.errorf(e, "worker %s lost its lock connection: its job locks are gone", worker);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            LOG.debug("close failed", e);
        }
    }
}
