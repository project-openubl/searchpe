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
  OPENUBL_ENV,
  SERVER_ENV_KEYS,
} from "@openubl-ui/common";
import proxies from "./proxies.js";

const debugMode = process.env.DEBUG === "1";
if (debugMode) {
  console.log("CONSOLE_ENV", OPENUBL_ENV);
}

const __dirname = fileURLToPath(new URL(".", import.meta.url));
const pathToClientDist = path.join(__dirname, "../../client/dist");

const port = OPENUBL_ENV.PORT ? parseInt(OPENUBL_ENV.PORT, 10) : 8080;

const app = express();
app.set("x-powered-by", false);

// Liveness/readiness for HTTP probes; it says nothing about the backends.
app.get("/healthz", (_, res) => {
  res.status(200).json({ status: "UP" });
});

app.use(createProxyMiddleware(proxies.xhandler));
if (OPENUBL_ENV.SEARCHPE_ENABLED === "true") {
  app.use(createProxyMiddleware(proxies.searchpe));
}

// An API path no proxy took (searchpe not deployed) is a 404, never the SPA's index.html.
app.use("/api", (_, res) => {
  res.status(404).json({
    kind: "not_found",
    message: "El servicio solicitado no está disponible.",
  });
});

app.engine("ejs", ejs.renderFile);
app.use(express.json());
app.set("views", pathToClientDist);
app.use(express.static(pathToClientDist));

// Handle any request that hasn't already been handled by express.static or proxy
app.get("*splat", (_, res) => {
  if (OPENUBL_ENV.NODE_ENV === "development") {
    res.send(`
      <style>pre { margin-left: 20px; }</style>
      You're running in development mode! The UI is served by the Vite dev server on port 3000: <a href="http://localhost:3000">http://localhost:3000</a><br /><br />
      To serve the UI through Express as in production, run: <pre>npm run start</pre>
    `);
  } else {
    res.render("index.html.ejs", {
      _env: encodeEnv(OPENUBL_ENV, SERVER_ENV_KEYS),
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
