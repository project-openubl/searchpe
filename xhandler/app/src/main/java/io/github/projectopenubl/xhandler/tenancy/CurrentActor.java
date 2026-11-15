package io.github.projectopenubl.xhandler.tenancy;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import org.eclipse.microprofile.jwt.JsonWebToken;

/** The authenticated caller of the current request, from its OIDC access token. */
@RequestScoped
public class CurrentActor {

    static final String PLATFORM_SUPPORT_ROLE = "platform-support";

    private final SecurityIdentity identity;
    private final JsonWebToken token;

    public CurrentActor(SecurityIdentity identity, JsonWebToken token) {
        this.identity = identity;
        this.token = token;
    }

    public Actor get() {
        if (identity.isAnonymous() || token.getSubject() == null) {
            throw new ApiException(401, "unauthorized", "Se requiere autenticación.");
        }
        // Keycloak service accounts (client credentials) carry the client id and a
        // service-account-* username; people don't.
        String username = token.getClaim("preferred_username");
        boolean client = token.containsClaim("client_id") || (username != null && username.startsWith("service-account-"));
        if (client) {
            String clientId = token.getClaim("azp");
            return new Actor("client", clientId, false, false);
        }
        Object verified = token.getClaim("email_verified");
        return new Actor("user", token.getSubject(), Boolean.TRUE.equals(verified) || "true".equals(String.valueOf(verified)),
                identity.hasRole(PLATFORM_SUPPORT_ROLE));
    }
}
