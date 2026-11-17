import { test as setup } from "@playwright/test";

import { authenticate } from "@support/helpers/auth";
import { ensureTenant } from "@support/helpers/tenants";

const AUTH_STATE_PATH = ".auth/user.json";

setup("authenticate", async ({ page }) => {
  await authenticate(page);
  // Organization specs need one; a fresh stack has none until the user signs up.
  await ensureTenant(page);
  await page.context().storageState({ path: AUTH_STATE_PATH });
});
