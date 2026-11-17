/* global process */

import path from "node:path";
import { fileURLToPath } from "node:url";

import ejs from "ejs";
import express from "express";
import { createProxyMiddleware } from "http-proxy-middleware";
import { createHttpTerminator } from "http-terminator";

import {
  brandingStrings,
  encodeEnv,
  hasUnsafePathSegments,
  INVALID_API_PATH_ERROR,
  OPENUBL_ENV,
  resolveApiProxyPath,
} from "@openubl-ui/common";
import proxies, { type IBackendProxy } from "./proxies.js";
import {
  originOf,
  securityHeaders,
  staticCacheControl,
} from "./securityHeaders.js";

const debugMode = process.env.DEBUG === "1";
if (debugMode) {
  console.log("CONSOLE_ENV", OPENUBL_ENV);
}

const __dirname = fileURLToPath(new URL(".", import.meta.url));
const pathToClientDist = path.join(__dirname, "../../client/dist");

const port = OPENUBL_ENV.PORT ? parseInt(OPENUBL_ENV.PORT, 10) : 8080;

const app = express();
app.set("x-powered-by", false);

app.use(securityHeaders({ oidcOrigin: originOf(OPENUBL_ENV.OIDC_SERVER_URL) }));

// Liveness/readiness for HTTP probes; it says nothing about the backends.
app.get("/healthz", (_, res) => {
  res.status(200).json({ status: "UP" });
});

// No API path that could climb out of its prefix (`..`, encoded slashes, backslashes) reaches a
// proxy, and none lands outside the backend's `/api/` after the rewrite.
app.use("/api", (req, res, next) => {
  if (hasUnsafePathSegments(req.originalUrl)) {
    res.status(400).json(INVALID_API_PATH_ERROR);
    return;
  }
  next();
});

const mountProxy = ({ route, options }: IBackendProxy) => {
  app.use(route.prefix, (req, res, next) => {
    if (resolveApiProxyPath(req.originalUrl, route) === undefined) {
      res.status(400).json(INVALID_API_PATH_ERROR);
      return;
    }
    next();
  });
  app.use(createProxyMiddleware(options));
};

mountProxy(proxies.xhandler);
if (proxies.searchpe) {
  mountProxy(proxies.searchpe);
}

// An API path no proxy took (searchpe not deployed) is a 404, never the SPA's index.html.
app.use("/api", (_, res) => {
  res.status(404).json({
    kind: "not_found",
    message: "El servicio solicitado no está disponible.",
  });
});

app.engine("ejs", ejs.renderFile);
app.set("views", pathToClientDist);
app.use(
  express.static(pathToClientDist, {
    index: false,
    setHeaders: (res, filePath) => {
      res.setHeader(
        "Cache-Control",
        staticCacheControl(path.relative(pathToClientDist, filePath)),
      );
    },
  }),
);

// The browser learns whether searchpe exists from the proxy the server mounted, never its URL.
const clientEnv = {
  ...OPENUBL_ENV,
  SEARCHPE_ENABLED: proxies.searchpe ? "true" : "false",
};

// Handle any request that hasn't already been handled by express.static or proxy
app.get("*splat", (_, res) => {
  if (OPENUBL_ENV.NODE_ENV === "development") {
    res.send(`
      <style>pre { margin-left: 20px; }</style>
      You're running in development mode! The UI is served by the Vite dev server on port 3000: <a href="http://localhost:3000">http://localhost:3000</a><br /><br />
      To serve the UI through Express as in production, run: <pre>npm run start</pre>
    `);
  } else {
    res.setHeader("Cache-Control", "no-store");
    res.render("index.html.ejs", {
      _env: encodeEnv(clientEnv),
      nonce: res.locals.nonce as string,
      branding: brandingStrings,
    });
  }
});

const server = app.listen(port, (error) => {
  if (error) {
    throw error; // e.g. EADDRINUSE
  }
  console.log(`Server listening on port::${port}`);
});

// Handle shutdown signals Ctrl-C (SIGINT) and default podman/docker stop (SIGTERM)
const httpTerminator = createHttpTerminator({ server });

const shutdown = async (signal: NodeJS.Signals) => {
  if (!server) {
    console.log(`${signal}, no server running.`);
    return;
  }

  console.log(`${signal} - Stopping server on port::${port}`);
  await httpTerminator.terminate();
  console.log(`${signal} - Stopped server on port::${port}`);
};

process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
