package io.github.projectopenubl.xhandler.notifications;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Page;
import io.github.projectopenubl.xhandler.audit.Auditor;
import io.github.projectopenubl.xhandler.status.StatusResource;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.Tenant;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/** In-app notifications and their preferences (DESIGN.md §26.6). */
@Path("/api/v1/tenants/{tenantId}")
public class NotificationResource {

    public record NotificationView(UUID id, String kind, String title, String body, UUID documentId, Instant createdAt, Instant readAt) {}

    public record Preference(@NotBlank String kind, boolean email, boolean inApp) {}

    public record NotificationSettings(@Email String notificationEmail) {}

    private final TenantAccess access;
    private final EntityManager em;
    private final Auditor auditor;

    public NotificationResource(TenantAccess access, EntityManager em, Auditor auditor) {
        this.access = access;
        this.em = em;
        this.auditor = auditor;
    }

    @Operation(operationId = "listNotifications")
    @GET
    @Path("/notifications")
    @Transactional
    public Page<NotificationView> list(@PathParam("tenantId") UUID tenantId, @QueryParam("unread") Boolean unread,
            @QueryParam("limit") Integer limit, @QueryParam("offset") Integer offset) {
        access.enter(tenantId, Role.VIEWER);
        int size = Page.limit(limit);
        int from = Page.offset(offset);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT id, kind, title, body, document_id, created_at, read_at FROM xhandler.notification"
                        + (Boolean.TRUE.equals(unread) ? " WHERE read_at IS NULL" : "") + " ORDER BY created_at DESC LIMIT :l OFFSET :o")
                .setParameter("l", size).setParameter("o", from).getResultList();
        return new Page<>(rows.stream().map(r -> new NotificationView((UUID) r[0], (String) r[1], (String) r[2], (String) r[3], (UUID) r[4],
                StatusResource.toInstant(r[5]), StatusResource.toInstant(r[6]))).toList(), size, from);
    }

    @APIResponse(responseCode = "204", description = "Notificación marcada como leída.")
    @Operation(operationId = "markNotificationRead")
    @POST
    @Path("/notifications/{id}/read")
    @Transactional
    public Response read(@PathParam("tenantId") UUID tenantId, @PathParam("id") UUID id) {
        access.enter(tenantId, Role.VIEWER);
        int updated = em.createNativeQuery("UPDATE xhandler.notification SET read_at = coalesce(read_at, now()) WHERE id = :id")
                .setParameter("id", id).executeUpdate();
        if (updated == 0) {
            throw ApiException.notFound();
        }
        return Response.noContent().build();
    }

    @APIResponse(responseCode = "204", description = "Notificaciones marcadas como leídas.")
    @Operation(operationId = "markAllNotificationsRead")
    @POST
    @Path("/notifications/read-all")
    @Transactional
    public Response readAll(@PathParam("tenantId") UUID tenantId) {
        access.enter(tenantId, Role.VIEWER);
        em.createNativeQuery("UPDATE xhandler.notification SET read_at = now() WHERE read_at IS NULL").executeUpdate();
        return Response.noContent().build();
    }

    @Operation(operationId = "getNotificationPreferences")
    @GET
    @Path("/notification-preferences")
    @Transactional
    public List<Preference> preferences(@PathParam("tenantId") UUID tenantId) {
        access.enter(tenantId, Role.VIEWER);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT kind, email, in_app FROM xhandler.notification_preference ORDER BY kind")
                .getResultList();
        return rows.stream().map(r -> new Preference((String) r[0], (Boolean) r[1], (Boolean) r[2])).toList();
    }

    @Operation(operationId = "setNotificationPreferences")
    @PUT
    @Path("/notification-preferences")
    @Transactional
    public List<Preference> setPreferences(@PathParam("tenantId") UUID tenantId, List<@Valid Preference> preferences) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        for (Preference p : preferences) {
            em.createNativeQuery("""
                            INSERT INTO xhandler.notification_preference (tenant_id, kind, email, in_app) VALUES (:t, :k, :e, :i)
                            ON CONFLICT (tenant_id, kind) DO UPDATE SET email = excluded.email, in_app = excluded.in_app""")
                    .setParameter("t", tenantId).setParameter("k", p.kind()).setParameter("e", p.email()).setParameter("i", p.inApp())
                    .executeUpdate();
        }
        auditor.record(scope, "notification.preferences_updated", "tenant", tenantId);
        return preferences(tenantId);
    }

    @Operation(operationId = "updateNotificationSettings")
    @PUT
    @Path("/notification-settings")
    @Transactional
    public Map<String, Object> settings(@PathParam("tenantId") UUID tenantId, @Valid NotificationSettings settings) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        Tenant tenant = Tenant.findById(tenantId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        tenant.notificationEmail = settings.notificationEmail() == null || settings.notificationEmail().isBlank()
                ? null : settings.notificationEmail().strip();
        auditor.record(scope, "tenant.notification_email_changed", "tenant", tenantId);
        java.util.Map<String, Object> view = new java.util.HashMap<>();
        view.put("notification_email", tenant.notificationEmail);
        return view;
    }
}
