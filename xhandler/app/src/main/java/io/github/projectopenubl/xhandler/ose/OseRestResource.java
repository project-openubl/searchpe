package io.github.projectopenubl.xhandler.ose;

import io.github.projectopenubl.xhandler.api.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;

/**
 * The OSE's REST channel (DESIGN.md §5, §11.4): the same check as {@code sendBill}, with the OSE
 * user in HTTP Basic auth. {@code POST /api/v1/ose/documents} {@code {file_name, content_base64}}
 * (the ZIP, as for SOAP) answers the CDR-OSE or an inconsistency (422 with SUNAT's code).
 */
@Path("/api/v1/ose/documents")
public class OseRestResource {

    public record Submission(@NotBlank String fileName, @NotBlank String contentBase64) {}

    private final OseClients clients;
    private final OseIntake intake;

    public OseRestResource(OseClients clients, OseIntake intake) {
        this.clients = clients;
        this.intake = intake;
    }

    @Operation(operationId = "submitOseDocument")
    @POST
    public Response submit(@HeaderParam("Authorization") String authorization, @Valid Submission submission) {
        OseClients.Client client = authenticate(authorization);
        byte[] zip;
        try {
            zip = Base64.getDecoder().decode(submission.contentBase64());
        } catch (IllegalArgumentException e) {
            throw inconsistency("0156", "El archivo ZIP está corrupto.");
        }
        try {
            OseIntake.Cdr cdr = intake.sendBill(client, "rest", submission.fileName(), zip);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("result", "cdr");
            body.put("name", cdr.name());
            body.put("cdr_zip_base64", Base64.getEncoder().encodeToString(cdr.zip()));
            return Response.ok(body).build();
        } catch (OseIntake.Fault e) {
            throw inconsistency(e.code, e.getMessage());
        } catch (ZipGuard.Refused e) {
            throw inconsistency(e.code, e.getMessage());
        }
    }

    @Operation(operationId = "getOseCdr")
    @GET
    @Path("/{ruc}-{tipo}-{serie}-{numero}/cdr")
    public Response cdr(@HeaderParam("Authorization") String authorization, @PathParam("ruc") String ruc, @PathParam("tipo") String tipo,
            @PathParam("serie") String serie, @PathParam("numero") long numero) {
        OseClients.Client client = authenticate(authorization);
        return intake.cdrOf(client, ruc, tipo, serie.toUpperCase(), numero)
                .map(cdr -> Response.ok(cdr.zip()).type("application/zip")
                        .header("Content-Disposition", "attachment; filename=\"R-" + cdr.name() + ".zip\"").build())
                .orElseThrow(ApiException::notFound);
    }

    private OseClients.Client authenticate(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            throw new ApiException(401, "unauthorized", "Usuario o contraseña incorrectos.");
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(authorization.substring(6).strip()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new ApiException(401, "unauthorized", "Usuario o contraseña incorrectos.");
        }
        int colon = decoded.indexOf(':');
        if (colon < 0) {
            throw new ApiException(401, "unauthorized", "Usuario o contraseña incorrectos.");
        }
        try {
            return clients.authenticate(decoded.substring(0, colon), decoded.substring(colon + 1));
        } catch (OseClients.Unauthorized e) {
            throw new ApiException(401, "unauthorized", "Usuario o contraseña incorrectos.");
        }
    }

    private static ApiException inconsistency(String code, String message) {
        return ApiException.invalid("inconsistency", message, code);
    }
}
