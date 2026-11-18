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
  // The setup created one, unless the organization's plan has no webhooks.
  await expect(webhooks.firstRowOrEmpty("No hay webhooks")).toBeVisible();
  const first = webhooks.table.getByRole("link").first();
  test.skip(
    (await first.count()) === 0,
    "the organization has no webhook (its plan has none)",
  );

  const url = (await first.textContent())?.trim() ?? "";
  await first.click();
  await expect(
    page.getByRole("heading", { level: 1, name: "Entregas del webhook" }),
  ).toBeVisible();
  // The header reads the webhook by id
  await expect(page.getByText(url, { exact: true })).toBeVisible();
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
