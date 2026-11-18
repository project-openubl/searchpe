package io.github.projectopenubl.xhandler.billing;

import io.github.projectopenubl.xhandler.audit.Auditor;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.Tenant;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;

/** A tenant's plan, usage and billing details (DESIGN.md §14). */
@Path("/api/v1/tenants/{tenantId}/billing")
public class BillingResource {

    public record BillingDetails(@Pattern(regexp = "\\d{11}") String billingRuc, @Size(max = 300) String billingName,
            @Email String billingEmail) {}

    public record BillingView(Entitlements.Usage usage, String billingRuc, String billingName, String billingEmail) {}

    private final TenantAccess access;
    private final Entitlements entitlements;
    private final Auditor auditor;

    public BillingResource(TenantAccess access, Entitlements entitlements, Auditor auditor) {
        this.access = access;
        this.entitlements = entitlements;
        this.auditor = auditor;
    }

    @Operation(operationId = "getBilling")
    @GET
    @Transactional
    public BillingView get(@PathParam("tenantId") UUID tenantId) {
        access.enter(tenantId, Role.VIEWER);
        Tenant tenant = Tenant.findById(tenantId);
        return new BillingView(entitlements.usage(tenantId), tenant.billingRuc, tenant.billingName, tenant.billingEmail);
    }

    @Operation(operationId = "updateBillingDetails")
    @PUT
    @Transactional
    public BillingView details(@PathParam("tenantId") UUID tenantId, @Valid BillingDetails details) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        Tenant tenant = Tenant.findById(tenantId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        tenant.billingRuc = details.billingRuc();
        tenant.billingName = details.billingName();
        tenant.billingEmail = details.billingEmail();
        auditor.record(scope, "tenant.billing_updated", "tenant", tenantId);
        return get(tenantId);
    }
}
