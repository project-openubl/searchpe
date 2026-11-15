package io.github.projectopenubl.xhandler.dev;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.projectopenubl.xhandler.engine.Engine;
import io.github.projectopenubl.xhandler.engine.EngineModels;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * P0 walking skeleton: builds, signs and sends a document to SUNAT beta in one synchronous call,
 * through the engine. Disabled unless {@code xhandler.dev-endpoints.enabled=true}. Replaced by
 * {@code POST /api/v1/documents} in P3 (DESIGN.md §11.1).
 */
@Path("/api/v1/dev/documents")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class DevDocumentResource {

    /** Result of the skeleton flow. */
    public record DevSendResult(String digestValue, EngineModels.SendResponse sunat) {}

    private final Engine engine;
    private final boolean enabled;

    public DevDocumentResource(
            Engine engine, @ConfigProperty(name = "xhandler.dev-endpoints.enabled") boolean enabled) {
        this.engine = engine;
        this.enabled = enabled;
    }

    @POST
    public DevSendResult buildSignSend(JsonNode document) {
        if (!enabled) {
            throw new NotFoundException();
        }
        EngineModels.BuildResponse built = engine.build(document);
        EngineModels.SignResponse signed = engine.sign(built.xml());
        EngineModels.SendResponse sunat = engine.send(signed.xml(), EngineModels.Environment.BETA);
        return new DevSendResult(signed.digestValue(), sunat);
    }
}
