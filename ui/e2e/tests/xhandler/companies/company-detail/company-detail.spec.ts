import { expect, test } from "@support/fixtures";

test("an unknown company is a 404 page", async ({ page, tenantsPage }) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  await page.goto(
    `/organizaciones/${tenantId}/empresas/00000000-0000-0000-0000-000000000000`,
  );
  await expect(
    page.getByRole("heading", { name: "404: La página no existe" }),
  ).toBeVisible();
});

test("a foreign organization is a 404 too", async ({ page }) => {
  await page.goto(
    "/organizaciones/00000000-0000-0000-0000-000000000000/empresas/00000000-0000-0000-0000-000000000000",
  );
  await expect(
    page.getByRole("heading", { name: "404: La página no existe" }),
  ).toBeVisible();
});

test("shows a company's administration tabs", async ({
  page,
  tenantsPage,
  companiesPage,
}) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");
  await companiesPage.goto(tenantId!);
  await companiesPage.expectLoaded();
  // The setup registered one when the organization had none.
  const firstCompany = companiesPage.table.getByRole("link").first();
  await firstCompany.click();

  await page.getByRole("tab", { name: "Series" }).click();
  await expect(page).toHaveURL(/pestana=series/);
  await expect(
    page.getByRole("grid", { name: "Tabla de series" }),
  ).toBeVisible();

  // Secrets are write-only: the table lists every kind, never a value.
  await page.getByRole("tab", { name: "Credenciales" }).click();
  const credentials = page.getByRole("grid", { name: "Tabla de credenciales" });
  await expect(credentials.getByText("Clave SOL")).toBeVisible();

  await page.getByRole("tab", { name: "Envío a SUNAT" }).click();
  await expect(page.getByText("Obligada a emitir mediante OSE")).toBeVisible();

  await page.getByRole("tab", { name: "Certificado" }).click();
  await expect(
    page.getByRole("heading", { name: "Certificado digital", exact: true }),
  ).toBeVisible();
});

test("creates a series, moves its next number forward and deactivates it", async ({
  page,
  tenantsPage,
  companiesPage,
}) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");
  await companiesPage.goto(tenantId!);
  await companiesPage.expectLoaded();
  await companiesPage.table.getByRole("link").first().click();
  await page.getByRole("tab", { name: "Series" }).click();
  const series = page.getByRole("grid", { name: "Tabla de series" });
  await expect(series).toBeVisible();

  // A series of its own (the shared stack keeps the default ones untouched).
  const serie = `Z${Math.floor(Math.random() * 36 ** 3)
    .toString(36)
    .toUpperCase()
    .padStart(3, "0")}`;
  await page.getByRole("button", { name: "Nueva serie" }).click();
  await page.getByRole("textbox", { name: "Serie", exact: true }).fill(serie);
  await page.getByRole("button", { name: "Crear serie" }).click();
  const row = series.getByRole("row").filter({ hasText: serie });
  await expect(row).toBeVisible();

  // Numbers only move forward.
  await row.getByRole("button", { name: /acciones|kebab/i }).click();
  await page.getByRole("menuitem", { name: "Editar" }).click();
  const nextNumber = page.getByRole("textbox", {
    name: "Siguiente número",
    exact: true,
  });
  await nextNumber.fill("0");
  await page.getByRole("button", { name: "Guardar" }).click();
  await expect(
    page.getByText("El siguiente número no puede ser menor que 1"),
  ).toBeVisible();
  await nextNumber.fill("250");
  await page.getByRole("button", { name: "Guardar" }).click();
  await expect(row).toContainText("250");

  await row.getByRole("button", { name: /acciones|kebab/i }).click();
  await page.getByRole("menuitem", { name: "Desactivar" }).click();
  await page.getByRole("button", { name: "Desactivar serie" }).click();
  await expect(row).toContainText("Inactiva");

  await row.getByRole("button", { name: /acciones|kebab/i }).click();
  await page.getByRole("menuitem", { name: "Activar" }).click();
  await expect(row).not.toContainText("Inactiva");
});
