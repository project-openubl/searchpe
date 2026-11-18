import { expect, test } from "@support/fixtures";
import { firstCompany, ReceptionsPage } from "@support/pom/documentDataPages";

test("lists a company's receptions and filters them by issue date", async ({
  page,
}) => {
  const company = await firstCompany(page);
  test.skip(!company, "the e2e user's organization has no company");

  const receptions = new ReceptionsPage(page);
  await receptions.goto(company!);
  await receptions.expectLoaded();

  const from = page.getByRole("textbox", { name: "Emitido desde" });
  await from.fill("01/01/2026");
  await expect(page).toHaveURL(/desde=2026-01-01/);

  const firstRow = receptions.rows().first();
  const empty = page.getByRole("heading", {
    name: /No hay comprobantes recibidos|No se encontraron resultados/,
  });
  await expect(firstRow.or(empty).first()).toBeVisible();
  test.skip(
    (await empty.count()) > 0,
    "the company has received no documents in the period",
  );

  await firstRow
    .getByRole("button", { name: /acciones|Kebab toggle/i })
    .click();
  await page.getByRole("menuitem", { name: "Ver detalle" }).click();
  await expect(
    page.getByRole("button", { name: "Descargar XML" }),
  ).toBeVisible();
});
