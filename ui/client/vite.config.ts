/// <reference types="vitest/config" />

import fs from "node:fs";
import { createRequire } from "node:module";
import path from "node:path";

import { tanstackRouter } from "@tanstack/router-plugin/vite";
import react from "@vitejs/plugin-react";
import { playwright } from "@vitest/browser-playwright";
import { defineConfig, type Plugin } from "vite";
import { ViteEjsPlugin } from "vite-plugin-ejs";
import { viteStaticCopy } from "vite-plugin-static-copy";

import {
  brandingStrings,
  encodeEnv,
  hasUnsafePathSegments,
  INVALID_API_PATH_ERROR,
  OPENUBL_ENV,
  resolveApiProxyPath,
  SEARCHPE_API_ROUTE,
  SEARCHPE_PROXY_PREFIX,
  XHANDLER_API_ROUTE,
  XHANDLER_PROXY_PREFIX,
} from "@openubl-ui/common";

const require = createRequire(import.meta.url);
export const brandingAssetPath = () =>
  `${require
    .resolve("@openubl-ui/common/package.json")
    .replace(/(.)\/package.json$/, "$1")}/dist/branding`;

const brandingPath: string = brandingAssetPath();

const isSearchpeProxied = Boolean(OPENUBL_ENV.SEARCHPE_API_URL);

/** The headers of the production server that don't break Vite's dev tooling (no CSP: HMR). */
const securityHeaders = {
  "Referrer-Policy": "no-referrer",
  "X-Content-Type-Options": "nosniff",
};

/** The Express server's API path guard (server/src/index.ts), in front of Vite's proxies. */
const apiPathGuard: Plugin = {
  name: "api-path-guard",
  configureServer(server) {
    server.middlewares.use((req, res, next) => {
      const url = req.url ?? "";
      const route = [
        XHANDLER_API_ROUTE,
        ...(isSearchpeProxied ? [SEARCHPE_API_ROUTE] : []),
      ].find(({ prefix }) => url === prefix || url.startsWith(`${prefix}/`));
      const isRejected = url.startsWith("/api/")
        ? hasUnsafePathSegments(url) ||
          (route !== undefined && resolveApiProxyPath(url, route) === undefined)
        : false;
      if (!isRejected) {
        next();
        return;
      }
      res.statusCode = 400;
      res.setHeader("Content-Type", "application/json; charset=utf-8");
      res.end(JSON.stringify(INVALID_API_PATH_ERROR));
    });
  },
};
const manifestPath = path.resolve(brandingPath, "manifest.json");
const faviconPath = path.resolve(brandingPath, "favicon.ico");

// https://vite.dev/config/
export default defineConfig({
  base: process.env.BASE_URL,
  plugins: [
    tanstackRouter({
      target: "react",
      autoCodeSplitting: true,
      routesDirectory: "./src/routes",
      generatedRouteTree: "./src/routeTree.gen.ts",
    }),
    react(),
    apiPathGuard,
    {
      name: "ignore-process-env",
      transform(code) {
        return code.replace(/process\.env/g, "({})");
      },
    },
    // Branding assets only matter to the app; under Vitest the copy plugin's file watcher is
    // just one more inotify instance to run out of.
    ...(process.env.VITEST
      ? []
      : [
          viteStaticCopy({
            targets: [
              {
                src: manifestPath,
                dest: ".",
              },
              {
                src: brandingPath,
                dest: ".",
              },
              {
                src: faviconPath,
                dest: ".",
              },
            ],
          }),
        ]),
    ...(process.env.NODE_ENV === "development"
      ? [
          ViteEjsPlugin({
            _env: encodeEnv({
              ...OPENUBL_ENV,
              SEARCHPE_ENABLED: isSearchpeProxied ? "true" : "false",
            }),
            // No CSP in development (Vite's HMR injects inline scripts), so no nonce either.
            nonce: "",
            branding: brandingStrings,
          }),
        ]
      : []),
    ...(process.env.NODE_ENV === "production"
      ? [
          {
            name: "copy-index",
            closeBundle: () => {
              const distDir = path.resolve(__dirname, "dist");
              const src = path.join(distDir, "index.html");
              const dest = path.join(distDir, "index.html.ejs");

              if (fs.existsSync(src)) {
                fs.renameSync(src, dest);
              }
            },
          },
        ]
      : []),
  ],
  resolve: {
    alias: {
      "@app": path.resolve(__dirname, "./src/app"),
    },
  },
  // Scan every source file for deps up front: coverage transforms untested files late,
  // and a dep found then makes Vite re-optimize mid-run and hang the browser tests.
  optimizeDeps: {
    entries: ["src/**/*.{ts,tsx}"],
  },
  build: {
    rollupOptions: {
      output: {
        // Group only modules already in the graph into stable vendor chunks.
        // The function form (vs. a list of package names) avoids force-resolving
        // a package's barrel, which would pull in tree-shaken-away optional deps.
        manualChunks(id) {
          if (!id.includes("node_modules")) {
            return undefined;
          }
          if (id.includes("/react/") || id.includes("/react-dom/")) {
            return "react";
          }
          if (id.includes("/@patternfly/")) {
            return "patternfly";
          }
          return undefined;
        },
      },
    },
    sourcemap: process.env.NODE_ENV === "development",
  },
  preview: {
    headers: securityHeaders,
  },
  server: {
    port: 3000,
    headers: securityHeaders,
    // `vitest run` needs no watcher, and each one holds an inotify instance (a per-user
    // limit that busy desktops exhaust). `npm test -w client -- --watch` still works without it
    // only through polling: set CHOKIDAR_USEPOLLING=1.
    watch:
      process.env.VITEST && !process.argv.includes("--watch")
        ? null
        : undefined,
    // The same proxies as the Express server; the browser sends its own Bearer token.
    proxy: {
      [XHANDLER_PROXY_PREFIX]: {
        target: OPENUBL_ENV.XHANDLER_API_URL || "http://localhost:8080",
        rewrite: XHANDLER_API_ROUTE.rewrite,
        changeOrigin: true,
        xfwd: true,
      },
      ...(isSearchpeProxied
        ? {
            [SEARCHPE_PROXY_PREFIX]: {
              target: OPENUBL_ENV.SEARCHPE_API_URL,
              rewrite: SEARCHPE_API_ROUTE.rewrite,
              changeOrigin: true,
              xfwd: true,
            },
          }
        : {}),
    },
  },
  test: {
    browser: {
      enabled: true,
      // Locally use the installed Google Chrome, as `e2e/playwright.local.ts` does
      // (Playwright's Chromium has no Fedora build); CI installs Playwright's Chromium.
      provider: playwright({
        launchOptions: process.env.CI ? {} : { channel: "chrome" },
      }),
      headless: true,
      instances: [{ browser: "chromium" }],
    },
    server: {
      deps: {
        inline: [
          "@patternfly/react-styles", // Ensures its CSS imports are ignored
          "@openubl-ui/common", // Required for vite.config.ts imports
        ],
      },
    },
    coverage: {
      provider: "v8",
      reporter: ["text", "json", "html"],
      reportsDirectory: "./coverage",
      include: ["src/**/*.{ts,tsx}"],
      exclude: [
        "**/*.test.{ts,tsx}",
        "**/*.spec.{ts,tsx}",
        "**/node_modules/**",
        "**/dist/**",
        "**/coverage/**",
        "**/*.d.ts",
        "**/vite.config.ts",
        "**/config/**",
        "**/types/**",
      ],
    },
  },
});
