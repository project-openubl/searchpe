import { expect, test } from "@support/fixtures";
import {
  SEARCHPE_ENABLED,
  SEARCHPE_IMPORT_ENABLED,
} from "@support/helpers/services";

test.skip(!SEARCHPE_ENABLED, "searchpe isn't part of this stack");

test("lists the padrón versions and offers a new one", async ({
  page,
  versionsPage,
}) => {
  await versionsPage.goto();
  await versionsPage.expectLoaded();

  await expect(
    versionsPage
      .rows()
      .first()
      .or(page.getByRole("heading", { name: "No hay versiones del padrón" })),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Nueva versión" }),
  ).toBeVisible();
});

test("creates a version and shows its import status", async ({
  page,
  versionsPage,
}) => {
  test.skip(
    !SEARCHPE_IMPORT_ENABLED,
    "creating a version downloads SUNAT's padrón; set E2E_SEARCHPE_IMPORT=true to run it",
  );
  test.slow();
  await versionsPage.goto();
  await versionsPage.expectLoaded();

  const created = page.waitForResponse(
    (response) =>
      response.url().endsWith("/api/searchpe/versions") &&
      response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Nueva versión" }).click();
  const response = await created;
  expect(response.ok()).toBe(true);
  const { id } = (await response.json()) as { id: number };

  await expect(
    page.getByText(`Se programó la importación de la versión #${id}`),
  ).toBeVisible();
  await expect(versionsPage.rows().filter({ hasText: `#${id}` })).toBeVisible();
});
