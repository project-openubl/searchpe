package io.github.projectopenubl.xhandler.support;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.audit.Auditor;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;

/**
 * Support access (DESIGN.md §26.1), tenant side: the tenant's owner or an admin approves or revokes
 * an operator's session; while approved, the operator has read-only access. All audited. Operators
 * request sessions through {@link SupportRequestResource}.
 */
@Path("/api/v1/tenants/{tenantId}/support-sessions")
public class SupportResource {

    static final Duration SESSION_LENGTH = Duration.ofHours(4);

    public record SessionView(UUID id, String operator, String reason, Instant requestedAt, String approvedBy, Instant expiresAt, Instant revokedAt) {}

    private final TenantAccess access;
    private final Auditor auditor;

    public SupportResource(TenantAccess access, Auditor auditor) {
        this.access = access;
        this.auditor = auditor;
    }

    @Operation(operationId = "listSupportSessions")
    @GET
    @Transactional
    public List<SessionView> list(@PathParam("tenantId") UUID tenantId) {
        access.enter(tenantId, Role.ADMIN);
        List<SupportSession> sessions = SupportSession.list("order by requestedAt desc");
        return sessions.stream().map(SupportResource::view).toList();
    }

    @Operation(operationId = "approveSupportSession")
    @POST
    @Path("/{sessionId}/approve")
    @Transactional
    public SessionView approve(@PathParam("tenantId") UUID tenantId, @PathParam("sessionId") UUID sessionId) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        SupportSession session = find(sessionId);
        if (session.approvedAt != null || session.revokedAt != null) {
            throw ApiException.conflict("session_closed", "La sesión de soporte ya fue aprobada o revocada.");
        }
        session.approvedBy = scope.actor().auditName();
        session.approvedAt = Instant.now();
        session.expiresAt = session.approvedAt.plus(SESSION_LENGTH);
        auditor.record(scope, "support_session.approved", "support_session", session.id, Map.of("expires_at", session.expiresAt.toString()));
        return view(session);
    }

    @Operation(operationId = "revokeSupportSession")
    @POST
    @Path("/{sessionId}/revoke")
    @Transactional
    public SessionView revoke(@PathParam("tenantId") UUID tenantId, @PathParam("sessionId") UUID sessionId) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        SupportSession session = find(sessionId);
        if (session.revokedAt == null) {
            session.revokedAt = Instant.now();
            auditor.record(scope, "support_session.revoked", "support_session", session.id);
        }
        return view(session);
    }

    private static SupportSession find(UUID id) {
        SupportSession session = SupportSession.findById(id);
        if (session == null) {
            throw ApiException.notFound();
        }
        return session;
    }

    private static SessionView view(SupportSession s) {
        return new SessionView(s.id, s.operator, s.reason, s.requestedAt, s.approvedBy, s.expiresAt, s.revokedAt);
    }
}
