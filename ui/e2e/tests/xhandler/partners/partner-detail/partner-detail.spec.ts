import { PartnersPage, PartnerTenantsPage } from "@support/pom/partnerPages";
import { expect, test } from "@support/fixtures";

// A 1×1 PNG.
const PNG = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
  "base64",
);

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

test("saves the branding, reads it back and uploads a logo", async ({
  page,
}) => {
  const partner = await new PartnersPage(page).firstPartner();
  test.skip(!partner, "the e2e user belongs to no partner");

  await page.goto(`/socios/${partner!.id}`);
  await page.getByRole("tab", { name: "Marca" }).click();
  const save = page.getByRole("button", { name: "Guardar marca" });
  await expect(save).toBeVisible();
  test.skip(
    (await save.getAttribute("aria-disabled")) === "true",
    "the e2e user is no admin of the partner",
  );

  const footer = `Facturación e2e ${Date.now()}`;
  await page.getByRole("textbox", { name: "Pie de página" }).fill(footer);
  await save.click();
  await expect(page.getByText("Se guardó la marca")).toBeVisible();

  // The saved branding is read back on a fresh load
  await page.reload();
  await page.getByRole("tab", { name: "Marca" }).click();
  await expect(
    page.getByRole("textbox", { name: "Pie de página" }),
  ).toHaveValue(footer);

  await page.getByRole("button", { name: /^(Subir|Cambiar) logo$/ }).click();
  const dialog = page.getByRole("dialog", { name: "Subir logo" });
  const chooser = page.waitForEvent("filechooser");
  await dialog.getByRole("button", { name: "Elegir" }).click();
  await (
    await chooser
  ).setFiles({ name: "logo.png", mimeType: "image/png", buffer: PNG });
  await dialog.getByRole("button", { name: "Subir logo" }).click();
  await expect(page.getByText("Se actualizó el logo")).toBeVisible();
  await expect(page.getByRole("button", { name: "Quitar logo" })).toBeVisible();
});

test("an unknown partner is a 404 page", async ({ page }) => {
  await page.goto("/socios/00000000-0000-0000-0000-000000000000");
  await expect(
    page.getByRole("heading", { name: "404: La página no existe" }),
  ).toBeVisible();
});
