import { expect, test } from "@support/fixtures";

test("shows the organization's data", async ({ page, tenantsPage }) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  await page.goto(`/organizaciones/${tenantId}/configuracion`);
  await expect(
    page.getByRole("heading", {
      level: 1,
      name: "Configuración de la organización",
    }),
  ).toBeVisible();
  await expect(page.getByLabel("Datos de la organización")).toContainText(
    "Propietario",
  );
});
