import { mkdirSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";

import { test as setup } from "@playwright/test";

import { authenticate } from "@support/helpers/auth";
import { ensureFixtures, FIXTURES_PATH } from "@support/helpers/tenants";

const AUTH_STATE_PATH = ".auth/user.json";

setup("authenticate", async ({ page }) => {
  await authenticate(page);
  // What the organization specs need, on a fresh stack or one that outlived earlier runs.
  const fixtures = await ensureFixtures(page);
  // .auth/ doesn't exist on a fresh checkout until storageState() below creates it
  mkdirSync(dirname(FIXTURES_PATH), { recursive: true });
  writeFileSync(FIXTURES_PATH, JSON.stringify(fixtures, null, 2));
  await page.context().storageState({ path: AUTH_STATE_PATH });
});
