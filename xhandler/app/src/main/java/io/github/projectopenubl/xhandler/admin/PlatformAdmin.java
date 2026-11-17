package io.github.projectopenubl.xhandler.admin;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;

/** The caller must be a platform administrator (Keycloak realm role {@code platform-admin}). */
@RequestScoped
public class PlatformAdmin {

    public static final String ROLE = "platform-admin";

    private final SecurityIdentity identity;

    public PlatformAdmin(SecurityIdentity identity) {
        this.identity = identity;
    }

    /** Returns the administrator's name for the audit trail, or fails with 403. */
    public String require() {
        if (identity.isAnonymous()) {
            throw new ApiException(401, "unauthorized", "Se requiere autenticación.");
        }
        if (!identity.hasRole(ROLE)) {
            throw ApiException.forbidden();
        }
        return "admin:" + identity.getPrincipal().getName();
    }
}
