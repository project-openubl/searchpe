import type { Locator, Page } from "@playwright/test";

import { DataViewList } from "./DataViewList";
import { firstCompany } from "./documentDataPages";

/**
 * The address of the first company of the user's first organization
 * (`/organizaciones/<tenant>/empresas/<company>`), or undefined when there is none.
 */
export const firstCompanyPath = async (
  page: Page,
): Promise<string | undefined> => {
  const company = await firstCompany(page);
  return company
    ? `/organizaciones/${company.tenantId}/empresas/${company.companyId}`
    : undefined;
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
