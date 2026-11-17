import type { Options } from "http-proxy-middleware";

import {
  type IApiProxyRoute,
  matchesProxyPrefix,
  OPENUBL_ENV,
  resolveApiProxyPath,
  SEARCHPE_API_ROUTE,
  XHANDLER_API_ROUTE,
} from "@openubl-ui/common";

type Logger = {
  info: (...args: unknown[]) => void;
  warn: (...args: unknown[]) => void;
  error: (...args: unknown[]) => void;
};

const logger: Logger =
  process.env.DEBUG === "1"
    ? console
    : {
        info() {},
        warn: console.warn,
        error: console.error,
      };

/** A proxied backend: its route (prefix, rewrite, allowed paths) and the proxy's options. */
export interface IBackendProxy {
  route: IApiProxyRoute;
  options: Options;
}

const backendProxy = (
  route: IApiProxyRoute,
  target: string,
): IBackendProxy => ({
  route,
  options: {
    pathFilter: (path) => matchesProxyPrefix(path, route.prefix),
    target,
    // The path guard in front of the proxy already answered 400 to anything this can't map;
    // the fallback is a path no backend serves, never the unmapped one.
    pathRewrite: (path) => resolveApiProxyPath(path, route) ?? "/api/-",
    logger,
    changeOrigin: true,
    xfwd: true,
  },
});

/**
 * One proxy per backend. The browser calls them on this origin (no CORS) and sends its own
 * `Authorization: Bearer` header, which passes through untouched. searchpe only exists when
 * `SEARCHPE_API_URL` is set: no target, no proxy.
 */
export const proxies: {
  xhandler: IBackendProxy;
  searchpe: IBackendProxy | undefined;
} = {
  xhandler: backendProxy(
    XHANDLER_API_ROUTE,
    OPENUBL_ENV.XHANDLER_API_URL ?? "http://localhost:8080",
  ),
  searchpe: OPENUBL_ENV.SEARCHPE_API_URL
    ? backendProxy(SEARCHPE_API_ROUTE, OPENUBL_ENV.SEARCHPE_API_URL)
    : undefined,
};

export default proxies;
