package io.github.projectopenubl.xhandler.admin.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The CLI maps commands to the admin API with the bearer token. */
class XhandlerAdminTest {

    HttpServer server;
    final List<String> calls = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            calls.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " " + exchange.getRequestHeaders().getFirst("Authorization")
                    + " " + body);
            int status = exchange.getRequestURI().getPath().contains("missing") ? 404 : 200;
            byte[] answer = (status == 404 ? "{\"kind\":\"not_found\",\"message\":\"No encontrado.\"}" : "{\"ok\":true}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    int run(String... args) {
        String[] all = new String[args.length + 4];
        all[0] = "--url";
        all[1] = "http://127.0.0.1:" + server.getAddress().getPort();
        all[2] = "--token";
        all[3] = "t0k3n";
        System.arraycopy(args, 0, all, 4, args.length);
        return XhandlerAdmin.run(all);
    }

    @Test
    void commands_call_the_admin_api() {
        assertEquals(0, run("requeue-document", "d1"));
        assertEquals(0, run("hold", "company", "t1", "c1"));
        assertEquals(0, run("release", "endpoint", "production:invoice"));
        assertEquals(0, run("retry", "--tenant", "t1", "--status", "unknown"));
        assertEquals(0, run("endpoint-status", "--cap", "production:invoice", "20"));
        assertEquals(0, run("data-repair", "requeue-stuck"));
        assertEquals(0, run("purge-jobs", "--older-than-hours", "24"));

        assertEquals("POST /api/admin/v1/documents/d1/retry Bearer t0k3n {}", calls.get(0));
        assertTrue(calls.get(1).startsWith("POST /api/admin/v1/tenants/t1/companies/c1/hold"));
        assertTrue(calls.get(2).startsWith("POST /api/admin/v1/endpoints/production:invoice/release"));
        assertTrue(calls.get(3).contains("\"status\":\"unknown\""), calls.get(3));
        assertTrue(calls.get(4).startsWith("PUT /api/admin/v1/endpoints/production:invoice"));
        assertTrue(calls.get(4).contains("\"concurrency_cap\":20"));
        assertTrue(calls.get(5).startsWith("POST /api/admin/v1/repair/requeue-stuck"));
        assertTrue(calls.get(6).startsWith("POST /api/admin/v1/jobs/purge?older_than_hours=24"));
    }

    @Test
    void api_errors_exit_with_1() {
        assertEquals(1, run("requeue-document", "missing"));
        assertEquals(1, run("data-repair", "everything"));
    }
}
