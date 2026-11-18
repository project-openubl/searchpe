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
