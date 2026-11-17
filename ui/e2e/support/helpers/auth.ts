import { expect, type Page } from "@playwright/test";

/** The dev realm's olga holds every searchpe permission (xhandler/deploy/keycloak). */
export const E2E_USERNAME = process.env.E2E_USERNAME ?? "olga";
export const E2E_PASSWORD = process.env.E2E_PASSWORD ?? "olga";

/**
 * Logs in through Keycloak's form, as a user would. The Keycloak session cookie is what the
 * saved storage state keeps; the app's tokens live in sessionStorage and are renewed from it.
 */
export const authenticate = async (page: Page) => {
  await page.goto("/");
  await page.getByLabel(/username|usuario/i).fill(E2E_USERNAME);
  await page.getByLabel(/^password$|^contraseña$/i).fill(E2E_PASSWORD);
  await page.getByRole("button", { name: /sign in|iniciar sesión/i }).click();
  await expect(
    page.getByRole("heading", { level: 1, name: /^Hola,/ }),
  ).toBeVisible();
};
