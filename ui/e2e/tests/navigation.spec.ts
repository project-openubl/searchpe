import { expect, test } from "@support/fixtures";
import { authenticate, E2E_USERNAME } from "@support/helpers/auth";
import { SEARCHPE_ENABLED } from "@support/helpers/services";

test.describe("app shell", () => {
  test("shows both products in the sidebar, in Spanish", async ({ page }) => {
    await page.goto("/");
    const nav = page.getByRole("navigation", { name: "Navegación" });

    await expect(nav.getByRole("link", { name: "Inicio" })).toBeVisible();
    await expect(
      nav.getByRole("button", { name: "Comprobantes electrónicos" }),
    ).toBeVisible();
    await expect(
      nav.getByRole("link", { name: "Organizaciones" }),
    ).toBeVisible();
    if (SEARCHPE_ENABLED) {
      await expect(
        nav.getByRole("link", { name: "Consulta RUC" }),
      ).toBeVisible();
    } else {
      await expect(
        nav.getByRole("button", { name: "Padrón RUC" }),
      ).not.toBeVisible();
    }
  });

  test("an unknown page is a 404", async ({ page }) => {
    await page.goto("/no-existe");
    await expect(
      page.getByRole("heading", { name: "404: La página no existe" }),
    ).toBeVisible();
  });

  test.describe("with a session of its own", () => {
    // Logging out ends the Keycloak session; the saved one is shared by every other spec.
    test.use({ storageState: { cookies: [], origins: [] } });

    test("logs out from the user menu", async ({ page }) => {
      await authenticate(page);
      await page
        .getByRole("button", { name: new RegExp(E2E_USERNAME, "i") })
        .click();
      await page.getByRole("menuitem", { name: "Cerrar sesión" }).click();
      // A public page: no new login starts by itself (ADR 0003)...
      await expect(
        page.getByRole("heading", { name: "Sesión cerrada" }),
      ).toBeVisible();
      // ...and signing in again asks for the password: the Keycloak session ended.
      await page.getByRole("button", { name: "Iniciar sesión" }).click();
      await expect(
        page.getByRole("textbox", { name: /username|usuario/i }),
      ).toBeVisible();
    });
  });
});
