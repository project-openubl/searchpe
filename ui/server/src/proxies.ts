import type { Options } from "http-proxy-middleware";

import {
  OPENUBL_ENV,
  rewriteSearchpeApiPath,
  rewriteXhandlerApiPath,
  SEARCHPE_PROXY_PREFIX,
  XHANDLER_PROXY_PREFIX,
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

/**
 * One proxy per backend. The browser calls them on this origin (no CORS) and sends its own
 * `Authorization: Bearer` header, which passes through untouched.
 */
export default {
  xhandler: {
    pathFilter: XHANDLER_PROXY_PREFIX,
    target: OPENUBL_ENV.XHANDLER_API_URL ?? "http://localhost:8080",
    pathRewrite: rewriteXhandlerApiPath,
    logger,
    changeOrigin: true,
  },
  // Only mounted when SEARCHPE_API_URL is set (searchpe is optional).
  searchpe: {
    pathFilter: SEARCHPE_PROXY_PREFIX,
    target: OPENUBL_ENV.SEARCHPE_API_URL,
    pathRewrite: rewriteSearchpeApiPath,
    logger,
    changeOrigin: true,
  },
} satisfies Record<string, Options>;
