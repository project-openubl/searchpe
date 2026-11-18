import { expect, test } from "@support/fixtures";

test("lists an organization's companies and opens one", async ({
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
  await expect(firstCompany).toBeVisible();
  const ruc = await firstCompany.textContent();
  await firstCompany.click();
  await expect(page.getByLabel("Datos de la empresa")).toContainText(ruc ?? "");
});
