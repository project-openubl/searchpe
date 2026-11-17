package io.github.projectopenubl.xhandler.email;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.audit.Auditor;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Addresses a tenant no longer emails (DESIGN.md §26.5): hard bounces and complaints, recorded by
 * the platform's bounce processing; the tenant sees them and may remove one after fixing it.
 */
@Path("/api/v1/tenants/{tenantId}/email-suppressions")
public class EmailSuppressions {

    public record Suppression(String address, String reason, Instant createdAt) {}

    private final TenantAccess access;
    private final EntityManager em;
    private final Auditor auditor;

    public EmailSuppressions(TenantAccess access, EntityManager em, Auditor auditor) {
        this.access = access;
        this.em = em;
        this.auditor = auditor;
    }

    @GET
    @Transactional
    public List<Suppression> list(@PathParam("tenantId") UUID tenantId) {
        access.enter(tenantId, Role.VIEWER);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT address, reason, created_at FROM xhandler.email_suppression ORDER BY created_at DESC")
                .getResultList();
        return rows.stream().map(r -> new Suppression((String) r[0], (String) r[1],
                io.github.projectopenubl.xhandler.status.StatusResource.toInstant(r[2]))).toList();
    }

    @DELETE
    @Path("/{address}")
    @Transactional
    public Response remove(@PathParam("tenantId") UUID tenantId, @PathParam("address") String address) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        int removed = em.createNativeQuery("DELETE FROM xhandler.email_suppression WHERE address = :a")
                .setParameter("a", normalize(address)).executeUpdate();
        if (removed == 0) {
            throw ApiException.notFound();
        }
        auditor.record(scope, "email.suppression_removed", "email", null, Map.of("address", normalize(address)));
        return Response.noContent().build();
    }

    /** With the tenant open. */
    static boolean suppressed(EntityManager em, String address) {
        return !em.createNativeQuery("SELECT 1 FROM xhandler.email_suppression WHERE address = :a")
                .setParameter("a", normalize(address)).getResultList().isEmpty();
    }

    /** With the tenant open (platform bounce processing). */
    public static void suppress(EntityManager em, UUID tenantId, String address, String reason) {
        em.createNativeQuery("""
                        INSERT INTO xhandler.email_suppression (tenant_id, address, reason) VALUES (:t, :a, :r)
                        ON CONFLICT (tenant_id, address) DO UPDATE SET reason = excluded.reason""")
                .setParameter("t", tenantId).setParameter("a", normalize(address)).setParameter("r", reason).executeUpdate();
    }

    static String normalize(String address) {
        return address.strip().toLowerCase(Locale.ROOT);
    }
}
