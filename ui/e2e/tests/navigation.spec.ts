import { expect, test } from "@support/fixtures";
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

  test("logs out from the user menu", async ({ page }) => {
    await page.goto("/");
    await page.getByRole("button", { name: /olga|alice|bob|carol/ }).click();
    await page.getByRole("menuitem", { name: "Cerrar sesión" }).click();
    // Keycloak's login form is back.
    await expect(page.getByLabel(/username|usuario/i)).toBeVisible();
  });
});
