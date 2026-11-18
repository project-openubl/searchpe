import { expect, test } from "@support/fixtures";
import { DraftsPage } from "@support/pom/draftPages";

test("prepares a factura as a draft, saves it and issues it", async ({
  page,
  tenantsPage,
}) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  const drafts = new DraftsPage(page);
  await drafts.goto(tenantId!);
  await drafts.expectLoaded();

  await page.getByRole("button", { name: "Nuevo borrador" }).click();
  const dialog = page.getByRole("dialog", { name: "Nuevo borrador" });
  const company = dialog.getByRole("combobox", { name: "Empresa" });
  await expect(company).toBeEnabled();
  test.skip(
    (await company.getByRole("option").first().textContent()) ===
      "No hay empresas",
    "the organization has no company",
  );
  await dialog
    .getByRole("combobox", { name: "Tipo de comprobante" })
    .selectOption({ label: "Factura" });
  await dialog.getByRole("button", { name: "Crear borrador" }).click();

  // The draft's own page: no number yet.
  await expect(
    page.getByRole("heading", { level: 1, name: "Factura: borrador" }),
  ).toBeVisible();

  // Client-side validation first: SUNAT's RUC has 11 digits.
  await page
    .getByRole("textbox", { name: "Número de documento", exact: true })
    .fill("123");
  await page.getByRole("button", { name: "Guardar borrador" }).click();
  await expect(page.getByText("El RUC tiene 11 dígitos")).toBeVisible();

  await page
    .getByRole("textbox", { name: "Número de documento", exact: true })
    .fill("20123456789");
  await page
    .getByRole("textbox", { name: "Nombre o razón social", exact: true })
    .fill("Cliente E2E SAC");
  const line = page.getByRole("group", { name: "Detalle 1" });
  await line
    .getByRole("textbox", { name: "Descripción", exact: true })
    .fill("Servicio de prueba");
  await line
    .getByRole("textbox", { name: "Valor unitario (sin IGV)" })
    .fill("100");
  await page.getByRole("button", { name: "Guardar borrador" }).click();
  await expect(page.getByText("Se guardó el borrador")).toBeVisible();

  // The list shows it as a draft.
  await drafts.goto(tenantId!);
  await drafts.expectLoaded();
  const row = drafts.rows().filter({ hasText: "Cliente E2E SAC" }).first();
  await expect(row).toContainText("Borrador");
  await row.getByRole("link", { name: "Factura" }).click();

  // Issue it: numbered and signed by the service (the company is on the fake or beta SUNAT).
  await page.getByRole("button", { name: "Emitir", exact: true }).click();
  await page
    .getByRole("dialog", { name: "¿Emitir el comprobante?" })
    .getByRole("button", { name: "Emitir", exact: true })
    .click();
  const issued = page.getByText(
    "Este borrador ya se emitió: los comprobantes emitidos no se modifican.",
  );
  const refused = page.getByText("No se pudo emitir el comprobante");
  await expect(issued.or(refused)).toBeVisible();
  test.skip(
    await refused.isVisible(),
    "the company can't issue yet (certificate or plan)",
  );
  await expect(
    page.getByLabel("Comprobante emitido").getByText(/^F\w{3}-\d+$/),
  ).toBeVisible();
});

test("deletes a draft", async ({ page, tenantsPage }) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  const drafts = new DraftsPage(page);
  await drafts.goto(tenantId!);
  await drafts.expectLoaded();
  await page.getByRole("button", { name: "Nuevo borrador" }).click();
  const dialog = page.getByRole("dialog", { name: "Nuevo borrador" });
  const company = dialog.getByRole("combobox", { name: "Empresa" });
  await expect(company).toBeEnabled();
  test.skip(
    (await company.getByRole("option").first().textContent()) ===
      "No hay empresas",
    "the organization has no company",
  );
  await dialog
    .getByRole("combobox", { name: "Tipo de comprobante" })
    .selectOption({ label: "Boleta de venta" });
  await dialog.getByRole("button", { name: "Crear borrador" }).click();
  await expect(
    page.getByRole("heading", { level: 1, name: "Boleta de venta: borrador" }),
  ).toBeVisible();

  await page.getByRole("button", { name: "Eliminar borrador" }).click();
  await page
    .getByRole("dialog", { name: "¿Eliminar el borrador?" })
    .getByRole("button", { name: "Eliminar" })
    .click();
  await expect(drafts.heading).toBeVisible();
  await expect(page.getByText("Se eliminó el borrador")).toBeVisible();
});
