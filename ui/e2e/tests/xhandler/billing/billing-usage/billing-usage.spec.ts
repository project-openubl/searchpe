import { expect, test } from "@support/fixtures";

test("shows the plan, usage and billing details", async ({
  page,
  tenantsPage,
}) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  await page.goto(`/organizaciones/${tenantId}/uso`);
  await expect(
    page.getByRole("heading", { level: 1, name: "Uso y plan" }),
  ).toBeVisible();
  await expect(page.getByLabel("Datos del plan")).toBeVisible();

  await page.getByRole("textbox", { name: "RUC" }).fill("123");
  await page.getByRole("textbox", { name: "Razón social" }).click();
  await expect(page.getByText("El RUC tiene 11 dígitos")).toBeVisible();
});
