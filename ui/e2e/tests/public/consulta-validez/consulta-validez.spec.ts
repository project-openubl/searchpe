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
