package io.github.projectopenubl.xhandler.dev;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.company.CompanyResource;
import io.github.projectopenubl.xhandler.engine.Engine;
import io.github.projectopenubl.xhandler.engine.EngineModels;
import io.github.projectopenubl.xhandler.keys.SigningService;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

/**
 * Skeleton flow for development: builds a document, signs it with the company's key (through the
 * vault) and sends it to SUNAT beta, synchronously. Only in builds with {@code
 * xhandler.dev-endpoints.enabled=true}; replaced by {@code POST /api/v1/documents} in P3.
 */
@IfBuildProperty(name = "xhandler.dev-endpoints.enabled", stringValue = "true")
@Path("/api/v1/tenants/{tenantId}/companies/{companyId}/dev/documents")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class DevDocumentResource {

    public record DevSendResult(String digestValue, EngineModels.SendResponse sunat) {}

    private final TenantAccess access;
    private final Engine engine;
    private final SigningService signing;

    public DevDocumentResource(TenantAccess access, Engine engine, SigningService signing) {
        this.access = access;
        this.engine = engine;
        this.signing = signing;
    }

    @POST
    @Transactional
    public DevSendResult buildSignSend(
            @PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, JsonNode document) {
        access.enter(tenantId, Role.OPERATOR);
        Company company = CompanyResource.find(companyId);
        if (!"beta".equals(company.sunatEnv)) {
            throw ApiException.invalid("invalid_request", "Este endpoint de desarrollo solo envía a SUNAT beta.", null);
        }
        EngineModels.BuildResponse built = engine.build(document);
        EngineModels.SignResponse signed = signing.sign(company, built.xml());
        EngineModels.SendResponse sunat = engine.send(signed.xml(), EngineModels.Environment.BETA);
        return new DevSendResult(signed.digestValue(), sunat);
    }
}
