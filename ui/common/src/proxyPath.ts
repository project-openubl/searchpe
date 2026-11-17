import {
  rewriteSearchpeApiPath,
  rewriteXhandlerApiPath,
  SEARCHPE_PROXY_PREFIX,
  XHANDLER_PROXY_PREFIX,
} from "./environment.js";

/** One backend behind the UI's origin: the browser's prefix and how it maps to the backend. */
export interface IApiProxyRoute {
  /** Path prefix the browser uses (`/api/xhandler`). */
  prefix: string;
  /** Client path → backend path (query string kept). */
  rewrite: (path: string) => string;
  /** Backend paths outside `/api/` this route may reach, matched exactly. */
  extraBackendPaths?: readonly string[];
}

/** searchpe's readiness check, the only backend path outside `/api/` the UI proxies. */
export const SEARCHPE_HEALTH_BACKEND_PATH = "/q/health/ready";

/** The browser's path for searchpe's readiness check (the status query). */
export const SEARCHPE_HEALTH_PATH = `${SEARCHPE_PROXY_PREFIX}${SEARCHPE_HEALTH_BACKEND_PATH}`;

export const XHANDLER_API_ROUTE: IApiProxyRoute = {
  prefix: XHANDLER_PROXY_PREFIX,
  rewrite: rewriteXhandlerApiPath,
};

export const SEARCHPE_API_ROUTE: IApiProxyRoute = {
  prefix: SEARCHPE_PROXY_PREFIX,
  rewrite: rewriteSearchpeApiPath,
  extraBackendPaths: [SEARCHPE_HEALTH_BACKEND_PATH],
};

/** The 400 body for a rejected API path (xhandler's error shape, Spanish message). */
export const INVALID_API_PATH_ERROR = {
  kind: "invalid_request",
  message: "La ruta solicitada no es válida.",
} as const;

const pathOf = (url: string): string => url.split(/[?#]/, 1)[0];

/** `path` is `prefix` itself or below it (`/api/xhandler/…`, never `/api/xhandlerx`). */
export const matchesProxyPrefix = (url: string, prefix: string): boolean => {
  const path = pathOf(url);
  return path === prefix || path.startsWith(`${prefix}/`);
};

const ENCODED_SEPARATOR_OR_DOT = /%2e|%2f|%5c/i;

const hasDotSegment = (path: string): boolean =>
  path.split("/").some((segment) => segment === "." || segment === "..");

/**
 * A path that could climb out of the proxied prefix once a backend or another proxy normalizes
 * it: a `.`/`..` segment, a backslash, or an encoded dot, slash or backslash, in the raw path or
 * once decoded (so double encoding is caught too). The query string isn't checked.
 */
export const hasUnsafePathSegments = (url: string): boolean => {
  const raw = pathOf(url);
  let decoded: string;
  try {
    decoded = decodeURIComponent(raw);
  } catch {
    return true; // malformed escapes
  }
  return [raw, decoded].some(
    (path) =>
      path.includes("\\") ||
      ENCODED_SEPARATOR_OR_DOT.test(path) ||
      hasDotSegment(path),
  );
};

/**
 * The backend path (with its query) for a browser request on `route`, or `undefined` when it
 * must be rejected with a 400: an unsafe path, or one that lands outside the backend's `/api/`
 * (and the route's `extraBackendPaths`) after the rewrite.
 */
export const resolveApiProxyPath = (
  url: string,
  route: IApiProxyRoute,
): string | undefined => {
  if (!matchesProxyPrefix(url, route.prefix) || hasUnsafePathSegments(url)) {
    return undefined;
  }
  const backend = route.rewrite(url);
  const backendPath = pathOf(backend);
  return backendPath.startsWith("/api/") ||
    route.extraBackendPaths?.includes(backendPath)
    ? backend
    : undefined;
};
