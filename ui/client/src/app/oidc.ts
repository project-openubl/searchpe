import { UserManager, type UserManagerSettings } from "oidc-client-ts";

import { ENV } from "./env";

export const OIDC_SERVER_URL = ENV.OIDC_SERVER_URL;
export const OIDC_CLIENT_ID = ENV.OIDC_CLIENT_ID;

/** Keycloak's account console for the realm (profile, password, 2FA). */
export const OIDC_ACCOUNT_URL = `${OIDC_SERVER_URL.replace(/\/+$/, "")}/account`;

/** The public page Keycloak returns to after logout; it never starts a login (ADR 0003). */
export const SESSION_CLOSED_PATH = "/sesion-cerrada";

export const oidcSignoutArgs = {
  extraQueryParams: {
    client_id: OIDC_CLIENT_ID,
  },
};

export const oidcClientSettings: UserManagerSettings = {
  authority: OIDC_SERVER_URL,
  client_id: OIDC_CLIENT_ID,
  redirect_uri: window.location.origin,
  post_logout_redirect_uri: `${window.location.origin}${SESSION_CLOSED_PATH}`,
  // Authorization code flow; oidc-client-ts always adds PKCE (S256).
  response_type: "code",
  scope: ENV.OIDC_SCOPE,
  automaticSilentRenew: true,
};

/**
 * The app's one `UserManager`: `AuthProvider` (react-oidc-context) and the axios interceptors
 * share it, so they see the same user, the same storage and the same renewals.
 */
export const userManager = new UserManager(oidcClientSettings);

/** The current page, as `url_state` for a login that comes back to it. */
export const currentPageState = (): string =>
  window.location.pathname + window.location.search;
