package io.github.projectopenubl.xhandler.engine;

import jakarta.ws.rs.GET;
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
    @Path("/validate")
    EngineModels.ValidateResponse validate(EngineModels.ValidateRequest request);

    @POST
    @Path("/inspect")
    EngineModels.InspectResponse inspect(EngineModels.InspectRequest request);

    @POST
    @Path("/sign")
    EngineModels.SignResponse sign(EngineModels.SignRequest request);

    @POST
    @Path("/sign/prepare")
    EngineModels.SignPrepareResponse signPrepare(EngineModels.SignPrepareRequest request);

    @POST
    @Path("/sign/finalize")
    EngineModels.SignResponse signFinalize(EngineModels.SignFinalizeRequest request);

    @POST
    @Path("/send")
    EngineModels.SendResponse send(EngineModels.SendRequest request);

    @POST
    @Path("/verify-ticket")
    EngineModels.SendResponse verifyTicket(EngineModels.VerifyTicketRequest request);

    @POST
    @Path("/status-cdr")
    EngineModels.StatusCdrResponse statusCdr(EngineModels.StatusCdrRequest request);

    @POST
    @Path("/validity")
    EngineModels.ValidityResponse validity(EngineModels.ValidityRequest request);

    @POST
    @Path("/ose/forward")
    EngineModels.OseForwardResponse oseForward(EngineModels.OseForwardRequest request);

    @POST
    @Path("/sire/proposal")
    EngineModels.SireTicketResponse sireProposal(EngineModels.SireProposalRequest request);

    @POST
    @Path("/sire/ticket")
    EngineModels.SireTicketStatus sireTicket(EngineModels.SireTicketRequest request);

    @POST
    @Path("/sire/replace")
    EngineModels.SireTicketResponse sireReplace(EngineModels.SireReplaceRequest request);

    @POST
    @Path("/conformity")
    EngineModels.ConformityResponse conformity(EngineModels.ConformityRequest request);

    @POST
    @Path("/validity/file")
    EngineModels.ValidityResponse validityFile(EngineModels.ValidityFileRequest request);

    @POST
    @Path("/cdr")
    EngineModels.XmlResponse cdr(EngineModels.CdrRequest request);

    @GET
    @Path("/info")
    EngineModels.InfoResponse info();
}
