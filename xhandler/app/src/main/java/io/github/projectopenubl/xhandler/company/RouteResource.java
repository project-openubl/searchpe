package io.github.projectopenubl.xhandler.company;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.audit.Auditor;
import io.github.projectopenubl.xhandler.secrets.CredentialService;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import io.github.projectopenubl.xhandler.webhooks.Egress;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;

/**
 * A company's submission route (DESIGN.md §3.2): SUNAT directly, or a third-party OSE. GRE always
 * goes to SUNAT. An OSE-obligated company can't choose SUNAT. OSE URLs are tenant-supplied hosts,
 * so they pass the egress checks (§16).
 */
@Path("/api/v1/tenants/{tenantId}/companies/{companyId}/route")
public class RouteResource {

    public record Ose(@NotBlank @Size(max = 200) String name, @NotBlank @Size(max = 2000) String invoiceUrl,
            @Size(max = 2000) String perceptionUrl, @NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 200) String password) {}

    public record RouteChange(@NotBlank @Pattern(regexp = "sunat|ose") String submissionRoute, @Valid Ose ose) {}

    public record RouteView(String submissionRoute, boolean oseObligated, String oseName, String oseInvoiceUrl, String osePerceptionUrl) {}

    private final TenantAccess access;
    private final CredentialService credentials;
    private final Egress egress;
    private final Auditor auditor;

    public RouteResource(TenantAccess access, CredentialService credentials, Egress egress, Auditor auditor) {
        this.access = access;
        this.credentials = credentials;
        this.egress = egress;
        this.auditor = auditor;
    }

    @Operation(operationId = "getRoute")
    @GET
    @Transactional
    public RouteView get(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId) {
        access.enter(tenantId, Role.VIEWER);
        return view(CompanyResource.find(companyId));
    }

    @Operation(operationId = "setRoute")
    @PUT
    @Transactional
    public RouteView set(@PathParam("tenantId") UUID tenantId, @PathParam("companyId") UUID companyId, @Valid RouteChange change) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        Company company = CompanyResource.findForUpdate(companyId);
        if ("sunat".equals(change.submissionRoute())) {
            if (company.oseObligated) {
                throw ApiException.conflict("ose_obligated", "La empresa está obligada a emitir a través de un OSE.");
            }
        } else {
            Ose ose = change.ose();
            if (ose == null) {
                throw ApiException.invalid("invalid_request", "Indique los datos del OSE.", null);
            }
            egress.check(ose.invoiceUrl());
            if (ose.perceptionUrl() != null) {
                egress.check(ose.perceptionUrl());
            }
            OseEndpoint endpoint = OseEndpoint.of(company.id).orElseGet(() -> {
                OseEndpoint e = new OseEndpoint();
                e.id = Ids.newId();
                e.tenantId = company.tenantId;
                e.companyId = company.id;
                e.createdAt = Instant.now();
                return e;
            });
            endpoint.name = ose.name().strip();
            endpoint.invoiceUrl = ose.invoiceUrl();
            endpoint.perceptionUrl = ose.perceptionUrl();
            endpoint.updatedAt = Instant.now();
            endpoint.persist();
            credentials.store(scope, company, "ose", Map.of("username", ose.username(), "password", ose.password()), ose.name());
        }
        company.submissionRoute = change.submissionRoute();
        auditor.record(scope, "company.route_changed", "company", companyId, Map.of("route", change.submissionRoute()));
        return view(company);
    }

    static RouteView view(Company company) {
        OseEndpoint ose = OseEndpoint.of(company.id).orElse(null);
        return new RouteView(company.submissionRoute, company.oseObligated, ose == null ? null : ose.name,
                ose == null ? null : ose.invoiceUrl, ose == null ? null : ose.perceptionUrl);
    }
}
