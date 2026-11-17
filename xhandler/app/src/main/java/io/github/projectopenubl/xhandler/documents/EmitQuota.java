package io.github.projectopenubl.xhandler.documents;

import java.util.UUID;

/**
 * Checks a tenant's plan before an emit (DESIGN.md §11.1 step 2, §14). Implemented by billing; throws
 * an ApiException (402/429) when the tenant is over quota.
 */
public interface EmitQuota {

    /** Runs with the tenant open, in the emit's first transaction. */
    void checkEmit(UUID tenantId, String sunatEnv);
}
