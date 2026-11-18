import { expect, test } from "@support/fixtures";

test("the OSE validity page opens without a session", async ({ page }) => {
  await page.goto("/consulta-validez");

  await expect(
    page.getByRole("heading", {
      level: 1,
      name: "Consulta de validez de comprobantes",
    }),
  ).toBeVisible();
  await expect(page).toHaveURL(/\/consulta-validez$/);
});

test("anyone checks a comprobante's validity", async ({ page }) => {
  await page.goto("/consulta-validez");

  await page
    .getByRole("textbox", { name: "RUC del emisor" })
    .fill("20000000001");
  await page.getByRole("textbox", { name: "Serie" }).fill("F999");
  await page.getByRole("textbox", { name: "Número" }).fill("1");
  // xhandler-service's CAPTCHA test mode (dev profile) takes this token.
  await page
    .getByRole("textbox", { name: "Código de verificación" })
    .fill("test-pass");
  await page.getByRole("button", { name: "Consultar" }).click();

  // A comprobante nobody sent, or a stack whose CAPTCHA isn't in test mode.
  await expect(
    page
      .getByText("Comprobante no encontrado")
      .or(page.getByText("No se pudo consultar el comprobante")),
  ).toBeVisible();
  await expect(page).toHaveURL(/\/consulta-validez$/);
});
