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
