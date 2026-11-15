package io.github.projectopenubl.xhandler.engine;

import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;

/** Turns engine error responses into {@link EngineException}. */
public class EngineErrorMapper implements ResponseExceptionMapper<EngineException> {

    @Override
    public EngineException toThrowable(Response response) {
        String kind = "other";
        String message = "engine returned HTTP " + response.getStatus();
        try {
            EngineModels.ErrorBody body = response.readEntity(EngineModels.ErrorBody.class);
            if (body != null) {
                kind = body.kind();
                message = body.message();
            }
        } catch (RuntimeException ignored) {
            // Not a JSON error body; keep the generic message
        }
        return new EngineException(response.getStatus(), kind, message);
    }

    @Override
    public boolean handles(int status, jakarta.ws.rs.core.MultivaluedMap<String, Object> headers) {
        return status >= 400;
    }
}
