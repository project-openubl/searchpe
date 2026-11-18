import { expect, test } from "@support/fixtures";

test("lists the user's organizations with their role", async ({
  tenantsPage,
}) => {
  await tenantsPage.goto();
  await tenantsPage.expectLoaded();

  // The setup signed the e2e user up for one: they own it.
  await expect(
    tenantsPage.rows().filter({ hasText: "Propietario" }).first(),
  ).toBeVisible();
});
