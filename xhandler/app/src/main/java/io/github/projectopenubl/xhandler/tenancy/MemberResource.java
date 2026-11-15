package io.github.projectopenubl.xhandler.tenancy;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Constraints;
import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.audit.Auditor;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * People and machine clients of a tenant. A machine client (an ERP using OAuth2 client
 * credentials) is added by its Keycloak client id.
 */
@Path("/api/v1/tenants/{tenantId}/members")
public class MemberResource {

    public record MemberView(UUID id, String kind, String subject, String role) {}

    public record AddMember(
            @NotBlank @Pattern(regexp = "user|client") String kind,
            @NotBlank String subject,
            @NotBlank @Pattern(regexp = "admin|operator|viewer") String role) {}

    private final TenantAccess access;
    private final Auditor auditor;

    public MemberResource(TenantAccess access, Auditor auditor) {
        this.access = access;
        this.auditor = auditor;
    }

    @GET
    @Transactional
    public List<MemberView> list(@PathParam("tenantId") UUID tenantId) {
        access.enter(tenantId, Role.ADMIN);
        List<TenantMember> members = TenantMember.list("order by createdAt");
        return members.stream().map(MemberResource::view).toList();
    }

    @POST
    @Transactional
    public Response add(@PathParam("tenantId") UUID tenantId, @Valid AddMember request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        TenantMember member = new TenantMember();
        member.id = Ids.newId();
        member.tenantId = tenantId;
        member.kind = request.kind();
        member.subject = request.subject().strip();
        member.role = request.role();
        try {
            member.persistAndFlush();
        } catch (PersistenceException e) {
            if (Constraints.violated(e).isPresent()) {
                throw ApiException.conflict("member_exists", "El miembro ya pertenece a la cuenta.");
            }
            throw e;
        }
        auditor.record(scope, "member.added", "tenant_member", member.id,
                Map.of("kind", member.kind, "subject", member.subject, "role", member.role));
        return Response.status(201).entity(view(member)).build();
    }

    private static MemberView view(TenantMember m) {
        return new MemberView(m.id, m.kind, m.subject, m.role);
    }
}
