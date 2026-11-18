import {
  firstCompanyPath,
  PseAuthorizationPage,
} from "@support/pom/companyTaxPages";
import { expect, test } from "@support/fixtures";

test("shows a company's emission mode and PSE authorizations", async ({
  page,
}) => {
  const companyPath = await firstCompanyPath(page);
  test.skip(!companyPath, "the e2e user's organization has no company");

  const psePage = new PseAuthorizationPage(page);
  await psePage.goto(companyPath!);
  await expect(psePage.heading).toBeVisible();
  await expect(psePage.history).toBeVisible();
  await expect(page.getByText("Modo de emisión")).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Registrar autorización" }),
  ).toBeVisible();
});
