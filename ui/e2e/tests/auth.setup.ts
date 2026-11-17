import { test as setup } from "@playwright/test";

import { authenticate } from "@support/helpers/auth";

const AUTH_STATE_PATH = ".auth/user.json";

setup("authenticate", async ({ page }) => {
  await authenticate(page);
  await page.context().storageState({ path: AUTH_STATE_PATH });
});
