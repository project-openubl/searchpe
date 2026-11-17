import { expect, test } from "@support/fixtures";
import { SEARCHPE_ENABLED } from "@support/helpers/services";

test.skip(!SEARCHPE_ENABLED, "searchpe isn't part of this stack");

test("an unknown contribuyente is a 404 page", async ({ page }) => {
  await page.goto("/padron/contribuyentes/99999999999");
  await expect(
    page.getByRole("heading", { name: "404: La página no existe" }),
  ).toBeVisible();
});
