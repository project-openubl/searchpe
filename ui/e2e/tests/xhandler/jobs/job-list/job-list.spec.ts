import { expect, test } from "@support/fixtures";

test("lists an organization's jobs and filters them by state", async ({
  page,
  tenantsPage,
  jobsPage,
}) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  await jobsPage.goto(tenantId!);
  await jobsPage.expectLoaded();

  await page.getByRole("button", { name: "Filtrar por estado" }).click();
  await page.getByRole("menuitem", { name: "Fallido" }).click();
  await expect(page).toHaveURL(/estado=failed/);
});
