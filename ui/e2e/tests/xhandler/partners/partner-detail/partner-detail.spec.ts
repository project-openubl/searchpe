import { PartnersPage, PartnerTenantsPage } from "@support/pom/partnerPages";
import { expect, test } from "@support/fixtures";

test("shows a partner's organizations and its branding form", async ({
  page,
}) => {
  const partner = await new PartnersPage(page).firstPartner();
  test.skip(!partner, "the e2e user belongs to no partner");

  const tenantsPage = new PartnerTenantsPage(page, partner!.name);
  await tenantsPage.goto(partner!.id);
  await tenantsPage.expectLoaded();
  await expect(
    page.getByRole("button", { name: "Nueva organización" }),
  ).toBeVisible();

  await page.getByRole("tab", { name: "Marca" }).click();
  await expect(
    page.getByRole("button", { name: "Guardar marca" }),
  ).toBeVisible();
});

test("an unknown partner is a 404 page", async ({ page }) => {
  await page.goto("/socios/00000000-0000-0000-0000-000000000000");
  await expect(
    page.getByRole("heading", { name: "404: La página no existe" }),
  ).toBeVisible();
});
