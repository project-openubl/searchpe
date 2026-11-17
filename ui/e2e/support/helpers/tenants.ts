import { expect, type Page } from "@playwright/test";

import { E2E_USERNAME } from "./auth";

/** The logged-in user's access token, from the app's OIDC session (sessionStorage). */
const accessToken = async (page: Page): Promise<string> => {
  const token = await page.evaluate(() => {
    for (const key of Object.keys(sessionStorage)) {
      if (key.startsWith("oidc.user:")) {
        const user = JSON.parse(sessionStorage.getItem(key) ?? "{}") as {
          access_token?: string;
        };
        return user.access_token;
      }
    }
    return undefined;
  });
  expect(token, "the login left no OIDC session").toBeTruthy();
  return token!;
};

/** Statuses a sign-up may answer when the user can't (or needn't) create another tenant. */
const SIGN_UP_REFUSALS = new Set([400, 403, 409, 422, 429]);

/**
 * Makes sure the e2e user belongs to an organization, so the organization specs run on a fresh
 * stack instead of skipping: signs the user up (`POST /api/v1/tenants`) when they have none.
 * "Already exists" or a sign-up limit is fine: the user's existing tenant is used.
 */
export const ensureTenant = async (page: Page) => {
  const headers = { Authorization: `Bearer ${await accessToken(page)}` };
  const myTenants = async () => {
    const response = await page.request.get("/api/xhandler/v1/tenants", {
      headers,
    });
    expect(response.ok(), `GET tenants: ${response.status()}`).toBe(true);
    return (await response.json()) as unknown[];
  };

  if ((await myTenants()).length > 0) return;

  const created = await page.request.post("/api/xhandler/v1/tenants", {
    headers,
    data: { name: `E2E ${E2E_USERNAME}` },
  });
  if (!created.ok() && !SIGN_UP_REFUSALS.has(created.status())) {
    throw new Error(
      `Creating the e2e tenant failed: ${created.status()} ${await created.text()}`,
    );
  }
  expect(
    (await myTenants()).length,
    "the e2e user still belongs to no organization",
  ).toBeGreaterThan(0);
};
