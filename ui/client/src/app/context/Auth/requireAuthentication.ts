import type { IAuthContext } from "./AuthContext";

interface IRequireAuthenticationArgs {
  context: { auth: IAuthContext | null };
  location: { href: string };
  preload: boolean;
}

/**
 * `beforeLoad` of the `_authenticated` layout (ADR 0003): without a session it starts the OIDC
 * login and holds the route on a promise that never settles, so no page renders and no loader
 * fires while the browser leaves for Keycloak. A preload (hovering a link) never starts a login.
 */
export const requireAuthentication = ({
  context: { auth },
  location,
  preload,
}: IRequireAuthenticationArgs): undefined | Promise<never> => {
  if (preload || !auth || auth.isAuthenticated()) {
    return undefined;
  }
  if (auth.error) {
    throw auth.error;
  }

  auth.login(location.href);
  return new Promise<never>(() => {});
};
