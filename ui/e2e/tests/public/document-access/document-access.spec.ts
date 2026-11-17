import { expect, test } from "@support/fixtures";

test("an invalid capability link explains itself without asking to log in", async ({
  page,
}) => {
  await page.goto("/p/documentos/token-que-no-existe");

  await expect(
    page.getByRole("heading", { level: 1, name: "Comprobante electrónico" }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Enlace no válido" }),
  ).toBeVisible();
  // No redirect to Keycloak, and no sidebar.
  await expect(page).toHaveURL(/\/p\/documentos\/token-que-no-existe$/);
  await expect(
    page.getByRole("navigation", { name: "Navegación" }),
  ).toHaveCount(0);
});
