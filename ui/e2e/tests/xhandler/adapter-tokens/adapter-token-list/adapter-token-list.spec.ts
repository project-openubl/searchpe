import { expect, test } from "@support/fixtures";
import {
  AdapterTokensPage,
  firstCompany,
} from "@support/pom/documentDataPages";

test("creates a Nubefact token, shows it once and revokes it", async ({
  page,
}) => {
  const company = await firstCompany(page);
  test.skip(!company, "the e2e user's organization has no company");

  const tokens = new AdapterTokensPage(page);
  await tokens.goto(company!);
  await tokens.expectLoaded();

  const label = `e2e ${Date.now()}`;
  await page.getByRole("button", { name: "Nuevo token" }).click();
  await page.getByRole("textbox", { name: "Etiqueta" }).fill(label);
  await page.getByRole("button", { name: "Crear", exact: true }).click();

  const created = page.getByRole("dialog", { name: "Token creado" });
  await expect(created).toBeVisible();
  await expect(
    created.getByText("Copia el token ahora: no se volverá a mostrar."),
  ).toBeVisible();
  await created.getByRole("button", { name: "Listo" }).click();

  const row = tokens.table.getByRole("row").filter({ hasText: label });
  await expect(row.getByText("Activo")).toBeVisible();
  await row.getByRole("button", { name: /acciones|Kebab toggle/i }).click();
  await page.getByRole("menuitem", { name: "Revocar" }).click();
  await page.getByRole("button", { name: "Revocar token" }).click();
  await expect(row.getByText(/Revocado el/)).toBeVisible();
});
