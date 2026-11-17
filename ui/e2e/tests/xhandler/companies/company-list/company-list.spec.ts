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

  const firstCompany = companiesPage.table.getByRole("link").first();
  if ((await firstCompany.count()) === 0) {
    await expect(
      page.getByRole("heading", {
        name: "Esta organización aún no tiene empresas",
      }),
    ).toBeVisible();
    return;
  }
  const ruc = await firstCompany.textContent();
  await firstCompany.click();
  await expect(page.getByLabel("Datos de la empresa")).toContainText(ruc ?? "");
});
