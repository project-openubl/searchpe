import { expect, test } from "@support/fixtures";
import { randomRuc } from "@support/pom/tenantAdminPages";

test("registers a company and opens it", async ({ page, tenantsPage }) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  await page.goto(`/organizaciones/${tenantId}/empresas`);
  await page.getByRole("button", { name: "Nueva empresa" }).click();
  await expect(
    page.getByRole("heading", { level: 1, name: "Nueva empresa" }),
  ).toBeVisible();

  // Client-side validation first: SUNAT's RUC has 11 digits.
  await page.getByRole("textbox", { name: "RUC" }).fill("123");
  await page.getByRole("button", { name: "Registrar empresa" }).click();
  await expect(page.getByText("El RUC tiene 11 dígitos")).toBeVisible();

  const ruc = randomRuc();
  await page.getByRole("textbox", { name: "RUC" }).fill(ruc);
  await page
    .getByRole("textbox", { name: "Razón social" })
    .fill(`E2E ${ruc} SAC`);
  await page.getByRole("button", { name: "Registrar empresa" }).click();

  // A tenant may hold only 3 unverified companies (DESIGN.md §26.8).
  const refused = page.getByText("No se pudo registrar la empresa");
  const detail = page.getByLabel("Datos de la empresa");
  await expect(refused.or(detail)).toBeVisible();
  test.skip(
    await refused.isVisible(),
    "the organization reached its unverified-company limit",
  );
  await expect(detail).toContainText(ruc);
});
