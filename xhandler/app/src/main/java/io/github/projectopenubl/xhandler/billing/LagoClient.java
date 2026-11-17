package io.github.projectopenubl.xhandler.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Lago's API (DESIGN.md §14), a separate deployment: usage events in, subscriptions (plans) out.
 * The tenant id is Lago's external customer id and external subscription id.
 */
@ApplicationScoped
public class LagoClient {

    private final Optional<String> url;
    private final Optional<String> apiKey;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public LagoClient(@ConfigProperty(name = "xhandler.lago.url") Optional<String> url,
            @ConfigProperty(name = "xhandler.lago.api-key") Optional<String> apiKey, ObjectMapper json) {
        this.url = url.map(u -> u.replaceAll("/+$", ""));
        this.apiKey = apiKey;
        this.json = json;
    }

    public boolean configured() {
        return url.isPresent() && apiKey.isPresent();
    }

    /** Sends a batch of usage events; Lago deduplicates by transaction_id. */
    public void sendEvents(List<Map<String, Object>> events) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(request("/api/v1/events/batch")
                        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("events", events)))).build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Lago answered HTTP " + response.statusCode() + " to the usage batch");
        }
    }

    /** The plan code of the tenant's active subscription, or empty if it has none. */
    public Optional<String> activePlan(String customerId) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(request("/api/v1/subscriptions?status[]=active&external_customer_id="
                + URLEncoder.encode(customerId, StandardCharsets.UTF_8)).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Lago answered HTTP " + response.statusCode() + " to the subscriptions query");
        }
        JsonNode subscriptions = json.readTree(response.body()).path("subscriptions");
        for (JsonNode subscription : subscriptions) {
            if ("active".equals(subscription.path("status").asText())) {
                return Optional.of(subscription.path("plan_code").asText());
            }
        }
        return Optional.empty();
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(url.orElseThrow() + path))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + apiKey.orElseThrow())
                .header("Content-Type", "application/json");
    }
}
