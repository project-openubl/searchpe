import { test as base } from "@playwright/test";

import {
  CompaniesPage,
  ContribuyentesPage,
  JobsPage,
  TenantsPage,
  VersionsPage,
} from "./pom/pages";

interface ITestFixtures {
  contribuyentesPage: ContribuyentesPage;
  versionsPage: VersionsPage;
  tenantsPage: TenantsPage;
  companiesPage: CompaniesPage;
  jobsPage: JobsPage;
}

/** Playwright's `test` with the page objects; specs import `test`/`expect` from here. */
export const test = base.extend<ITestFixtures>({
  contribuyentesPage: async ({ page }, provide) =>
    provide(new ContribuyentesPage(page)),
  versionsPage: async ({ page }, provide) => provide(new VersionsPage(page)),
  tenantsPage: async ({ page }, provide) => provide(new TenantsPage(page)),
  companiesPage: async ({ page }, provide) => provide(new CompaniesPage(page)),
  jobsPage: async ({ page }, provide) => provide(new JobsPage(page)),
});

export { expect } from "@playwright/test";
