package io.github.projectopenubl.xhandler.audit;

import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Map;

/**
 * Records changes and every access to personal data or secrets (DESIGN.md §16), in the same
 * transaction as the action.
 */
@ApplicationScoped
public class Auditor {

    @Transactional(Transactional.TxType.MANDATORY)
    public void record(TenantScope scope, String action, String objectType, Object objectId, Map<String, Object> details) {
        AuditLog entry = new AuditLog();
        entry.id = Ids.newId();
        entry.at = Instant.now();
        entry.tenantId = scope.tenantId();
        entry.actor = scope.actor().auditName() + (scope.role() == io.github.projectopenubl.xhandler.tenancy.Role.SUPPORT ? " (support)" : "");
        entry.action = action;
        entry.objectType = objectType;
        entry.objectId = objectId == null ? null : objectId.toString();
        entry.details = details;
        entry.persist();
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public void record(TenantScope scope, String action, String objectType, Object objectId) {
        record(scope, action, objectType, objectId, null);
    }
}
