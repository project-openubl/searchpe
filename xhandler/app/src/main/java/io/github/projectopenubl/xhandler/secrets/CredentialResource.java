package io.github.projectopenubl.xhandler.secrets;

import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.company.CompanyResource;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Company secrets. They can be written, but never read back through the API. */
@Path("/api/v1/tenants/{tenantId}/companies/{companyId}/credentials")
public class CredentialResource {

    public record CredentialView(String kind, String label, Instant updatedAt) {}

    /** SUNAT SOL user (a secondary user with the CPE profile). */
    public record SolCredentials(@NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 200) String password) {}

    private final TenantAccess access;
    private final CredentialService credentials;

    public CredentialResource(TenantAccess access, CredentialService credentials) {
        this.access = access;
        this.credentials = credentials;
    }

    @GET
    @Transactional
    public List<CredentialView> list(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        access.enter(tenantId, Role.VIEWER);
        Company company = CompanyResource.find(companyId);
        List<Credential> list = Credential.list("companyId = ?1 order by kind", company.id);
        return list.stream().map(c -> new CredentialView(c.kind, c.label, c.updatedAt)).toList();
    }

    @PUT
    @Path("/sol")
    @Transactional
    public CredentialView putSol(
            @PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @Valid SolCredentials request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        Company company = CompanyResource.find(companyId);
        Credential stored = credentials.store(scope, company, "sol",
                Map.of("username", request.username(), "password", request.password()), mask(request.username()));
        return new CredentialView(stored.kind, stored.label, Instant.now());
    }

    /** "20123456789MODDATOS" → "2012…ATOS" */
    static String mask(String value) {
        return value.length() <= 8 ? "…" : value.substring(0, 4) + "…" + value.substring(value.length() - 4);
    }
}
