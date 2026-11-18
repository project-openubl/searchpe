import { expect, test } from "@support/fixtures";
import { EmailSettingsPage } from "@support/pom/documentDataPages";

test("adds a sender domain and shows the TXT record to publish", async ({
  page,
  tenantsPage,
}) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  const email = new EmailSettingsPage(page);
  await email.goto(tenantId!);
  await email.expectLoaded();
  await expect(email.suppressions).toBeVisible();

  // Domains list by name and the stack outlives the run: newer runs sort first.
  const domain = `0e2e-${9_999_999_999_999 - Date.now()}.example.pe`;
  await page.getByRole("button", { name: "Nuevo dominio" }).click();
  await page.getByRole("textbox", { name: "Dominio" }).fill(domain);
  await page.getByRole("button", { name: "Agregar" }).click();

  const row = email.table.getByRole("row").filter({ hasText: domain });
  await expect(row.getByText("Sin verificar")).toBeVisible();
  await expect(
    row.getByRole("textbox", { name: "Valor del registro TXT" }),
  ).toBeVisible();
});
