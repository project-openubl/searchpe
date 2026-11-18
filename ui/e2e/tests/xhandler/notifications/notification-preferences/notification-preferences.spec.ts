import { expect, test } from "@support/fixtures";
import { NotificationPreferencesPage } from "@support/pom/tenantAdminPages";

test("shows every notification kind with its channels", async ({
  page,
  tenantsPage,
}) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  const preferences = new NotificationPreferencesPage(page);
  await preferences.goto(tenantId!);
  await preferences.expectLoaded();
  await expect(
    preferences.table.getByText("Certificado por vencer"),
  ).toBeVisible();
  await expect(
    page.getByRole("switch", { name: "Correo: Certificado por vencer" }),
  ).toBeVisible();
});
