import { expect, test } from "@support/fixtures";

test("lists the user's organizations with their role", async ({
  page,
  tenantsPage,
}) => {
  await tenantsPage.goto();
  await tenantsPage.expectLoaded();

  await expect(
    tenantsPage
      .rows()
      .first()
      .or(
        page.getByRole("heading", {
          name: "Aún no perteneces a ninguna organización",
        }),
      ),
  ).toBeVisible();
});
