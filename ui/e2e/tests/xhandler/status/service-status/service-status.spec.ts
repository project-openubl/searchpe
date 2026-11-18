import { expect, test } from "@support/fixtures";
import { ServiceStatusPage } from "@support/pom/tenantAdminPages";

test("shows the service's components and queues", async ({ page }) => {
  const status = new ServiceStatusPage(page);
  await status.goto();
  await status.expectLoaded();
  await expect(
    page.getByRole("heading", { name: "Base de datos" }),
  ).toBeVisible();
  await expect(page.getByLabel("Versiones del servicio")).toContainText(
    "xhandler-service",
  );
  // SUNAT's availability per environment
  await expect(
    page.getByRole("heading", { name: "SUNAT", exact: true }),
  ).toBeVisible();
  for (const environment of ["Producción", "Beta", "Pruebas (fake)"]) {
    await expect(
      page.getByRole("heading", { name: environment, exact: true }),
    ).toBeVisible();
  }
});
