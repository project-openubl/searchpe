package io.github.projectopenubl.xhandler.admin.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.net.URI;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * xhandler-admin (DESIGN.md §15): platform operations through the admin API. Authenticates as a
 * Keycloak client with the platform-admin role, or with a ready-made token.
 *
 * <pre>
 * export XHANDLER_URL=https://api.example.com
 * export XHANDLER_OIDC_ISSUER=https://sso.example.com/realms/xhandler
 * export XHANDLER_CLIENT_ID=xhandler-admin XHANDLER_CLIENT_SECRET=...
 * xhandler-admin retry --tenant 0190... --status unknown
 * </pre>
 */
@Command(
        name = "xhandler-admin",
        mixinStandardHelpOptions = true,
        version = "xhandler-admin 0.1.0",
        description = "Platform operations for xhandler-service.",
        subcommands = {
            XhandlerAdmin.Status.class,
            XhandlerAdmin.RotateKeys.class,
            XhandlerAdmin.RequeueDocument.class,
            XhandlerAdmin.Retry.class,
            XhandlerAdmin.Hold.class,
            XhandlerAdmin.Release.class,
            XhandlerAdmin.CancelJob.class,
            XhandlerAdmin.PurgeJobs.class,
            XhandlerAdmin.EndpointStatus.class,
            XhandlerAdmin.MigrationStatus.class,
            XhandlerAdmin.DataRepair.class,
            XhandlerAdmin.ResetAdmin.class,
        })
public class XhandlerAdmin {

    static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Option(names = "--url", defaultValue = "${env:XHANDLER_URL:-http://localhost:8080}", description = "Service URL")
    String url;

    @Option(names = "--token", defaultValue = "${env:XHANDLER_TOKEN}", description = "Bearer token (instead of client credentials)")
    String token;

    @Option(names = "--oidc-issuer", defaultValue = "${env:XHANDLER_OIDC_ISSUER}", description = "Keycloak realm URL")
    String issuer;

    @Option(names = "--client-id", defaultValue = "${env:XHANDLER_CLIENT_ID:-xhandler-admin}")
    String clientId;

    @Option(names = "--client-secret", defaultValue = "${env:XHANDLER_CLIENT_SECRET}")
    String clientSecret;

    AdminClient client() {
        String bearer = token;
        if ((bearer == null || bearer.isBlank()) && issuer != null && clientSecret != null) {
            bearer = AdminClient.clientCredentialsToken(issuer, clientId, clientSecret);
        }
        if (bearer == null || bearer.isBlank()) {
            throw new AdminClient.CliException("no credentials: set XHANDLER_TOKEN, or XHANDLER_OIDC_ISSUER and XHANDLER_CLIENT_SECRET");
        }
        return new AdminClient(URI.create(url), bearer);
    }

    static Integer print(JsonNode node) throws Exception {
        System.out.println(JSON.writeValueAsString(node));
        return 0;
    }

    abstract static class Base implements Callable<Integer> {
        @CommandLine.ParentCommand
        XhandlerAdmin root;
    }

    @Command(name = "status", description = "Versions, online workers, health and queue depth.")
    static class Status extends Base {
        @Override
        public Integer call() throws Exception {
            return print(new AdminClient(URI.create(root.url), root.token).get("/api/v1/status"));
        }
    }

    @Command(name = "rotate-keys", description = "Re-wrap every secret and signing key with the current master key.")
    static class RotateKeys extends Base {
        @Override
        public Integer call() throws Exception {
            return print(root.client().post("/api/admin/v1/keys/rotate", null));
        }
    }

    @Command(name = "requeue-document", description = "Attempt a document now (clears its hold; exception → resend).")
    static class RequeueDocument extends Base {
        @Parameters(paramLabel = "DOCUMENT_ID")
        String id;

        @Override
        public Integer call() throws Exception {
            return print(root.client().post("/api/admin/v1/documents/" + id + "/retry", null));
        }
    }

    @Command(name = "retry", description = "Retry one document, or a tenant's documents by filter.")
    static class Retry extends Base {
        @Parameters(paramLabel = "DOCUMENT_ID", arity = "0..1")
        String id;

        @Option(names = "--tenant")
        String tenant;

        @Option(names = "--company")
        String company;

        @Option(names = "--status", description = "pending_send, in_progress, unknown or exception")
        String status;

        @Option(names = "--from", description = "Issue date from (YYYY-MM-DD)")
        String from;

        @Option(names = "--to", description = "Issue date to (YYYY-MM-DD)")
        String to;

        @Override
        public Integer call() throws Exception {
            if (id != null) {
                return print(root.client().post("/api/admin/v1/documents/" + id + "/retry", null));
            }
            if (tenant == null) {
                throw new AdminClient.CliException("give a DOCUMENT_ID or --tenant");
            }
            Map<String, Object> filter = new LinkedHashMap<>();
            filter.put("company_id", company);
            filter.put("status", status);
            filter.put("from", from);
            filter.put("to", to);
            return print(root.client().post("/api/admin/v1/tenants/" + tenant + "/retry", filter));
        }
    }

    abstract static class Toggle extends Base {
        @Parameters(index = "0", description = "document, company, tenant or endpoint")
        String kind;

        @Parameters(index = "1..*", description = "document: ID; company: TENANT_ID COMPANY_ID; tenant: ID; endpoint: ID")
        java.util.List<String> ids;

        abstract String action();

