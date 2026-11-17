import type { OidcClientSettings } from "oidc-client-ts";

import { ENV } from "./env";

export const OIDC_SERVER_URL = ENV.OIDC_SERVER_URL;
export const OIDC_CLIENT_ID = ENV.OIDC_CLIENT_ID;

/** Keycloak's account console for the realm (profile, password, 2FA). */
export const OIDC_ACCOUNT_URL = `${OIDC_SERVER_URL.replace(/\/+$/, "")}/account`;

export const oidcSignoutArgs = {
  extraQueryParams: {
    client_id: OIDC_CLIENT_ID,
  },
};

export const oidcClientSettings: OidcClientSettings = {
  authority: OIDC_SERVER_URL,
  client_id: OIDC_CLIENT_ID,
  redirect_uri: window.location.origin,
  post_logout_redirect_uri: window.location.origin,
  // Authorization code flow; oidc-client-ts always adds PKCE (S256).
  response_type: "code",
  scope: ENV.OIDC_SCOPE,
};
