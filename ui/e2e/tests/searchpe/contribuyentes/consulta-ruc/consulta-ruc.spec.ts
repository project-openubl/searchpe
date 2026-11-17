import { expect, test } from "@support/fixtures";
import { SEARCHPE_ENABLED } from "@support/helpers/services";

test.skip(!SEARCHPE_ENABLED, "searchpe isn't part of this stack");

test.describe("consulta RUC", () => {
  test("validates the number before searching", async ({ page }) => {
    await page.goto("/padron/consulta");
    const input = page.getByRole("textbox", { name: "Número de documento" });

    await input.fill("123");
    await expect(page.getByRole("button", { name: "Buscar" })).toBeDisabled();
    await input.fill("12345678");
    await expect(page.getByRole("button", { name: "Buscar" })).toBeEnabled();
  });

  test("keeps the searched number in the URL and answers found or not found", async ({
    page,
  }) => {
    await page.goto("/padron/consulta");
    await page
      .getByRole("textbox", { name: "Número de documento" })
      .fill("20000000001");
    await page.getByRole("button", { name: "Buscar" }).click();

    await expect(page).toHaveURL(/numero=20000000001/);
    // The padrón of the stack may or may not have it; either answer is a finished search.
    await expect(
      page
        .getByRole("heading", { name: "No se encontraron resultados" })
        .or(page.getByLabel("Datos del contribuyente")),
    ).toBeVisible();
  });
});
