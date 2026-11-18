import { firstCompanyPath, SirePeriodPage } from "@support/pom/companyTaxPages";
import { expect, test } from "@support/fixtures";

test("opens a register and period from the address", async ({ page }) => {
  const companyPath = await firstCompanyPath(page);
  test.skip(!companyPath, "the e2e user's organization has no company");

  const sirePage = new SirePeriodPage(page);
  // Nothing is requested: SIRE calls SUNAT (or the fake) only on the user's action.
  await sirePage.goto(companyPath!, "?libro=rce&periodo=202401");
  await expect(sirePage.heading).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Compras (RCE)", pressed: true }),
  ).toBeVisible();
  await expect(page.getByRole("combobox", { name: "Periodo" })).toHaveValue(
    "202401",
  );
  await expect(
    page
      .getByRole("heading", {
        name: "Aún no se solicitó la propuesta de este periodo",
      })
      .or(page.getByText("Estado del periodo")),
  ).toBeVisible();
});
