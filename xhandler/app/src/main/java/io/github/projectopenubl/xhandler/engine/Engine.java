package io.github.projectopenubl.xhandler.engine;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import java.util.Base64;
import java.util.List;
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

    /** XSD and SUNAT rules in one call (DESIGN.md §4.1, R3/R5). */
    public EngineModels.ValidateResponse validate(String xml) {
        return call(() -> client.validate(new EngineModels.ValidateRequest(xml)));
    }

    /** Reads the identity of a UBL document (type, RUC, serie-número, totals, digest). */
    public EngineModels.InspectResponse inspect(String xml) {
        return call(() -> client.inspect(new EngineModels.InspectRequest(xml)));
    }

    /** Signs with SUNAT's public beta demo key (beta companies without their own certificate). */
    public EngineModels.SignResponse signWithDemoKey(String xml) {
        return call(() -> client.sign(new EngineModels.SignRequest(xml, true)));
    }

    public EngineModels.SignPrepareResponse signPrepare(String xml) {
        return call(() -> client.signPrepare(new EngineModels.SignPrepareRequest(xml)));
    }

    public EngineModels.SignResponse signFinalize(String xml, byte[] signatureValue, byte[] certificateDer) {
        Base64.Encoder base64 = Base64.getEncoder();
        return call(() -> client.signFinalize(new EngineModels.SignFinalizeRequest(
                xml, base64.encodeToString(signatureValue), base64.encodeToString(certificateDer))));
    }

    public EngineModels.SendResponse send(String xml, EngineModels.Environment environment) {
        return send(xml, environment, null, null);
    }

    public EngineModels.SendResponse send(
            String xml, EngineModels.Environment environment, EngineModels.Credentials credentials, EngineModels.Urls urls) {
        return call(() -> client.send(new EngineModels.SendRequest(xml, environment, credentials, urls)));
    }

    public EngineModels.SendResponse verifyTicket(
            String xml, String ticket, EngineModels.Environment environment, EngineModels.Credentials credentials,
            EngineModels.Urls urls) {
        return call(() -> client.verifyTicket(new EngineModels.VerifyTicketRequest(xml, ticket, environment, credentials, urls)));
    }

    public EngineModels.StatusCdrResponse statusCdr(
            EngineModels.Environment environment, EngineModels.Credentials credentials, EngineModels.DocumentKey document) {
        return call(() -> client.statusCdr(new EngineModels.StatusCdrRequest(environment, credentials, document)));
    }

    public EngineModels.ValidityResponse validity(EngineModels.ValidityRequest request) {
        return call(() -> client.validity(request));
    }

    public EngineModels.ValidityResponse validityFile(
            String xml, EngineModels.Environment environment, EngineModels.Credentials credentials) {
        return call(() -> client.validityFile(new EngineModels.ValidityFileRequest(xml, environment, credentials)));
    }

    /** Builds an unsigned OSE CDR for a received document (OSE role, R4). */
    public String cdr(String xml, String oseRuc, String responseCode, String description, List<EngineModels.CdrNote> notes,
            java.time.Instant issuedAt) {
        return call(() -> client.cdr(new EngineModels.CdrRequest(xml, oseRuc, responseCode, description, notes, issuedAt.toString())))
                .xml();
    }

    public EngineModels.InfoResponse info() {
        return call(client::info);
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
