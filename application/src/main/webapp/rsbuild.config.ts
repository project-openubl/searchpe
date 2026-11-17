import { defineConfig } from "@rsbuild/core";
import { pluginReact } from "@rsbuild/plugin-react";

// Quarkus in dev mode. The dev server proxies the API to it, and the UI sends
// users there to log in.
const DEV_BACKEND_URL = "http://localhost:8080";

export default defineConfig({
  plugins: [pluginReact()],
  source: {
    entry: {
      index: "./src/index.tsx",
    },
    define: {
      "process.env.SEARCHPE_DEV_BACKEND_URL": JSON.stringify(DEV_BACKEND_URL),
    },
  },
  html: {
    template: "./index.html",
  },
  output: {
    // Quarkus serves this directory (copied into META-INF/resources by the
    // `ui` Maven profile), so keep the same output dir CRA used.
    distPath: {
      root: "build",
    },
  },
  server: {
    port: 3000,
    // Same behaviour as the former CRA src/setupProxy.js
    proxy: {
      "/api": DEV_BACKEND_URL,
    },
  },
});
