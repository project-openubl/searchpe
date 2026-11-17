package io.github.projectopenubl.xhandler.engine;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Plays the Rust engine over real HTTP, answering with JSON in the engine's wire format, so tests
 * exercise the REST client, error mapping and snake_case (de)serialization. Records the requests.
 *
 * <p>Tests script answers per path with {@link #on}: this is the SUNAT fake of the app's tests
 * (any code, outages, delays, a dropped answer). Scripts are global: tests that script must reset
 * in {@code @AfterEach} and use documents only they touch.
 */
public class EngineStub implements QuarkusTestResourceLifecycleManager {

    /** A request the stub received. */
    public record Recorded(String path, String body) {}

    /** An answer: HTTP status and JSON body. */
    public record Reply(int status, String json) {
        public static Reply ok(String json) {
            return new Reply(200, json);
        }
    }

    private static final List<Recorded> REQUESTS = new CopyOnWriteArrayList<>();
    /** What the fake exchange-rate file answers. */
    public static final java.util.concurrent.atomic.AtomicReference<String> FX_LINE =
            new java.util.concurrent.atomic.AtomicReference<>("16/11/2026|3.380|3.390|");
    private static final Map<String, Function<String, Reply>> SCRIPTS = new ConcurrentHashMap<>();

    private HttpServer server;

    public static List<Recorded> requests() {
        return List.copyOf(REQUESTS);
    }

    /** Requests to {@code path} whose body contains {@code text}. */
    public static List<Recorded> requests(String path, String text) {
        return REQUESTS.stream().filter(r -> r.path().equals(path) && r.body().contains(text)).toList();
    }

    public static void reset() {
        REQUESTS.clear();
        SCRIPTS.clear();
    }

    /** Answers requests to {@code path} with {@code script} (given the request body) until reset. */
    public static void on(String path, Function<String, Reply> script) {
        SCRIPTS.put(path, script);
    }

    /** Body of the last request to {@code path}. */
    public static String lastBody(String path) {
        return REQUESTS.stream().filter(r -> r.path().equals(path)).reduce((a, b) -> b).map(Recorded::body).orElseThrow();
    }

    public static String sendAnswer(String status, Integer code, String codeClass, String ticket) {
        return "{\"status\":\"" + status + "\",\"code\":" + code + ",\"code_class\":" + quote(codeClass)
                + ",\"description\":\"respuesta " + status + "\",\"notes\":[],\"cdr_zip\":"
                + ("accepted".equals(status) || "rejected".equals(status) ? "\"UEsDBA==\"" : "null")
                + ",\"ticket\":" + quote(ticket) + "}";
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    @Override
    public Map<String, String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/v1/", this::handle);
        // SUNAT's exchange rate file (ExchangeRates job)
        server.createContext("/fx/", exchange -> respondText(exchange, FX_LINE.get()));
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return Map.of("quarkus.rest-client.engine.url", base, "xhandler.exchange-rates.url", base + "/fx/tipoCambio.txt");
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

        Function<String, Reply> script = SCRIPTS.get(path);
        if (script != null) {
            Reply reply = script.apply(body);
            if (reply == null) {
                // A dropped answer: close without responding
                exchange.close();
                return;
            }
            respond(exchange, reply.status(), reply.json());
            return;
        }
        switch (path) {
            case "/v1/build" -> build(exchange, body);
            case "/v1/validate" -> respond(exchange, 200, "{\"valid\":true,\"errors\":[],\"observations\":[]}");
            case "/v1/sign" -> {
                // Echoes the document with a signature, so the signed XML keeps its serie-número
                String xml = JSON.readTree(body).path("xml").asText() + "<ds:Signature/>";
                respond(exchange, 200, JSON.writeValueAsString(java.util.Map.of("xml", xml, "digest_value", "digest-" + Math.abs(xml.hashCode()))));
            }
            case "/v1/inspect" -> respond(exchange, 200, inspect(JSON.readTree(body).path("xml").asText()));
            // data_to_sign = base64("data-to-sign")
            case "/v1/sign/prepare" -> respond(exchange, 200, "{\"data_to_sign\":\"ZGF0YS10by1zaWdu\",\"digest_value\":\"digest-123\"}");
            case "/v1/sign/finalize" -> {
                String xml = JSON.readTree(body).path("xml").asText();
                String signed = xml.startsWith("<Invoice/>") || xml.isEmpty() ? "<Invoice signed/>" : xml + "<ds:Signature/>";
                respond(exchange, 200, JSON.writeValueAsString(java.util.Map.of("xml", signed, "digest_value", "digest-123")));
            }
            case "/v1/send" -> respond(exchange, 200, """
                    {"status":"accepted","code":0,"code_class":null,"description":"La Factura numero F001-1, ha sido aceptada",\
                    "notes":[],"cdr_zip":"UEsDBA==","ticket":null}""");
            case "/v1/verify-ticket" -> respond(exchange, 200, sendAnswer("accepted", 0, null, null));
            case "/v1/status-cdr" -> respond(exchange, 200, "{\"status\":\"not_found\",\"code\":\"0011\",\"description\":\"no existe\",\"cdr_zip\":null}");
            case "/v1/validity/file" -> respond(exchange, 200, "{\"state\":\"matches\",\"code\":\"0001\",\"description\":\"coincide\"}");
            case "/v1/validity" -> respond(exchange, 200, "{\"state\":\"valid\",\"code\":\"0001\",\"description\":\"válido\"}");
            case "/v1/info" -> respond(exchange, 200, "{\"engine_version\":\"0.0.0-stub\",\"xhandler_rust_rev\":\"stub\"}");
            default -> respond(exchange, 404, "{\"kind\":\"other\",\"message\":\"not found\"}");
        }
    }

    static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    /** {@code <Kind><cbc:ID>serie-numero</cbc:ID></Kind>}; enriched JSON is the document itself. */
    private static void build(HttpExchange exchange, String body) throws IOException {
        var document = JSON.readTree(body).path("document");
        var spec = document.path("spec");
        String kind = document.path("kind").asText();
        String id;
        if (kind.equals("SummaryDocuments") || kind.equals("VoidedDocuments") || kind.equals("Reversion")) {
            String prefix = kind.equals("SummaryDocuments") ? "RC" : kind.equals("VoidedDocuments") ? "RA" : "RR";
            id = prefix + "-" + spec.path("fecha_emision").asText().replace("-", "") + "-" + spec.path("numero").asText();
        } else if (spec.hasNonNull("serie_numero")) {
            id = spec.get("serie_numero").asText();
        } else {
            respond(exchange, 422, "{\"kind\":\"invalid_document\",\"message\":\"missing field `serie_numero`\"}");
            return;
        }
        var cliente = spec.path("cliente");
        String xml = "<" + kind + "><cbc:ID>" + id + "</cbc:ID><ruc>" + spec.path("proveedor").path("ruc").asText() + "</ruc>"
                + "<moneda>" + spec.path("moneda").asText("PEN") + "</moneda>"
                + "<cliente>" + cliente.path("tipo_documento_identidad").asText("6") + "|" + cliente.path("numero_documento_identidad").asText("")
                + "</cliente></" + kind + ">";
        var answer = JSON.createObjectNode();
        answer.put("xml", xml);
        answer.set("enriched_json", document);
        respond(exchange, 200, JSON.writeValueAsString(answer));
    }

    private static String inspect(String xml) throws IOException {
        int start = xml.indexOf("<cbc:ID>");
        String id = start < 0 ? null : xml.substring(start + 8, xml.indexOf("</cbc:ID>"));
        var answer = JSON.createObjectNode();
        answer.put("document_type", xml.substring(1, xml.indexOf('>')));
        answer.put("serie_numero", id);
        answer.put("moneda", between(xml, "<moneda>", "</moneda>", "PEN"));
        answer.put("total", "1180.00");
        String[] cliente = between(xml, "<cliente>", "</cliente>", "6|12121212121").split("\\|", -1);
        answer.put("customer_tipo", cliente[0]);
        answer.put("customer_numero", cliente.length > 1 ? cliente[1] : "");
        answer.put("signed", xml.contains("Signature"));
        answer.put("sha256", "0");
        return JSON.writeValueAsString(answer);
    }

    private static String between(String text, String open, String close, String fallback) {
        int a = text.indexOf(open);
        int b = text.indexOf(close);
        return a < 0 || b < 0 ? fallback : text.substring(a + open.length(), b);
    }

    private static void respondText(HttpExchange exchange, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
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
