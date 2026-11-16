package io.github.projectopenubl.xhandler.tenancy;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.audit.Auditor;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.inject.Instance;
import java.util.Map;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.UUID;

/**
 * Opens a tenant for the current transaction: checks the caller's role, then sets {@code
 * app.tenant_id} so row-level security shows only that tenant's rows (DESIGN.md §6, §10.8).
 *
 * <p>Call it first in every {@code @Transactional} method that touches tenant data. Unknown tenants
 * and tenants the caller doesn't belong to are both 404, so ids don't leak.
 */
@ApplicationScoped
public class TenantAccess {

    private final EntityManager em;
    private final CurrentActor currentActor;
    private final Auditor auditor;
    private final Instance<HttpServerRequest> request;

    public TenantAccess(EntityManager em, CurrentActor currentActor, Auditor auditor, Instance<HttpServerRequest> request) {
        this.em = em;
        this.currentActor = currentActor;
        this.auditor = auditor;
        this.request = request;
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public TenantScope enter(UUID tenantId, Role minimum) {
        Actor actor = currentActor.get();
        Role role = roleOf(tenantId, actor);
        if (role == null) {
            throw ApiException.notFound();
        }
        if (!role.allows(minimum)) {
            throw ApiException.forbidden();
        }
        setTenant(tenantId);
        TenantScope scope = new TenantScope(tenantId, actor, role);
        if (role == Role.SUPPORT) {
            // Every access by a platform operator is audited (DESIGN.md §26.1)
            HttpServerRequest http = request.get();
            auditor.record(scope, "support.access", "tenant", tenantId, Map.of("method", http.method().name(), "path", http.path()));
        }
        return scope;
    }

    /**
     * Opens a tenant without a caller, for platform work (key rotation, schedules). Never reachable
     * from a request without an explicit platform check.
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public void enterAsPlatform(UUID tenantId) {
        setTenant(tenantId);
    }

    private Role roleOf(UUID tenantId, Actor actor) {
        Object role = em.createNativeQuery("SELECT xhandler.member_role(:tenant, :kind, :subject)")
                .setParameter("tenant", tenantId)
                .setParameter("kind", actor.kind())
                .setParameter("subject", actor.subject())
                .getSingleResult();
        if (role != null) {
            return Role.fromDb(role.toString());
        }
        if (actor.platformSupport()) {
            Object active = em.createNativeQuery("SELECT xhandler.has_support_session(:tenant, :operator)")
                    .setParameter("tenant", tenantId)
                    .setParameter("operator", actor.subject())
                    .getSingleResult();
            if (Boolean.TRUE.equals(active)) {
                return Role.SUPPORT;
            }
        }
        return null;
    }

    void setTenant(UUID tenantId) {
        em.createNativeQuery("SELECT set_config('app.tenant_id', :tenant, true)")
                .setParameter("tenant", tenantId.toString())
                .getSingleResult();
    }
}
