import type React from "react";
import { useCallback, useMemo } from "react";
import {
  AuthProvider as OidcProvider,
  useAuth as useOidcAuth,
} from "react-oidc-context";

import { oidcClientSettings, oidcSignoutArgs } from "@app/oidc";
import { useLastTenantStore } from "@app/stores/useLastTenantStore";

import { AuthContext, type IAuthContext } from "./AuthContext";

interface IAuthProviderProps {
  children: React.ReactNode;
}

/** OIDC (Keycloak) is the only login; public routes never call `login` (ADR 0003). */
export const AuthProvider: React.FC<IAuthProviderProps> = ({ children }) => (
  <OidcProvider
    {...oidcClientSettings}
    automaticSilentRenew={true}
    onSigninCallback={(user) => {
      // Back to the page that started the login, without the code/state params.
      const target =
        typeof user?.url_state === "string" && user.url_state.startsWith("/")
          ? user.url_state
          : "/";
      window.history.replaceState({}, "", target);
    }}
  >
    <OidcAuthBridge>{children}</OidcAuthBridge>
  </OidcProvider>
);

interface IOidcAuthBridgeProps {
  children: React.ReactNode;
}

const OidcAuthBridge: React.FC<IOidcAuthBridgeProps> = ({ children }) => {
  const auth = useOidcAuth();

  const login = useCallback(
    (redirectTo?: string) => {
      // A failure (Keycloak unreachable) lands in `auth.error`, which the shell shows.
      auth
        .signinRedirect({
          url_state:
            redirectTo ?? window.location.pathname + window.location.search,
        })
        .catch(() => undefined);
    },
    [auth],
  );

  const logout = useCallback(async () => {
    useLastTenantStore.getState().reset();
    await auth.signoutRedirect(oidcSignoutArgs);
  }, [auth]);

  const value: IAuthContext = useMemo(
    () => ({
      user: auth.isAuthenticated
        ? {
            username:
              (auth.user?.profile.preferred_username as string) ??
              auth.user?.profile.sub ??
              "",
          }
        : null,
      isAuthenticated: () => auth.isAuthenticated,
      isLoading: auth.isLoading,
      error: auth.error ?? null,
      login,
      logout,
    }),
    [auth, login, logout],
  );

  return <AuthContext value={value}>{children}</AuthContext>;
};
