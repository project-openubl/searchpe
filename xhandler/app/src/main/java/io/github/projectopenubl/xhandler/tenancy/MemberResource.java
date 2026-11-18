package io.github.projectopenubl.xhandler.tenancy;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Constraints;
import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.audit.Auditor;
import io.github.projectopenubl.xhandler.documents.Idempotency;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
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
 * People and machine clients of a tenant. A machine client (an ERP using OAuth2 client
 * credentials) is added by its Keycloak client id.
 *
 * <p>Admins add members as admin, operator or viewer; owners come only from signup or a partner.
 * Only an owner changes or removes an owner, and a tenant with owners always keeps one: member
 * changes lock the tenant row, so two owners demoting each other can't both succeed.
 */
@Path("/api/v1/tenants/{tenantId}/members")
public class MemberResource {

    public record MemberView(UUID id, String kind, String subject, String role) {}

    public record AddMember(
            @NotBlank @Pattern(regexp = "user|client") String kind,
            @NotBlank String subject,
            @NotBlank @Pattern(regexp = "admin|operator|viewer") String role) {}

    public record UpdateMember(@NotBlank @Pattern(regexp = "admin|operator|viewer") String role) {}

    private final TenantAccess access;
    private final Auditor auditor;
    private final Idempotency idempotency;
    private final EntityManager em;

    public MemberResource(TenantAccess access, Auditor auditor, Idempotency idempotency, EntityManager em) {
        this.access = access;
        this.auditor = auditor;
        this.idempotency = idempotency;
        this.em = em;
    }

    @Operation(operationId = "listMembers")
    @GET
    @Transactional
    public List<MemberView> list(@PathParam("tenantId") UUID tenantId) {
        access.enter(tenantId, Role.ADMIN);
        List<TenantMember> members = TenantMember.list("order by createdAt");
        return members.stream().map(MemberResource::view).toList();
    }

    @APIResponse(responseCode = "201", description = "Miembro agregado (también la respuesta repetida de una Idempotency-Key ya usada).",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MemberView.class)))
    @APIResponse(responseCode = "409", description = "El miembro ya pertenece a la cuenta, o Idempotency-Key en proceso.",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @Operation(operationId = "addMember")
    @POST
    public Response add(@PathParam("tenantId") UUID tenantId, @HeaderParam("Idempotency-Key") String idempotencyKey,
            @Valid AddMember request) {
        return idempotency.create(tenantId, Role.ADMIN, idempotencyKey, idempotency.hash("member", tenantId, request), () -> {
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
                if (Constraints.violated(e).filter(name -> name.startsWith("tenant_member_") && name.endsWith("_key")).isPresent()) {
                    throw ApiException.conflict("member_exists", "El miembro ya pertenece a la cuenta.");
                }
                throw e;
            }
            auditor.record(scope, "member.added", "tenant_member", member.id,
                    Map.of("kind", member.kind, "subject", member.subject, "role", member.role));
            return view(member);
        }, view -> view);
    }

    @APIResponse(responseCode = "200", description = "Miembro con su nuevo rol.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MemberView.class)))
    @APIResponse(responseCode = "409", description = "Dejaría a la cuenta sin propietario.",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @Operation(operationId = "updateMember", summary = "Cambia el rol de un miembro")
    @PUT
    @Path("/{memberId}")
    @Transactional
    public MemberView update(@PathParam("tenantId") UUID tenantId, @PathParam("memberId") UUID memberId, @Valid UpdateMember request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        TenantMember member = lockedMember(scope, memberId);
        String previous = member.role;
        if (!previous.equals(request.role())) {
            keepAnOwner(member);
            member.role = request.role();
            auditor.record(scope, "member.role_changed", "tenant_member", member.id,
                    Map.of("kind", member.kind, "subject", member.subject, "from", previous, "to", member.role));
        }
        return view(member);
    }

    @APIResponse(responseCode = "204", description = "Miembro quitado de la cuenta.")
    @APIResponse(responseCode = "409", description = "Dejaría a la cuenta sin propietario.",
            content = @Content(mediaType = "application/json", schema = @Schema(ref = "ApiError")))
    @Operation(operationId = "removeMember", summary = "Quita a un miembro de la cuenta")
    @DELETE
    @Path("/{memberId}")
    @Transactional
    public Response remove(@PathParam("tenantId") UUID tenantId, @PathParam("memberId") UUID memberId) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        TenantMember member = lockedMember(scope, memberId);
        keepAnOwner(member);
        member.delete();
        auditor.record(scope, "member.removed", "tenant_member", member.id,
                Map.of("kind", member.kind, "subject", member.subject, "role", member.role));
        return Response.noContent().build();
    }

    /**
     * The member, after locking the tenant row: member changes of a tenant run one at a time, so the
     * owner count read next stays true until commit. Owners are changed by owners only.
     */
    private TenantMember lockedMember(TenantScope scope, UUID memberId) {
        Tenant tenant = em.find(Tenant.class, scope.tenantId(), LockModeType.PESSIMISTIC_WRITE);
        if (tenant == null) {
            throw ApiException.notFound();
        }
        TenantMember member = TenantMember.findById(memberId);
        if (member == null) {
            throw ApiException.notFound();
        }
        if (Role.OWNER.json().equals(member.role) && !scope.role().allows(Role.OWNER)) {
            throw new ApiException(403, "owner_required", "Solo un propietario puede cambiar o quitar a otro propietario.");
        }
        return member;
    }

    /** A tenant with owners keeps at least one (a partner's tenant may have none to begin with). */
    private static void keepAnOwner(TenantMember member) {
        if (Role.OWNER.json().equals(member.role) && TenantMember.count("role = ?1", Role.OWNER.json()) <= 1) {
            throw ApiException.conflict("last_owner", "La cuenta debe conservar al menos un propietario.");
        }
    }

    private static MemberView view(TenantMember m) {
        return new MemberView(m.id, m.kind, m.subject, m.role);
    }
}
