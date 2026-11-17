package io.github.projectopenubl.xhandler.documents;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** A local webhook endpoint for tests: records what it receives and answers 204. */
public class WebhookReceiver implements AutoCloseable {

    public record Received(String body, String signature, String contentType) {}

    private final HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();

    public WebhookReceiver() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            received.add(new Received(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                    exchange.getRequestHeaders().getFirst("X-Xhandler-Signature"),
                    exchange.getRequestHeaders().getFirst("Content-Type")));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
    }

    public List<Received> received() {
        return List.copyOf(received);
    }

    public List<String> types() {
        return received.stream().map(r -> {
            int i = r.body().indexOf("\"type\":\"");
            return i < 0 ? "" : r.body().substring(i + 8, r.body().indexOf('"', i + 8));
        }).toList();
    }

    /** {@code t=<ts>,v1=<hex HMAC-SHA256(secret, "ts.body")>} */
    public boolean verifies(Received r, String secret) throws Exception {
        String[] parts = r.signature().split(",");
        String timestamp = parts[0].substring(2);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
        return ("v1=" + HexFormat.of().formatHex(mac.doFinal(r.body().getBytes(StandardCharsets.UTF_8)))).equals(parts[1]);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
