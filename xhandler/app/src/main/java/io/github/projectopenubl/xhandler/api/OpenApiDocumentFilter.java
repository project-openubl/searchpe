package io.github.projectopenubl.xhandler.api;

import io.quarkus.smallrye.openapi.OpenApiFilter;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.Components;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.responses.APIResponse;
import org.eclipse.microprofile.openapi.models.responses.APIResponses;

/**
 * Completes the generated OpenAPI document (DESIGN.md §12) for SDK generators:
 *
 * <ul>
 *   <li>every operation under {@code /api/} except the public ones requires the bearer scheme;
 *   <li>{@link ApiError} is a reusable schema, referenced by the {@code 4XX} and {@code 5XX}
 *       responses of every operation under {@code /api/} (an OIDC {@code 401} has no body);
 * </ul>
 *
 * <p>Runs at build time, so the stored document ({@code target/openapi}) and {@code /q/openapi}
 * are the same.
 */
@OpenApiFilter(stages = OpenApiFilter.RunStage.BUILD)
public class OpenApiDocumentFilter implements OASFilter {

    static final String SECURITY_SCHEME = "bearer";
    static final String ERROR_SCHEMA = "ApiError";

    /**
     * Paths without login: keep in step with {@code quarkus.http.auth.permission.public.paths}.
     * The SOAP endpoints ({@code /ose/billService}, SUNAT's path) are outside {@code /api/} and
     * authenticate in the message (WS-Security).
     */
    static final List<String> PUBLIC_PREFIXES = List.of("/api/v1/status", "/api/v1/public/", "/api/v1/ose/");

    static boolean isPublic(String path) {
        return !path.startsWith("/api/") || PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        Components components = openAPI.getComponents();
        if (components == null) {
            components = OASFactory.createComponents();
            openAPI.setComponents(components);
        }
        components.addSchema(ERROR_SCHEMA, errorSchema());
        if (openAPI.getPaths() == null) {
            return;
        }
        for (Map.Entry<String, PathItem> path : openAPI.getPaths().getPathItems().entrySet()) {
            if (!path.getKey().startsWith("/api/")) {
                continue;
            }
            boolean secured = !isPublic(path.getKey());
            for (Operation operation : path.getValue().getOperations().values()) {
                if (secured) {
                    operation.setSecurity(List.of(OASFactory.createSecurityRequirement().addScheme(SECURITY_SCHEME)));
                }
                addErrorResponses(operation, secured);
            }
        }
    }

    private static void addErrorResponses(Operation operation, boolean secured) {
        APIResponses responses = operation.getResponses();
        if (responses == null) {
            responses = OASFactory.createAPIResponses();
            operation.setResponses(responses);
        }
        if (secured && !responses.hasAPIResponse("401")) {
            responses.addAPIResponse("401", OASFactory.createAPIResponse()
                    .description("Sin token o con un token inválido (sin cuerpo)."));
        }
        if (!responses.hasAPIResponse("4XX")) {
            responses.addAPIResponse("4XX", errorResponse("Error de la solicitud: `kind` estable, `message` en español."));
        }
        if (!responses.hasAPIResponse("5XX")) {
            responses.addAPIResponse("5XX", errorResponse("Error del servicio (p. ej. 502 cuando falla el motor)."));
        }
    }

    private static APIResponse errorResponse(String description) {
        return OASFactory.createAPIResponse()
                .description(description)
                .content(OASFactory.createContent().addMediaType("application/json", OASFactory.createMediaType()
                        .schema(OASFactory.createSchema().ref(ERROR_SCHEMA))));
    }

    private static Schema errorSchema() {
        return OASFactory.createSchema()
                .type(List.of(Schema.SchemaType.OBJECT))
                .description("Error de la API (AGENTS.md: `{ kind, message, detail }`).")
                .addProperty("kind", OASFactory.createSchema()
                        .type(List.of(Schema.SchemaType.STRING))
                        .description("Código estable del error, p. ej. `not_found`, `invalid_request`, `invalid_document`."))
                .addProperty("message", OASFactory.createSchema()
                        .type(List.of(Schema.SchemaType.STRING))
                        .description("Mensaje para personas, en español."))
                .addProperty("detail", OASFactory.createSchema()
                        .type(List.of(Schema.SchemaType.STRING, Schema.SchemaType.NULL))
                        .description("Detalle opcional (p. ej. el campo inválido o el código SUNAT)."))
                .required(List.of("kind", "message", "detail"));
    }
}
