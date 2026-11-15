package io.github.projectopenubl.xhandler.engine;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Engine errors on documents and certificates are the client's problem (422); anything else is
 * ours (502).
 */
@Provider
public class EngineExceptionHandler implements ExceptionMapper<EngineException> {

    @Override
    public Response toResponse(EngineException e) {
        int status = e.status() == 422 ? 422 : Response.Status.BAD_GATEWAY.getStatusCode();
        return Response.status(status).entity(new EngineModels.ErrorBody(e.kind(), e.getMessage())).build();
    }
}
