import { firstCompanyPath } from "@support/pom/companyTaxPages";
import { expect, test } from "@support/fixtures";

test("an unknown factura is a 404 page", async ({ page }) => {
  const companyPath = await firstCompanyPath(page);
  test.skip(!companyPath, "the e2e user's organization has no company");

  await page.goto(
    `${companyPath}/conformidad/00000000-0000-0000-0000-000000000000`,
  );
  await expect(
    page.getByRole("heading", { name: "404: La página no existe" }),
  ).toBeVisible();
});
