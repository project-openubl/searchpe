package io.github.projectopenubl.xhandler.dev;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.projectopenubl.xhandler.engine.Engine;
import io.github.projectopenubl.xhandler.engine.EngineModels;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * P0 walking skeleton: builds, signs (SUNAT beta demo key) and sends a document to SUNAT beta in one
 * synchronous call, through the engine.
 *
 * <p>Only compiled into builds with {@code xhandler.dev-endpoints.enabled=true} (dev mode, tests,
 * {@code hack/e2e.sh}); production images don't contain it. Replaced by {@code POST
 * /api/v1/documents} in P3 (DESIGN.md §11.1).
 */
@IfBuildProperty(name = "xhandler.dev-endpoints.enabled", stringValue = "true")
@Path("/api/v1/dev/documents")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class DevDocumentResource {

    /** Result of the skeleton flow. */
    public record DevSendResult(String digestValue, EngineModels.SendResponse sunat) {}

    private final Engine engine;

    public DevDocumentResource(Engine engine) {
        this.engine = engine;
    }

    @POST
    public DevSendResult buildSignSend(JsonNode document) {
        EngineModels.BuildResponse built = engine.build(document);
        EngineModels.SignResponse signed = engine.signWithDemoKey(built.xml());
        EngineModels.SendResponse sunat = engine.send(signed.xml(), EngineModels.Environment.BETA);
        return new DevSendResult(signed.digestValue(), sunat);
    }
}
