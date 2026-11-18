import { expect, type Locator, type Page } from "@playwright/test";

import { DataViewList } from "./DataViewList";

/**
 * The address of the first company of the user's first organization
 * (`/organizaciones/<tenant>/empresas/<company>`), or undefined when there is none.
 */
export const firstCompanyPath = async (
  page: Page,
): Promise<string | undefined> => {
  await page.goto("/organizaciones");
  const tenants = page.getByRole("grid", { name: "Tabla de organizaciones" });
  await expect(tenants).toBeVisible();
  const tenantLink = tenants.getByRole("link").first();
  if ((await tenantLink.count()) === 0) return undefined;
  await tenantLink.click();
  const companies = page.getByRole("grid", { name: "Tabla de empresas" });
  await expect(companies).toBeVisible();
  const companyLink = companies.getByRole("link").first();
  // The table settles on rows or its empty state.
  await expect(
    companyLink.or(
      page.getByRole("heading", {
        name: "Esta organización aún no tiene empresas",
      }),
    ),
  ).toBeVisible();
  if ((await companyLink.count()) === 0) return undefined;
  const href = await companyLink.getAttribute("href");
  return href?.match(/\/organizaciones\/[^/]+\/empresas\/[^/?]+/)?.[0];
};

export class PseAuthorizationPage {
  readonly heading: Locator;
  readonly history: Locator;

  constructor(readonly page: Page) {
    this.heading = page.getByRole("heading", {
      level: 1,
      name: "Autorización de PSE",
    });
    this.history = page.getByRole("grid", {
      name: "Tabla de autorizaciones de PSE",
    });
  }

  async goto(companyPath: string) {
    await this.page.goto(`${companyPath}/pse`);
  }
}

export class SirePeriodPage {
  readonly heading: Locator;

  constructor(readonly page: Page) {
    this.heading = page.getByRole("heading", { level: 1, name: "SIRE" });
  }

  async goto(companyPath: string, search = "") {
    await this.page.goto(`${companyPath}/sire${search}`);
  }
}

export class ConformityListPage extends DataViewList {
  constructor(page: Page) {
    super(page, "Facturas negociables", "Tabla de facturas negociables");
  }

  async goto(companyPath: string) {
    await this.page.goto(`${companyPath}/conformidad`);
  }
}
