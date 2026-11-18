import { expect, test } from "@support/fixtures";
import { MembersPage } from "@support/pom/tenantAdminPages";

test("lists the organization's members and validates a new one", async ({
  page,
  tenantsPage,
}) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  const members = new MembersPage(page);
  await members.goto(tenantId!);
  await members.expectLoaded();
  // The e2e user signed up the organization: it is listed as its owner.
  await expect(members.table.getByText("Propietario").first()).toBeVisible();

  await page.getByRole("button", { name: "Agregar miembro" }).click();
  const dialog = page.getByRole("dialog", { name: "Agregar miembro" });
  await dialog.getByRole("button", { name: "Agregar" }).click();
  await expect(dialog.getByText("Ingresa el identificador")).toBeVisible();
  await dialog.getByRole("button", { name: "Cancelar" }).click();
  await expect(dialog).toBeHidden();
});
