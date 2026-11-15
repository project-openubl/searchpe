package io.github.projectopenubl.xhandler.dev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.projectopenubl.xhandler.engine.Engine;
import io.github.projectopenubl.xhandler.engine.EngineException;
import io.github.projectopenubl.xhandler.engine.EngineModels;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;

/** Engine stand-in for tests: records the calls and returns canned answers. */
@Mock
@ApplicationScoped
public class FakeEngine extends Engine {

    private final List<String> calls = new ArrayList<>();

    public FakeEngine() {
        super(null);
    }

    /** Calls received so far. Methods, not fields: tests reach this bean through a CDI proxy. */
    public List<String> calls() {
        return List.copyOf(calls);
    }

    public void reset() {
        calls.clear();
    }

    @Override
    public EngineModels.BuildResponse build(JsonNode document) {
        calls.add("build");
        if (document.path("spec").path("serie_numero").asText().isEmpty()) {
            throw new EngineException(422, "invalid_document", "serie_numero is required");
        }
        return new EngineModels.BuildResponse("<Invoice/>", JsonNodeFactory.instance.objectNode());
    }

    @Override
    public EngineModels.SignResponse sign(String xml) {
        calls.add("sign");
        return new EngineModels.SignResponse("<Invoice signed/>", "digest-123");
    }

    @Override
    public EngineModels.SendResponse send(String xml, EngineModels.Environment environment) {
        calls.add("send:" + environment.json());
        return new EngineModels.SendResponse(
                "accepted", "0", "La Factura numero F001-1, ha sido aceptada", List.of(), "UEsDBA==", null);
    }
}
