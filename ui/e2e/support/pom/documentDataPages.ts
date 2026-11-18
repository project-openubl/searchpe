import { expect, type Page } from "@playwright/test";

import { DataViewList } from "./DataViewList";

/** Page objects of the documents and data screens (U1b). */

/** The first company of the first organization as `{ tenantId, companyId }`, or undefined. */
export const firstCompany = async (
  page: Page,
): Promise<{ tenantId: string; companyId: string } | undefined> => {
  await page.goto("/organizaciones");
  const tenants = page.getByRole("grid", { name: "Tabla de organizaciones" });
  await expect(tenants).toBeVisible();
  const tenantLink = tenants.getByRole("link").first();
  if ((await tenantLink.count()) === 0) return undefined;
  await tenantLink.click();
  const companies = page.getByRole("grid", { name: "Tabla de empresas" });
  await expect(companies).toBeVisible();
  const companyLink = companies.getByRole("link").first();
  await expect(
    companyLink.or(
      page.getByRole("heading", {
        name: "Esta organización aún no tiene empresas",
      }),
    ),
  ).toBeVisible();
  if ((await companyLink.count()) === 0) return undefined;
  const match = (await companyLink.getAttribute("href"))?.match(
    /\/organizaciones\/([^/]+)\/empresas\/([^/?]+)/,
  );
  return match ? { tenantId: match[1], companyId: match[2] } : undefined;
};

export class DocumentsPage extends DataViewList {
  constructor(page: Page) {
    super(page, "Comprobantes", "Tabla de comprobantes");
  }

  async goto(tenantId: string) {
    await this.page.goto(`/organizaciones/${tenantId}/documentos`);
  }
}

export class ReceptionsPage extends DataViewList {
  constructor(page: Page) {
    super(page, "Recepciones", "Tabla de recepciones");
  }

  async goto({ tenantId, companyId }: { tenantId: string; companyId: string }) {
    await this.page.goto(
      `/organizaciones/${tenantId}/empresas/${companyId}/recepciones`,
    );
  }
}

export class ReportsPage extends DataViewList {
  constructor(page: Page) {
    super(page, "Reportes", "Tabla de reportes");
  }

  async goto({ tenantId, companyId }: { tenantId: string; companyId: string }) {
    await this.page.goto(
      `/organizaciones/${tenantId}/empresas/${companyId}/reportes`,
    );
  }
}

export class AdapterTokensPage extends DataViewList {
  constructor(page: Page) {
    super(page, "Tokens de adaptadores", "Tabla de tokens de adaptadores");
  }

  async goto({ tenantId, companyId }: { tenantId: string; companyId: string }) {
    await this.page.goto(
      `/organizaciones/${tenantId}/empresas/${companyId}/adaptadores`,
    );
  }
}

export class ExportsPage extends DataViewList {
  constructor(page: Page) {
    super(page, "Exportaciones", "Tabla de exportaciones");
  }

  async goto(tenantId: string) {
    await this.page.goto(`/organizaciones/${tenantId}/exportaciones`);
  }
}

export class DataSubjectRequestsPage extends DataViewList {
  constructor(page: Page) {
    super(
      page,
      "Solicitudes de datos personales",
      "Tabla de solicitudes de datos personales",
    );
  }

  async goto(tenantId: string) {
    await this.page.goto(`/organizaciones/${tenantId}/datos-personales`);
  }
}

export class EmailSettingsPage extends DataViewList {
  constructor(page: Page) {
    super(page, "Correo", "Tabla de dominios remitentes");
  }

  readonly suppressions = this.page.getByRole("grid", {
    name: "Tabla de direcciones suprimidas",
  });

  async goto(tenantId: string) {
    await this.page.goto(`/organizaciones/${tenantId}/correo`);
  }
}
