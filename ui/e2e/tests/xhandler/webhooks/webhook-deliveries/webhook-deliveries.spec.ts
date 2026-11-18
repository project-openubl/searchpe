import { expect, test } from "@support/fixtures";
import { WebhooksPage } from "@support/pom/tenantAdminPages";

test("opens a webhook's deliveries from the list", async ({
  page,
  tenantsPage,
}) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  const webhooks = new WebhooksPage(page);
  await webhooks.goto(tenantId!);
  await webhooks.expectLoaded();
  const first = webhooks.table.getByRole("link").first();
  test.skip((await first.count()) === 0, "the organization has no webhook");

  await first.click();
  await expect(
    page.getByRole("heading", { level: 1, name: "Entregas del webhook" }),
  ).toBeVisible();
  await expect(
    page.getByRole("grid", { name: "Tabla de entregas" }),
  ).toBeVisible();
});

test("an unknown webhook is a 404 page", async ({ page, tenantsPage }) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  await page.goto(
    `/organizaciones/${tenantId}/webhooks/00000000-0000-0000-0000-000000000000`,
  );
  await expect(
    page.getByRole("heading", { name: "404: La página no existe" }),
  ).toBeVisible();
});
