import { expect, test } from "@support/fixtures";

test("an invalid capability link explains itself without asking to log in", async ({
  page,
}) => {
  await page.goto("/p/documentos/token-que-no-existe");

  await expect(
    page.getByRole("heading", { level: 1, name: "Comprobante electrónico" }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Enlace no válido" }),
  ).toBeVisible();
  // No redirect to Keycloak, and no sidebar.
  await expect(page).toHaveURL(/\/p\/documentos\/token-que-no-existe$/);
  await expect(
    page.getByRole("navigation", { name: "Navegación" }),
  ).toHaveCount(0);
});

// A real capability link comes from an emit (`access_url`); the public project has no session to
// emit one, so the stack provides it.
const DOCUMENT_TOKEN = process.env.E2E_PUBLIC_DOCUMENT_TOKEN;

test("a capability link shows the document and downloads its PDF without a session", async ({
  page,
}) => {
  test.skip(
    !DOCUMENT_TOKEN,
    "no E2E_PUBLIC_DOCUMENT_TOKEN: the stack has no issued document link",
  );
  await page.goto(`/p/documentos/${DOCUMENT_TOKEN}`);

  await expect(
    page.getByRole("heading", { level: 1, name: "Comprobante electrónico" }),
  ).toBeVisible();
  await expect(page.getByLabel("Datos del comprobante")).toBeVisible();
  const download = page.waitForEvent("download");
  await page.getByRole("button", { name: "Descargar PDF" }).click();
  expect((await download).suggestedFilename()).toMatch(/\.pdf$/);
  await expect(page).toHaveURL(new RegExp(`/p/documentos/${DOCUMENT_TOKEN}$`));
});
