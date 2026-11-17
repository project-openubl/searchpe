import { defineConfig, devices } from "@playwright/test";

const authFile = ".auth/user.json";
const isCI = !!process.env.CI;

/** The UI under test; by default the Vite dev server this config starts. */
const baseURL = process.env.E2E_BASE_URL ?? "http://localhost:3000";

/**
 * Runs against a real stack (README.md): Keycloak with the dev realm, xhandler-service and,
 * optionally, searchpe. Nothing is mocked.
 */
export default defineConfig({
  testDir: "./tests",
  fullyParallel: true,
  forbidOnly: isCI,
  retries: isCI ? 1 : 0,
  workers: isCI ? 2 : 3,
  reporter: isCI ? [["github"], ["html", { open: "never" }]] : "html",
  use: {
    baseURL,
    locale: "es-PE",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    navigationTimeout: 30_000,
    // Locally the installed Google Chrome (no `playwright install` needed); CI uses Chromium.
    ...(process.env.E2E_BROWSER_CHANNEL
      ? { channel: process.env.E2E_BROWSER_CHANNEL }
      : {}),
  },
  projects: [
    {
      name: "setup",
      testMatch: /auth\.setup\.ts/,
    },
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"], storageState: authFile },
      dependencies: ["setup"],
      testMatch: /.*\.spec\.ts/,
      testIgnore: /\/public\//,
    },
    {
      // Public routes (ADR 0003) run with no session at all.
      name: "public",
      use: { ...devices["Desktop Chrome"] },
      testMatch: /\/public\/.*\.spec\.ts/,
    },
  ],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: "npm run start:dev",
        cwd: "..",
        port: 3000,
        reuseExistingServer: !isCI,
        timeout: 120_000,
      },
});
