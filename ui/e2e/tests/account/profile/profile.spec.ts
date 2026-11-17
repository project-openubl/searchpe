import { expect, test } from "@support/fixtures";
import { E2E_USERNAME } from "@support/helpers/auth";
import { SEARCHPE_ENABLED } from "@support/helpers/services";

test("the profile shows the user, their permissions and a link to Keycloak", async ({
  page,
}) => {
  await page.goto("/perfil");

  await expect(
    page.getByRole("heading", { level: 1, name: "Mi perfil" }),
  ).toBeVisible();
  await expect(page.getByText(E2E_USERNAME, { exact: true })).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Gestionar mi cuenta" }),
  ).toHaveAttribute("href", /\/realms\/[^/]+\/account$/);
  if (SEARCHPE_ENABLED) {
    await expect(
      page.getByRole("list", { name: "Permisos en searchpe" }),
    ).toBeVisible();
  }
});
