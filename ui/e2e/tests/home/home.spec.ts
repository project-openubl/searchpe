import { expect, test } from "@support/fixtures";

test("the home page greets the user and shows each product's availability", async ({
  page,
}) => {
  await page.goto("/");

  await expect(
    page.getByRole("heading", { level: 1, name: /^Hola,/ }),
  ).toBeVisible();
  await expect(
    page.getByText("Comprobantes electrónicos").first(),
  ).toBeVisible();
  await expect(page.getByText("Disponible").first()).toBeVisible();
});
