import { expect, test } from "@support/fixtures";
import { firstCompany, ReportsPage } from "@support/pom/documentDataPages";

test("lists a company's reports and validates a new one", async ({ page }) => {
  const company = await firstCompany(page);
  test.skip(!company, "the e2e user's organization has no company");

  const reports = new ReportsPage(page);
  await reports.goto(company!);
  await reports.expectLoaded();

  await page.getByRole("button", { name: "Nuevo reporte" }).click();
  const dialog = page.getByRole("dialog", { name: "Nuevo reporte" });
  await expect(dialog).toBeVisible();
  await dialog.getByRole("button", { name: "Solicitar" }).click();
  await expect(dialog.getByText("Elige el periodo.")).toBeVisible();
  await dialog.getByRole("button", { name: "Cancelar" }).click();
  await expect(dialog).toBeHidden();
});