        @Override
        public Integer call() throws Exception {
            String path = switch (kind) {
                case "document" -> "/api/admin/v1/documents/" + ids.get(0) + "/" + action();
                case "company" -> "/api/admin/v1/tenants/" + ids.get(0) + "/companies/" + ids.get(1) + "/" + action();
                case "tenant" -> "/api/admin/v1/tenants/" + ids.get(0) + "/" + action();
                case "endpoint" -> "/api/admin/v1/endpoints/" + ids.get(0) + "/" + action();
                default -> throw new AdminClient.CliException("unknown kind: " + kind);
            };
            return print(root.client().post(path, null));
        }
    }

    @Command(name = "hold", description = "Stop SUNAT attempts for a document, company, tenant or endpoint.")
    static class Hold extends Toggle {
        @Override
        String action() {
            return "hold";
        }
    }

    @Command(name = "release", description = "Resume SUNAT attempts for a document, company, tenant or endpoint.")
    static class Release extends Toggle {
        @Override
        String action() {
            return "release";
        }
    }

    @Command(name = "cancel-job", description = "Cancel a job (waiting: at once; running: at its next safe point).")
    static class CancelJob extends Base {
        @Parameters(paramLabel = "JOB_ID")
        String id;

        @Override
        public Integer call() throws Exception {
            return print(root.client().post("/api/admin/v1/jobs/" + id + "/cancel", null));
        }
    }

    @Command(name = "purge-jobs", description = "Delete finished jobs older than the given age.")
    static class PurgeJobs extends Base {
        @Option(names = "--older-than-hours", defaultValue = "168")
        int hours;

        @Override
        public Integer call() throws Exception {
            return print(root.client().post("/api/admin/v1/jobs/purge?older_than_hours=" + hours, null));
        }
    }

    @Command(name = "endpoint-status", description = "SUNAT endpoints: breaker, cap, hold. --cap ID N sets a cap.")
    static class EndpointStatus extends Base {
        @Option(names = "--cap", arity = "2", paramLabel = "ID N")
        java.util.List<String> cap;

        @Override
        public Integer call() throws Exception {
            if (cap != null) {
                return print(root.client().put("/api/admin/v1/endpoints/" + cap.get(0), Map.of("concurrency_cap", Integer.parseInt(cap.get(1)))));
            }
            return print(root.client().get("/api/admin/v1/endpoints"));
        }
    }

    @Command(name = "migration-status", description = "Applied and pending database migrations.")
    static class MigrationStatus extends Base {
        @Override
        public Integer call() throws Exception {
            return print(root.client().get("/api/admin/v1/migrations"));
        }
    }

    @Command(name = "data-repair", description = "Repairs: partitions (create missing ones), requeue-stuck (documents no attempt would pick up).")
    static class DataRepair extends Base {
        @Parameters(paramLabel = "TASK", description = "partitions | requeue-stuck")
        String task;

        @Override
        public Integer call() throws Exception {
            if (!task.equals("partitions") && !task.equals("requeue-stuck")) {
                throw new AdminClient.CliException("unknown repair task: " + task);
            }
            return print(root.client().post("/api/admin/v1/repair/" + task, null));
        }
    }

    /**
     * Resets a Keycloak user's password (e.g. a platform administrator locked out) with Keycloak's
     * admin API. The new password is temporary: it must be changed at the next login.
     */
    @Command(name = "reset-admin", description = "Reset a Keycloak user's password to a temporary one.")
    static class ResetAdmin extends Base {
        @Parameters(paramLabel = "USERNAME")
        String username;

        @Option(names = "--keycloak-url", defaultValue = "${env:KEYCLOAK_URL}", required = true)
        String keycloakUrl;

        @Option(names = "--realm", defaultValue = "${env:XHANDLER_REALM:-xhandler}")
        String realm;

        @Option(names = "--admin-user", defaultValue = "${env:KEYCLOAK_ADMIN}", required = true)
        String adminUser;

        @Option(names = "--admin-password", defaultValue = "${env:KEYCLOAK_ADMIN_PASSWORD}", required = true)
        String adminPassword;

        @Override
        public Integer call() throws Exception {
            String token = AdminClient.passwordlessToken(keycloakUrl + "/realms/master/protocol/openid-connect/token", Map.of(
                    "grant_type", "password", "client_id", "admin-cli", "username", adminUser, "password", adminPassword));
            AdminClient keycloak = new AdminClient(URI.create(keycloakUrl), token);
            JsonNode users = keycloak.get("/admin/realms/" + realm + "/users?exact=true&username="
                    + java.net.URLEncoder.encode(username, java.nio.charset.StandardCharsets.UTF_8));
            if (!users.isArray() || users.isEmpty()) {
                throw new AdminClient.CliException("no user " + username + " in realm " + realm);
            }
            String password = randomPassword();
            keycloak.put("/admin/realms/" + realm + "/users/" + users.get(0).path("id").asText() + "/reset-password",
                    Map.of("type", "password", "value", password, "temporary", true));
            System.out.println("Temporary password for " + username + ": " + password);
            return 0;
        }

        static String randomPassword() {
            String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
            SecureRandom random = new SecureRandom();
            StringBuilder password = new StringBuilder();
            for (int i = 0; i < 20; i++) {
                password.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            return password.toString();
        }
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String... args) {
        return new CommandLine(new XhandlerAdmin())
                .setExecutionExceptionHandler((e, cmd, parsed) -> {
                    cmd.getErr().println("error: " + e.getMessage());
                    return 1;
                })
                .execute(args);
    }
}
