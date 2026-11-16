import { defineConfig } from "@rsbuild/core";
import { pluginReact } from "@rsbuild/plugin-react";

export default defineConfig({
  plugins: [pluginReact()],
  source: {
    entry: {
      index: "./src/index.tsx",
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
      "/api": "http://localhost:8080",
    },
  },
});
