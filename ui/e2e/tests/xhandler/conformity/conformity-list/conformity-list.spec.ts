import {
  ConformityListPage,
  firstCompanyPath,
} from "@support/pom/companyTaxPages";
import { expect, test } from "@support/fixtures";

test("lists a company's facturas negociables", async ({ page }) => {
  const companyPath = await firstCompanyPath(page);
  test.skip(!companyPath, "the e2e user's organization has no company");

  const conformityPage = new ConformityListPage(page);
  await conformityPage.goto(companyPath!);
  await conformityPage.expectLoaded();
  await expect(
    conformityPage.firstRowOrEmpty("No hay facturas negociables"),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Exportar CSV" }),
  ).toBeVisible();
});
