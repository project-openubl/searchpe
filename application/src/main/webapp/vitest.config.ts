import { defineConfig } from "vitest/config";

export default defineConfig({
  resolve: {
    // Honour the `paths` from tsconfig.json (bare imports like "shared/hooks")
    tsconfigPaths: true,
  },
  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: ["./src/setupTests.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    coverage: {
      provider: "v8",
      include: ["src/**/*.{js,jsx,ts,tsx}"],
    },
  },
});
