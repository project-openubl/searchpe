package io.github.projectopenubl.xhandler.engine;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import io.github.projectopenubl.xhandler.api.ApiError;
import org.jboss.logging.Logger;

/**
 * Engine errors as API responses. Problems with the document or certificate are the client's (422,
 * with the engine's detail); anything else is ours (502, logged, no internal detail). Messages for
 * API clients are in Spanish (AGENTS.md).
 */
@Provider
public class EngineExceptionHandler implements ExceptionMapper<EngineException> {

    private static final Logger LOG = Logger.getLogger(EngineExceptionHandler.class);

    @Override
    public Response toResponse(EngineException e) {
        if (e.status() == 422) {
            return Response.status(422).entity(new ApiError(e.kind(), clientMessage(e.kind()), e.getMessage())).build();
        }
        LOG.errorf("engine error (HTTP %d, %s): %s", e.status(), e.kind(), e.getMessage());
        return Response.status(Response.Status.BAD_GATEWAY)
                .entity(new ApiError(e.kind(), "Error interno al procesar el documento. Intente nuevamente.", null))
                .build();
    }

    private static String clientMessage(String kind) {
        return switch (kind) {
            case "invalid_document" -> "El documento no es válido.";
            case "invalid_request" -> "La solicitud está incompleta o es inconsistente.";
            default -> "No se pudo procesar el documento.";
        };
    }
}
