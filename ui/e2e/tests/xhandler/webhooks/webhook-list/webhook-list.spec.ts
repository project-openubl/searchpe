import { expect, test } from "@support/fixtures";
import { WebhooksPage } from "@support/pom/tenantAdminPages";

test("lists the organization's webhooks and validates a new one", async ({
  page,
  tenantsPage,
}) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  const webhooks = new WebhooksPage(page);
  await webhooks.goto(tenantId!);
  await webhooks.expectLoaded();

  await page.getByRole("button", { name: "Nuevo webhook" }).click();
  const dialog = page.getByRole("dialog", { name: "Nuevo webhook" });
  await dialog.getByRole("textbox", { name: "URL" }).fill("http://erp.pe/x");
  await dialog.getByRole("button", { name: "Crear webhook" }).click();
  await expect(dialog.getByText("La URL debe usar https")).toBeVisible();
  await dialog.getByRole("button", { name: "Cancelar" }).click();
  await expect(dialog).toBeHidden();
});
