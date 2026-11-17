package io.github.projectopenubl.xhandler.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.documents.Document;
import io.github.projectopenubl.xhandler.documents.DocumentView;
import io.github.projectopenubl.xhandler.documents.EmitService;
import io.github.projectopenubl.xhandler.documents.VoidingService;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/**
 * Nubefact-compatible API (DESIGN.md §5): {@code POST /api/v1/public/nubefact/{company}} with
 * {@code Authorization: Token token="…"} (or the bare token), operations {@code generar_comprobante},
 * {@code consultar_comprobante}, {@code generar_anulacion} and {@code consultar_anulacion}. Answers
 * in Nubefact's shape. Documents keep the client's numbering. Sending is asynchronous: the first
 * answer has {@code aceptada_por_sunat: false} until SUNAT answers; clients poll with
 * {@code consultar_comprobante}. Calling {@code generar_anulacion} attests the buyer doesn't have it.
 */
@Path("/api/v1/public/nubefact/{companyId}")
public class NubefactResource {

    private final EntityManager em;
    private final TenantAccess access;
    private final EmitService emit;
    private final VoidingService voiding;
    private final ObjectMapper json;

    public NubefactResource(EntityManager em, TenantAccess access, EmitService emit, VoidingService voiding, ObjectMapper json) {
        this.em = em;
        this.access = access;
        this.emit = emit;
        this.voiding = voiding;
        this.json = json;
    }

    record Client(UUID tenantId, UUID companyId, String sunatEnv, UUID tokenId, String label) {}

    @APIResponse(responseCode = "200", description = "Respuesta con el formato de Nubefact (objeto libre).",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = JsonNode.class)))
    @Operation(operationId = "callNubefact")
    @POST
    public Response call(@PathParam("companyId") UUID companyId, @HeaderParam("Authorization") String authorization, JsonNode request) {
        Client client = authenticate(companyId, authorization);
        String operacion = request == null ? "" : request.path("operacion").asText("generar_comprobante");
        try {
            return dispatch(client, operacion, request);
        } catch (java.time.format.DateTimeParseException e) {
            throw ApiException.invalid("invalid_document", "Fecha inválida: use DD-MM-AAAA.", e.getParsedString());
        } catch (NumberFormatException e) {
            throw ApiException.invalid("invalid_document", "Número inválido en el comprobante.", e.getMessage());
        }
    }

    private Response dispatch(Client client, String operacion, JsonNode request) {
        return switch (operacion) {
            case "generar_comprobante" -> generate(client, request);
            case "consultar_comprobante" -> Response.ok(NubefactAdapter.answer(find(client, request), null, null, null)).build();
            case "generar_anulacion" -> annul(client, request);
            case "consultar_anulacion" -> {
                JsonNode view = find(client, request);
                yield Response.ok(Map.of("numero", view.path("numero").asLong(), "anulado", "voided".equals(view.path("status").asText()),
                        "estado_xhandler", view.path("status").asText())).build();
            }
            default -> throw ApiException.invalid("invalid_request", "operacion desconocida: " + operacion, null);
        };
    }

    private Response generate(Client client, JsonNode request) {
        NubefactAdapter.Converted converted = NubefactAdapter.convert(request);
        // Per company and environment: two companies (or beta and production) may share a number
        String key = "nubefact:" + client.companyId() + ":" + client.sunatEnv() + ":" + converted.tipo() + ":" + converted.serie() + ":"
                + converted.numero();
        EmitService.Emitted emitted = emit.emitNumbered(client.tenantId(), client.companyId(), key,
                new EmitService.EmitRequest(converted.document(), converted.serie(), null, Map.of("adapter", "nubefact")),
                converted.numero(), UUID.randomUUID().toString(), true);
        return Response.ok(NubefactAdapter.answer(emitted.body(), null, null, null)).build();
    }

    private Response annul(Client client, JsonNode request) {
        JsonNode view = find(client, request);
        // The token voids only what it emitted, not documents issued through the UI or the API
        if (!"adapter".equals(view.path("origin").asText()) || !"nubefact".equals(view.path("labels").path("adapter").asText())) {
            throw ApiException.conflict("not_an_adapter_document", "El comprobante no fue emitido por la API compatible con Nubefact.");
        }
        String motivo = request.path("motivo").asText("Anulación");
        Document voidingDocument = voiding.voidDocumentAsPlatform(client.tenantId(), UUID.fromString(view.path("id").asText()),
                new VoidingService.VoidRequest(motivo, true, null), UUID.randomUUID().toString());
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(client.tenantId());
            io.github.projectopenubl.xhandler.documents.DocumentEvent.record(Document.findById(UUID.fromString(view.path("id").asText())), null,
                    "void requested by Nubefact token " + client.tokenId() + " (" + client.label() + ")", "token:" + client.tokenId(), null);
        });
        return Response.ok(Map.of("numero", view.path("numero").asLong(), "enlace", "", "sunat_ticket_numero", "",
                "aceptada_por_sunat", false, "estado_xhandler", "voiding", "comunicacion", voidingDocument.serieNumero())).build();
    }

    private JsonNode find(Client client, JsonNode request) {
        String tipo = NubefactAdapter.tipo(request);
        String serie = request.path("serie").asText();
        long numero = request.path("numero").asLong();
        return QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(client.tenantId());
            List<Document> found = Document.list("companyId = ?1 and sunatEnv = ?2 and tipoComprobante = ?3 and serie = ?4 and numero = ?5"
                    + " order by createdAt desc", client.companyId(), client.sunatEnv(), tipo, serie, numero);
            if (found.isEmpty()) {
                throw ApiException.notFound();
            }
            return json.valueToTree(DocumentView.of(found.get(0)));
        });
    }

    /** The token opens exactly one company; a wrong token or company is 401 (nothing leaks). */
    private Client authenticate(UUID companyId, String authorization) {
        String token = authorization == null ? "" : authorization.strip();
        if (token.startsWith("Token ")) {
            token = token.substring(6).strip();
        }
        if (token.startsWith("token=")) {
            token = token.substring(6);
        }
        token = token.replace("\"", "");
        String hash = AdapterResource.hash(token);
        return QuarkusTransaction.requiringNew().call(() -> {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery("SELECT tenant_id, company_id, label, token_id FROM xhandler.adapter_token_company_v2(:h, 'nubefact')")
                    .setParameter("h", hash).getResultList();
            if (rows.isEmpty() || !companyId.equals(rows.get(0)[1])) {
                throw new ApiException(401, "unauthorized", "Token inválido.");
            }
            UUID tenant = (UUID) rows.get(0)[0];
            access.enterAsPlatform(tenant);
            io.github.projectopenubl.xhandler.company.Company company = io.github.projectopenubl.xhandler.company.Company.findById(companyId);
            return new Client(tenant, companyId, company.sunatEnv, (UUID) rows.get(0)[3], (String) rows.get(0)[2]);
        });
    }
}
