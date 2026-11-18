/** Define process.env to contain `OpenublEnvType` */
declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace NodeJS {
    // eslint-disable-next-line @typescript-eslint/no-empty-object-type
    interface ProcessEnv extends Partial<Readonly<OpenublEnvType>> {}
  }
}

/** The set of environment variables used by `@openubl-ui` packages. */
export type OpenublEnvType = {
  NODE_ENV: "development" | "production" | "test";
  VERSION: string;

  /** SSO / OIDC client id (a public client with PKCE) */
  OIDC_CLIENT_ID: string;

  /** SSO / OIDC server URL (the Keycloak realm) */
  OIDC_SERVER_URL: string;

  /** SSO / OIDC scope */
  OIDC_SCOPE: string;

  /** The listen port for the UI's server */
  PORT?: string;

  /** Target URL for the `/api/xhandler` proxy (xhandler-service) */
  XHANDLER_API_URL?: string;

  /** Target URL for the `/api/searchpe` proxy (searchpe); unset when searchpe isn't deployed */
  SEARCHPE_API_URL?: string;

  /**
   * `"true"` when searchpe is deployed (`SEARCHPE_API_URL` set): the browser gets this flag,
   * never the URL, and hides searchpe's section without it.
   */
  SEARCHPE_ENABLED: "true" | "false";

  /** Location of branding files (relative paths computed from the project source root) */
  BRANDING?: string;

  /**
   * The platform's own virtual Libro de Reclamaciones (https), linked from every shell
   * (Ley 32495); unset hides the link.
   */
  LIBRO_RECLAMACIONES_URL?: string;
};

/**
 * The only keys of `OpenublEnv` sent to the browser (`window._env`). An allowlist: a new server
 * setting (a backend URL, a secret) stays on the server unless it is added here on purpose.
 */
export const CLIENT_ENV_KEYS = [
  "NODE_ENV",
  "VERSION",
  "OIDC_CLIENT_ID",
  "OIDC_SERVER_URL",
  "OIDC_SCOPE",
  "SEARCHPE_ENABLED",
  "LIBRO_RECLAMACIONES_URL",
] as const satisfies readonly (keyof OpenublEnvType)[];

/** Path prefix the browser uses for xhandler-service; the proxies send it to `/api`. */
export const XHANDLER_PROXY_PREFIX = "/api/xhandler";

/** Path prefix the browser uses for searchpe; the proxies send it to `/api`. */
export const SEARCHPE_PROXY_PREFIX = "/api/searchpe";

/** Rewrite a client path (`/api/xhandler/v1/...`) to xhandler-service's (`/api/v1/...`). */
export const rewriteXhandlerApiPath = (path: string): string =>
  path.replace(/^\/api\/xhandler(?=\/|$)/, "/api");

/**
 * Rewrite a client path (`/api/searchpe/whoami`) to searchpe's (`/api/whoami`). Its health
 * check lives outside `/api`, so `/api/searchpe/q/health` goes to `/q/health`.
 */
export const rewriteSearchpeApiPath = (path: string): string =>
  path
    .replace(/^\/api\/searchpe\/q\/health(?=\/|\?|$)/, "/q/health")
    .replace(/^\/api\/searchpe(?=\/|$)/, "/api");

/** Create an `OpenublEnv` from a partial one, filling in defaults. */
export const buildOpenublEnv = ({
  NODE_ENV = "production",
  PORT,
  VERSION = "99.0.0",

  OIDC_CLIENT_ID = "xhandler-ui",
  OIDC_SERVER_URL = "http://localhost:8180/realms/xhandler",
  OIDC_SCOPE = "openid",

  XHANDLER_API_URL,
  SEARCHPE_API_URL,
  SEARCHPE_ENABLED = SEARCHPE_API_URL ? "true" : "false",

  BRANDING,

  LIBRO_RECLAMACIONES_URL,
}: Partial<OpenublEnvType> = {}): OpenublEnvType => ({
  NODE_ENV,
  PORT,
  VERSION,

  OIDC_CLIENT_ID,
  OIDC_SERVER_URL,
  OIDC_SCOPE,

  XHANDLER_API_URL,
  SEARCHPE_API_URL,
  SEARCHPE_ENABLED,

  BRANDING,

  LIBRO_RECLAMACIONES_URL,
});

export const OPENUBL_ENV_DEFAULTS = buildOpenublEnv();

/** Current `@openubl-ui` environment config from `process.env`. */
export const OPENUBL_ENV = buildOpenublEnv(process.env);
