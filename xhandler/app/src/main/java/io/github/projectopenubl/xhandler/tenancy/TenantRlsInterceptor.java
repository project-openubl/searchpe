package io.github.projectopenubl.xhandler.tenancy;

import io.agroal.api.AgroalPoolInterceptor;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Runs every pooled connection as the non-owner role {@code xhandler_app}, so PostgreSQL row-level
 * security applies (DESIGN.md §6). Until a transaction sets {@code app.tenant_id} (see {@link
 * TenantAccess}), queries see no rows: forgetting the tenant fails closed.
 *
 * <p>Flyway migrates at startup, before {@link StartupEvent}, as the connecting (owner) user.
 */
@ApplicationScoped
public class TenantRlsInterceptor implements AgroalPoolInterceptor {

    private static volatile boolean ready;

    void onStart(@Observes StartupEvent event) {
        ready = true;
    }

    @Override
    public void onConnectionAcquire(Connection connection) {
        if (!ready) {
            return;
        }
        execute(connection, "SET ROLE xhandler_app");
    }

    @Override
    public void onConnectionReturn(Connection connection) {
        if (!ready) {
            return;
        }
        execute(connection, "RESET app.tenant_id; RESET ROLE");
    }

    private static void execute(Connection connection, String sql) {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("could not set the database role", e);
        }
    }
}
