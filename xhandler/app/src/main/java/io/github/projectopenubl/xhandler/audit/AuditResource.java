package io.github.projectopenubl.xhandler.audit;

import io.github.projectopenubl.xhandler.api.Page;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A tenant's audit log, newest first: tenants can see who accessed their data (DESIGN.md §26.1). */
@Path("/api/v1/tenants/{tenantId}/audit")
public class AuditResource {

    public record AuditEntry(Instant at, String actor, String action, String objectType, String objectId, Map<String, Object> details) {}

    private final TenantAccess access;

    public AuditResource(TenantAccess access) {
        this.access = access;
    }

    @GET
    @Transactional
    public Page<AuditEntry> list(
            @PathParam("tenantId") UUID tenantId, @QueryParam("limit") Integer limit, @QueryParam("offset") Integer offset) {
        access.enter(tenantId, Role.ADMIN);
        int size = Page.limit(limit);
        int from = Page.offset(offset);
        List<AuditLog> entries = AuditLog.find("order by at desc, id desc").range(from, from + size - 1).list();
        return new Page<>(entries.stream()
                .map(e -> new AuditEntry(e.at, e.actor, e.action, e.objectType, e.objectId, e.details))
                .toList(), size, from);
    }
}
