import { expect, test } from "@support/fixtures";
import { readFixtures } from "@support/helpers/tenants";
import { ReceptionsPage } from "@support/pom/documentDataPages";

test("lists a company's receptions and filters them by issue date", async ({
  page,
}) => {
  // The setup's company received the factura it issued to itself.
  const fixtures = readFixtures();
  test.skip(!fixtures, "the setup prepared no company");
  const company = {
    tenantId: fixtures!.tenantId,
    companyId: fixtures!.companyId,
  };

  const receptions = new ReceptionsPage(page);
  await receptions.goto(company);
  await receptions.expectLoaded();

  const from = page.getByRole("textbox", { name: "Emitido desde" });
  await from.fill("01/01/2026");
  await expect(page).toHaveURL(/desde=2026-01-01/);

  // Not empty: the setup's company received a factura this year.
  const empty = /No hay comprobantes recibidos|No se encontraron resultados/;
  const firstRow = receptions.firstRowOrEmpty(empty);
  await expect(firstRow).toBeVisible();
  await expect(page.getByRole("heading", { name: empty })).toHaveCount(0);

  await firstRow
    .getByRole("button", { name: /acciones|Kebab toggle/i })
    .click();
  await page.getByRole("menuitem", { name: "Ver detalle" }).click();
  // Read by id: its validity as SUNAT answered last
  const detail = page.getByRole("dialog");
  await expect(detail.getByText("Validez en SUNAT")).toBeVisible();
  await expect(
    detail.getByRole("button", { name: "Descargar XML" }),
  ).toBeVisible();
});
