import { expect, test } from "@support/fixtures";

test("shows the organization's data", async ({ page, tenantsPage }) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  await page.goto(`/organizaciones/${tenantId}/configuracion`);
  await expect(
    page.getByRole("heading", {
      level: 1,
      name: "Configuración de la organización",
    }),
  ).toBeVisible();
  await expect(page.getByLabel("Datos de la organización")).toContainText(
    "Propietario",
  );
});

test("renames the organization and keeps its notification email", async ({
  page,
  tenantsPage,
}) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  await page.goto(`/organizaciones/${tenantId}/configuracion`);
  const name = page.getByRole("textbox", { name: "Nombre", exact: true });
  await expect(name).not.toHaveValue("");
  const original = await name.inputValue();
  const email = `avisos-${Date.now()}@example.com`;

  await name.fill(`${original} (renombrada)`);
  await page.getByRole("button", { name: "Guardar nombre" }).click();
  await expect(page.getByLabel("Datos de la organización")).toContainText(
    `${original} (renombrada)`,
  );
  const correo = page.getByRole("textbox", { name: "Correo", exact: true });
  await correo.fill(email);
  await page.getByRole("button", { name: "Guardar", exact: true }).click();
  await expect(page.getByText(`Los avisos llegarán a ${email}`)).toBeVisible();

  // Both come back from the API after a reload
  await page.reload();
  await expect(name).toHaveValue(`${original} (renombrada)`);
  await expect(correo).toHaveValue(email);

  // Leave the shared organization as it was
  await name.fill(original);
  await page.getByRole("button", { name: "Guardar nombre" }).click();
  await expect(page.getByLabel("Datos de la organización")).toContainText(
    original,
  );
});
