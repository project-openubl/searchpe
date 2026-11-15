package io.github.projectopenubl.xhandler.engine;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * HTTP client of the Rust engine sidecar (DESIGN.md §4.1). The engine runs in the same pod and is
 * reached over localhost; OpenTelemetry propagates the trace context on every call.
 */
@RegisterRestClient(configKey = "engine")
@RegisterProvider(EngineErrorMapper.class)
@Path("/v1")
public interface EngineClient {

    @POST
    @Path("/build")
    EngineModels.BuildResponse build(EngineModels.BuildRequest request);

    @POST
    @Path("/sign")
    EngineModels.SignResponse sign(EngineModels.SignRequest request);

    @POST
    @Path("/send")
    EngineModels.SendResponse send(EngineModels.SendRequest request);
}
