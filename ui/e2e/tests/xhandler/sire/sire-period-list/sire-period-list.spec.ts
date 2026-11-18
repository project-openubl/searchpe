import {
  firstCompanyPath,
  SirePeriodListPage,
} from "@support/pom/companyTaxPages";
import { expect, test } from "@support/fixtures";

test("lists a company's requested SIRE periods and filters them by register", async ({
  page,
}) => {
  const companyPath = await firstCompanyPath(page);
  test.skip(!companyPath, "the e2e user's organization has no company");

  // Reached from the SIRE page; nothing is requested (that would call SUNAT or the fake).
  await page.goto(`${companyPath}/sire`);
  await page
    .getByRole("link", { name: "Ver los periodos solicitados" })
    .click();
  const periods = new SirePeriodListPage(page);
  await periods.expectLoaded();
  await expect(page).toHaveURL(/\/sire\/periodos/);

  // A fresh stack has requested none: the empty state is a settled list too.
  const empty =
    /No hay periodos de SIRE solicitados|No se encontraron resultados/;
  await expect(periods.firstRowOrEmpty(empty)).toBeVisible();

  await page.getByRole("button", { name: "Filtrar por registro" }).click();
  await page.getByRole("option", { name: "Compras (RCE)" }).click();
  await expect(page).toHaveURL(/libro=rce/);
  await expect(periods.firstRowOrEmpty(empty)).toBeVisible();
});
