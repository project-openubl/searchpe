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
    private final io.github.projectopenubl.xhandler.webhooks.Egress egress;

    public CredentialResource(TenantAccess access, CredentialService credentials, io.github.projectopenubl.xhandler.webhooks.Egress egress) {
        this.access = access;
        this.credentials = credentials;
        this.egress = egress;
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

    /** GRE REST API (DESIGN.md §3.1): the SOL user plus the API client created in SOL. */
    public record GreCredentials(@NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 200) String password,
            @NotBlank @Size(max = 200) String clientId, @NotBlank @Size(max = 300) String clientSecret) {}

    @PUT
    @Path("/gre")
    @Transactional
    public CredentialView putGre(
            @PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @Valid GreCredentials request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        Company company = CompanyResource.find(companyId);
        Credential stored = credentials.store(scope, company, "gre", Map.of("username", request.username(), "password",
                request.password(), "client_id", request.clientId(), "client_secret", request.clientSecret()), mask(request.username()));
        return new CredentialView(stored.kind, stored.label, Instant.now());
    }

    /** The tenant's own SMTP server for document emails (DESIGN.md §11.6). */
    public record SmtpCredentials(@NotBlank @Size(max = 255) String host, @jakarta.validation.constraints.Min(1)
            @jakarta.validation.constraints.Max(65535) int port, @NotBlank @Size(max = 200) String username,
            @NotBlank @Size(max = 300) String password, @NotBlank @jakarta.validation.constraints.Email String from, Boolean startTls) {}

    @PUT
    @Path("/smtp")
    @Transactional
    public CredentialView putSmtp(
            @PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @Valid SmtpCredentials request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        Company company = CompanyResource.find(companyId);
        // A tenant-supplied host: it must resolve to public addresses (DESIGN.md §16)
        egress.checkHost(request.host());
        Credential stored = credentials.store(scope, company, "smtp", Map.of("host", request.host(), "port", String.valueOf(request.port()),
                "username", request.username(), "password", request.password(), "from", request.from(),
                "start_tls", String.valueOf(!Boolean.FALSE.equals(request.startTls()))), request.from());
        return new CredentialView(stored.kind, stored.label, Instant.now());
    }

    /** A commercial RUC/DNI lookup provider (DESIGN.md §17): URL with {tipo}/{numero}, and the tenant's token. */
    public record LookupCredentials(@NotBlank @Size(max = 2000) String url, @Size(max = 500) String token) {}

    @PUT
    @Path("/lookup")
    @Transactional
    public CredentialView putLookup(
            @PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @Valid LookupCredentials request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        Company company = CompanyResource.find(companyId);
        egress.check(request.url().replace("{tipo}", "ruc").replace("{numero}", "20000000001"));
        Map<String, String> fields = new java.util.HashMap<>(Map.of("url", request.url()));
        if (request.token() != null) {
            fields.put("token", request.token());
        }
        Credential stored = credentials.store(scope, company, "lookup", fields, java.net.URI.create(request.url().replace("{", "").replace("}", "")).getHost());
        return new CredentialView(stored.kind, stored.label, Instant.now());
    }

    /** "20123456789MODDATOS" → "2012…ATOS" */
    static String mask(String value) {
        return value.length() <= 8 ? "…" : value.substring(0, 4) + "…" + value.substring(value.length() - 4);
    }
}
