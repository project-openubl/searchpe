package io.github.projectopenubl.xhandler.admin.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/** HTTP client of xhandler-service's admin API, authenticated with an OIDC bearer token. */
public final class AdminClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final URI base;
    private final String token;

    public AdminClient(URI base, String token) {
        this.base = base;
        this.token = token;
    }

    /**
     * A token for a confidential client with the platform-admin role (client credentials). {@code
     * issuer} is the realm URL, e.g. {@code https://sso.example.com/realms/xhandler}.
     */
    public static String clientCredentialsToken(String issuer, String clientId, String clientSecret) {
        Map<String, String> form = Map.of("grant_type", "client_credentials", "client_id", clientId, "client_secret", clientSecret);
        return passwordlessToken(issuer + "/protocol/openid-connect/token", form);
    }

    static String passwordlessToken(String tokenUrl, Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        JsonNode answer = send(HttpClient.newHttpClient(), request);
        return answer.path("access_token").asText();
    }

    public JsonNode get(String path) {
        return send(http, authorized(path).GET().build());
    }

    public JsonNode post(String path, Object body) {
        return send(http, authorized(path).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(write(body))).build());
    }

    public JsonNode put(String path, Object body) {
        return send(http, authorized(path).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(write(body))).build());
    }

    private HttpRequest.Builder authorized(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofMinutes(10));
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private static JsonNode send(HttpClient http, HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = response.body().isBlank() ? JSON.nullNode() : JSON.readTree(response.body());
            if (response.statusCode() >= 400) {
                String message = body.path("message").asText(response.body());
                throw new CliException("HTTP " + response.statusCode() + ": " + message);
            }
            return body;
        } catch (IOException e) {
            throw new CliException("request to " + request.uri() + " failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CliException("interrupted");
        }
    }

    private static String write(Object body) {
        try {
            return body == null ? "{}" : JSON.writeValueAsString(body);
        } catch (IOException e) {
            throw new CliException(e.getMessage());
        }
    }

    /** A failure to report to the operator (exit code 1). */
    public static class CliException extends RuntimeException {
        public CliException(String message) {
            super(message);
        }
    }
}
