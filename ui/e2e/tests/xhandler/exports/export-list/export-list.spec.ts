import { expect, test } from "@support/fixtures";
import { ExportsPage } from "@support/pom/documentDataPages";

// Requesting an export starts a job and allows one per hour: the spec only reads.
test("lists the organization's exports and guards leaving the platform", async ({
  page,
  tenantsPage,
}) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  const exports = new ExportsPage(page);
  await exports.goto(tenantId!);
  await exports.expectLoaded();
  await expect(
    page.getByRole("button", { name: "Nueva exportación" }),
  ).toBeVisible();

  await page.getByRole("button", { name: "Dejar la plataforma" }).click();
  const dialog = page.getByRole("dialog", { name: "¿Dejar la plataforma?" });
  await expect(dialog).toBeVisible();
  // The organization's name must be typed before leaving is possible.
  await expect(
    dialog.getByRole("button", { name: "Dejar la plataforma" }),
  ).toBeDisabled();
  await dialog.getByRole("button", { name: "Volver" }).click();
  await expect(dialog).toBeHidden();
});
