import type { Page } from "@playwright/test";

import { DataViewList } from "./DataViewList";

/** Manual issuing: the tenant's drafts (`/organizaciones/<t>/borradores`). */
export class DraftsPage extends DataViewList {
  constructor(page: Page) {
    super(page, "Emisión manual", "Tabla de borradores");
  }

  async goto(tenantId: string) {
    await this.page.goto(`/organizaciones/${tenantId}/borradores`);
  }
}
