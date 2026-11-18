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

  await jobsPage.chooseFilter("Nombre", "Estado");
  await page.getByRole("button", { name: "Filtrar por estado" }).click();
  await page.getByRole("option", { name: "Fallido" }).click();
  await expect(page).toHaveURL(/estado=failed/);

  // One state at a time: a second pick replaces the first.
  await page.getByRole("button", { name: "Filtrar por estado" }).click();
  await page.getByRole("option", { name: "En espera" }).click();
  await expect(page).toHaveURL(/estado=waiting/);
  await expect(page).not.toHaveURL(/estado=failed/);
});
