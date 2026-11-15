package io.github.projectopenubl.xhandler.engine;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Plays the Rust engine over real HTTP, answering with JSON in the engine's wire format, so tests
 * exercise the REST client, error mapping and snake_case (de)serialization. Records the requests.
 */
public class EngineStub implements QuarkusTestResourceLifecycleManager {

    /** A request the stub received. */
    public record Recorded(String path, String body) {}

    private static final List<Recorded> REQUESTS = new CopyOnWriteArrayList<>();

    private HttpServer server;

    public static List<Recorded> requests() {
        return List.copyOf(REQUESTS);
    }

    public static void reset() {
        REQUESTS.clear();
    }

    @Override
    public Map<String, String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/v1/", this::handle);
        server.start();
        return Map.of("quarkus.rest-client.engine.url", "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        REQUESTS.add(new Recorded(path, body));

        switch (path) {
            case "/v1/build" -> {
                if (body.contains("\"serie_numero\"")) {
                    respond(exchange, 200, "{\"xml\":\"<Invoice/>\",\"enriched_json\":{\"kind\":\"Invoice\"}}");
                } else {
                    respond(exchange, 422, "{\"kind\":\"invalid_document\",\"message\":\"missing field `serie_numero`\"}");
                }
            }
            case "/v1/sign" -> respond(exchange, 200, "{\"xml\":\"<Invoice signed/>\",\"digest_value\":\"digest-123\"}");
            case "/v1/send" -> respond(exchange, 200, """
                    {"status":"accepted","code":0,"description":"La Factura numero F001-1, ha sido aceptada",\
                    "notes":[],"cdr_zip":"UEsDBA==","ticket":null}""");
            default -> respond(exchange, 404, "{\"kind\":\"other\",\"message\":\"not found\"}");
        }
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
