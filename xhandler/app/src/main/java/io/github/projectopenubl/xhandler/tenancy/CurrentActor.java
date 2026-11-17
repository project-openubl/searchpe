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
        // A Keycloak service account (client credentials) token carries client_id = azp and the
        // username "service-account-<azp>". All three must agree: a person can't pick a
        // username to become a client.
        String clientId = token.getClaim("client_id");
        String azp = token.getClaim("azp");
        String username = token.getClaim("preferred_username");
        if (clientId != null && clientId.equals(azp) && ("service-account-" + azp).equals(username)) {
            return new Actor("client", azp, false, false);
        }
        Object verified = token.getClaim("email_verified");
        return new Actor("user", token.getSubject(), Boolean.TRUE.equals(verified) || "true".equals(String.valueOf(verified)),
                identity.hasRole(PLATFORM_SUPPORT_ROLE));
    }
}
