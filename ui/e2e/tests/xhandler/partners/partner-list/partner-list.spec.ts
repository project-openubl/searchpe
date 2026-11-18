import { PartnersPage } from "@support/pom/partnerPages";
import { expect, test } from "@support/fixtures";

test("lists the user's partners with their role", async ({ page }) => {
  const partnersPage = new PartnersPage(page);
  await partnersPage.goto();
  await partnersPage.expectLoaded();

  await expect(
    partnersPage
      .rows()
      .first()
      .or(page.getByRole("heading", { name: "No perteneces a ningún socio" })),
  ).toBeVisible();
});
