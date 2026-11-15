package io.github.projectopenubl.xhandler.engine;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/** Calls to the Rust engine. Stateless and thread-safe (DESIGN.md §10.8). */
@ApplicationScoped
public class Engine {

    private final EngineClient client;

    public Engine(@RestClient EngineClient client) {
        this.client = client;
    }

    public EngineModels.BuildResponse build(JsonNode document) {
        return client.build(new EngineModels.BuildRequest(document));
    }

    public EngineModels.SignResponse sign(String xml) {
        return client.sign(new EngineModels.SignRequest(xml));
    }

    public EngineModels.SendResponse send(String xml, EngineModels.Environment environment) {
        return client.send(new EngineModels.SendRequest(xml, environment));
    }
}
