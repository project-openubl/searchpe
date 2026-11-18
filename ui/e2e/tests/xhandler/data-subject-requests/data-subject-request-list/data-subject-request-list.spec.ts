import { expect, test } from "@support/fixtures";
import { DataSubjectRequestsPage } from "@support/pom/documentDataPages";

test("registers a personal-data request, reads its data and closes it", async ({
  page,
  tenantsPage,
}) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  const requests = new DataSubjectRequestsPage(page);
  await requests.goto(tenantId!);
  await requests.expectLoaded();

  // A random DNI: the stack outlives the run.
  const dni = String(10_000_000 + Math.floor(Math.random() * 89_999_999));
  await page.getByRole("button", { name: "Nueva solicitud" }).click();
  await page.getByRole("textbox", { name: "Número de documento" }).fill(dni);
  await page
    .getByRole("textbox", { name: "Nombre del titular" })
    .fill("Titular E2E");
  await page.getByRole("button", { name: "Registrar" }).click();

  const row = requests.table.getByRole("row").filter({ hasText: dni });
  await expect(row.getByText("Abierta")).toBeVisible();

  await row.getByRole("button", { name: /acciones|Kebab toggle/i }).click();
  await page.getByRole("menuitem", { name: "Ver datos personales" }).click();
  const personalData = page.getByRole("dialog", {
    name: /Datos personales de Titular E2E/,
  });
  await expect(
    personalData.getByText("No hay comprobantes emitidos a esta persona."),
  ).toBeVisible();
  await personalData.getByRole("button", { name: "Cerrar" }).click();

  await row.getByRole("button", { name: /acciones|Kebab toggle/i }).click();
  await page.getByRole("menuitem", { name: "Cerrar" }).click();
  await page
    .getByRole("textbox", { name: "Respuesta" })
    .fill("Atendida por e2e.");
  await page.getByRole("button", { name: "Cerrar solicitud" }).click();
  await expect(row.getByText("Cerrada")).toBeVisible();
});
