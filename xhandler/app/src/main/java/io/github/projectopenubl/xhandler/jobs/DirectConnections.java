package io.github.projectopenubl.xhandler.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import org.eclipse.microprofile.config.Config;

/**
 * Connections that never go through the pool: the worker's lock connection and the LISTEN
 * connection (DESIGN.md §10.8). They must reach PostgreSQL directly, never through a
 * transaction-pooling proxy such as PgBouncer ({@code xhandler.jobs.direct-jdbc-url} overrides the
 * datasource URL for that).
 */
@ApplicationScoped
public class DirectConnections {

    private final Config config;

    public DirectConnections(Config config) {
        this.config = config;
    }

    public Connection open(String applicationName) throws SQLException {
        String url = config.getOptionalValue("xhandler.jobs.direct-jdbc-url", String.class)
                .orElseGet(() -> config.getValue("quarkus.datasource.jdbc.url", String.class));
        Properties properties = new Properties();
        config.getOptionalValue("quarkus.datasource.username", String.class).ifPresent(u -> properties.setProperty("user", u));
        config.getOptionalValue("quarkus.datasource.password", String.class).ifPresent(p -> properties.setProperty("password", p));
        properties.setProperty("ApplicationName", applicationName);
        // Detect a dead peer: the heartbeat on this connection must fail when it is gone
        properties.setProperty("tcpKeepAlive", "true");
        properties.setProperty("socketTimeout", "30");
        Connection connection = DriverManager.getConnection(url, properties);
        connection.setAutoCommit(true);
        try (Statement statement = connection.createStatement()) {
            // Same rights as pooled connections (TenantRlsInterceptor)
            statement.execute("SET ROLE xhandler_app");
        }
        return connection;
    }
}
