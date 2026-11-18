import { expect, test } from "@support/fixtures";
import { DocumentsPage } from "@support/pom/documentDataPages";

test("lists the organization's documents, filters them and opens one", async ({
  page,
  tenantsPage,
}) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  const documents = new DocumentsPage(page);
  await documents.goto(tenantId!);
  await documents.expectLoaded();

  await documents.chooseFilter("Serie", "Estado");
  await page.getByRole("button", { name: "Filtrar por estado" }).click();
  await page.getByRole("option", { name: "Aceptado", exact: true }).click();
  await expect(page).toHaveURL(/estado=accepted/);

  await page.getByRole("button", { name: "Limpiar filtros" }).first().click();
  await expect(page).not.toHaveURL(/estado=/);

  // The setup issued one.
  const firstDocument = documents.table.getByRole("link").first();

  await firstDocument.click();
  await expect(
    page.getByRole("heading", { name: "Estado en SUNAT" }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Línea de tiempo" }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Descargar PDF" }),
  ).toBeVisible();
});

test("an unknown document is a 404 page", async ({ page, tenantsPage }) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  await page.goto(
    `/organizaciones/${tenantId}/documentos/00000000-0000-0000-0000-000000000000`,
  );
  await expect(
    page.getByRole("heading", { name: "404: La página no existe" }),
  ).toBeVisible();
});
