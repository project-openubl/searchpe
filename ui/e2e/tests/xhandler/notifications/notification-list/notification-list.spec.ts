import { expect, test } from "@support/fixtures";
import { NotificationsPage } from "@support/pom/tenantAdminPages";

test("lists notifications and keeps the unread filter in the URL", async ({
  page,
  tenantsPage,
}) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  const notifications = new NotificationsPage(page);
  await notifications.goto(tenantId!);
  await notifications.expectLoaded();

  await page.getByText("Solo no leídas").click();
  await expect(page).toHaveURL(/noLeidas=true/);

  await page.getByRole("button", { name: "Marcar todas como leídas" }).click();
  await expect(
    page.getByText("Se marcaron todas las notificaciones como leídas"),
  ).toBeVisible();
});
