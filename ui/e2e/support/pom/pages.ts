import { expect, type Page } from "@playwright/test";

import { DataViewList } from "./DataViewList";

export class ContribuyentesPage extends DataViewList {
  constructor(page: Page) {
    super(page, "Contribuyentes", "Tabla de contribuyentes");
  }

  async goto() {
    await this.page.goto("/padron/contribuyentes");
  }
}

export class VersionsPage extends DataViewList {
  constructor(page: Page) {
    super(page, "Versiones del padrón", "Tabla de versiones del padrón");
  }

  async goto() {
    await this.page.goto("/padron/versiones");
  }
}

export class TenantsPage extends DataViewList {
  constructor(page: Page) {
    super(page, "Organizaciones", "Tabla de organizaciones");
  }

  async goto() {
    await this.page.goto("/organizaciones");
  }

  /** The first organization's id from its link, or undefined when the user has none. */
  async firstTenantId(): Promise<string | undefined> {
    await this.goto();
    await this.expectLoaded();
    // Count only once the table settled: while loading it has no link either.
    await expect(
      this.firstRowOrEmpty("Aún no perteneces a ninguna organización"),
    ).toBeVisible();
    const link = this.table.getByRole("link").first();
    if ((await link.count()) === 0) return undefined;
    const href = await link.getAttribute("href");
    return href?.match(/\/organizaciones\/([^/]+)/)?.[1];
  }
}

export class CompaniesPage extends DataViewList {
  constructor(page: Page) {
    super(page, "Empresas", "Tabla de empresas");
  }

  async goto(tenantId: string) {
    await this.page.goto(`/organizaciones/${tenantId}/empresas`);
  }
}

export class JobsPage extends DataViewList {
  constructor(page: Page) {
    super(page, "Trabajos", "Tabla de trabajos");
  }

  async goto(tenantId: string) {
    await this.page.goto(`/organizaciones/${tenantId}/trabajos`);
  }
}
