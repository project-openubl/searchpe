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

test("adds an API client, changes its role and removes it", async ({
  page,
  tenantsPage,
}) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");
  const clientId = `e2e-erp-${Date.now()}`;

  const members = new MembersPage(page);
  await members.goto(tenantId!);
  await members.expectLoaded();

  await page.getByRole("button", { name: "Agregar miembro" }).click();
  const add = page.getByRole("dialog", { name: "Agregar miembro" });
  await add.getByRole("radio", { name: "Cliente de API" }).check();
  await add
    .getByRole("textbox", { name: "Client ID", exact: true })
    .fill(clientId);
  await add.getByRole("combobox", { name: "Rol" }).selectOption("viewer");
  await add.getByRole("button", { name: "Agregar" }).click();
  await expect(add).toBeHidden();
  await expect(members.row(clientId)).toContainText("Lector");

  await members.rowAction(clientId, "Cambiar rol");
  const change = page.getByRole("dialog", { name: "Cambiar rol" });
  await change.getByRole("combobox", { name: "Rol" }).selectOption("operator");
  await change.getByRole("button", { name: "Guardar" }).click();
  await expect(change).toBeHidden();
  await expect(members.row(clientId)).toContainText("Operador");

  await members.rowAction(clientId, "Quitar");
  const confirm = page.getByRole("dialog", { name: "¿Quitar al miembro?" });
  await confirm.getByRole("button", { name: "Quitar", exact: true }).click();
  await expect(confirm).toBeHidden();
  await expect(members.row(clientId)).toHaveCount(0);
});
