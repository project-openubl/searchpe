package io.github.projectopenubl.xhandler.tenancy;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Tenant isolation relies on the app never holding owner or superuser rights: row-level security
 * doesn't apply to the table owner, and superusers bypass it (DESIGN.md §6). In production the app
 * must log in as a non-owner role that is a member of {@code xhandler_app}; this refuses to start
 * otherwise. Dev and tests log in as a superuser and rely on {@link TenantRlsInterceptor}'s {@code
 * SET ROLE}.
 */
@ApplicationScoped
public class DatabaseRoleCheck {

    private final DataSource dataSource;
    private final boolean enabled;

    public DatabaseRoleCheck(DataSource dataSource, @ConfigProperty(name = "xhandler.db.check-login-role", defaultValue = "true") boolean enabled) {
        this.dataSource = dataSource;
        this.enabled = enabled;
    }

    void onStart(@Observes StartupEvent event) throws SQLException {
        if (!enabled || LaunchMode.current() != LaunchMode.NORMAL) {
            return;
        }
        // session_user is the login role, whatever SET ROLE the pool interceptor did
        String sql = """
                SELECT r.rolsuper, r.rolbypassrls,
                       EXISTS (SELECT FROM pg_tables WHERE schemaname = 'xhandler' AND tableowner = session_user),
                       pg_has_role(session_user, 'xhandler_app', 'MEMBER')
                FROM pg_roles r WHERE r.rolname = session_user""";
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery(sql)) {
            row.next();
            if (row.getBoolean(1) || row.getBoolean(2) || row.getBoolean(3) || !row.getBoolean(4)) {
                throw new IllegalStateException("The database login role must not be a superuser, bypass RLS or own the "
                        + "xhandler tables, and must be a member of xhandler_app (DESIGN.md §6). Run migrations with "
                        + "separate owner credentials (quarkus.flyway.username/password).");
            }
        }
    }
}
