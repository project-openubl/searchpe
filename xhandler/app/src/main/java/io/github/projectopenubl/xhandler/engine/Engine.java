package io.github.projectopenubl.xhandler.engine;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import java.util.function.Supplier;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Calls to the Rust engine. Stateless and thread-safe (DESIGN.md §10.8). Errors come out as {@link
 * EngineException}, including the engine being unreachable or too slow.
 */
@ApplicationScoped
public class Engine {

    /** Kind of {@link EngineException} when the engine can't be reached or doesn't answer in time. */
    public static final String UNAVAILABLE = "engine_unavailable";

    private final EngineClient client;

    public Engine(@RestClient EngineClient client) {
        this.client = client;
    }

    public EngineModels.BuildResponse build(JsonNode document) {
        return call(() -> client.build(new EngineModels.BuildRequest(document)));
    }

    /** Signs with SUNAT's beta demo key (P0; real keys arrive with the HSM in P1). */
    public EngineModels.SignResponse signWithDemoKey(String xml) {
        return call(() -> client.sign(new EngineModels.SignRequest(xml, true)));
    }

    public EngineModels.SendResponse send(String xml, EngineModels.Environment environment) {
        return call(() -> client.send(new EngineModels.SendRequest(xml, environment)));
    }

    private static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (ProcessingException e) {
            // Connection refused, timeout, broken response. For /send this means the outcome is
            // unknown: SUNAT may have the document (DESIGN.md §9.2.1).
            throw new EngineException(502, UNAVAILABLE, e.getMessage());
        }
    }
}
