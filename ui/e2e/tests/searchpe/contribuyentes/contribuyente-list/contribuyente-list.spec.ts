import { expect, test } from "@support/fixtures";
import { SEARCHPE_ENABLED } from "@support/helpers/services";

test.skip(!SEARCHPE_ENABLED, "searchpe isn't part of this stack");

test("lists contribuyentes or explains the search is disabled; filters go to the URL", async ({
  page,
  contribuyentesPage,
}) => {
  await contribuyentesPage.goto();
  await expect(contribuyentesPage.heading).toBeVisible();

  const disabled = page.getByText(
    "La búsqueda de contribuyentes está desactivada",
  );
  // searchpe.allow.advancedSearch=false (its default) answers 400.
  if (await disabled.isVisible()) {
    test.skip(true, "searchpe runs with searchpe.allow.advancedSearch=false");
  }
  await expect(contribuyentesPage.table).toBeVisible();

  await page.getByPlaceholder("Filtrar por nombre, RUC o DNI").fill("SAC");
  await expect(page).toHaveURL(/texto=SAC/);
});
