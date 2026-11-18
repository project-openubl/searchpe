import { expect, type Page } from "@playwright/test";

import { DataViewList } from "./DataViewList";

export class PartnersPage extends DataViewList {
  constructor(page: Page) {
    super(page, "Socios", "Tabla de socios");
  }

  async goto() {
    await this.page.goto("/socios");
  }

  /** The first partner's id and name from its link, or undefined when the user belongs to none. */
  async firstPartner(): Promise<{ id: string; name: string } | undefined> {
    await this.goto();
    await this.expectLoaded();
    const link = this.table.getByRole("link").first();
    await expect(
      link.or(
        this.page.getByRole("heading", {
          name: "No perteneces a ningún socio",
        }),
      ),
    ).toBeVisible();
    if ((await link.count()) === 0) return undefined;
    const id = (await link.getAttribute("href"))?.match(
      /\/socios\/([^/?]+)/,
    )?.[1];
    return id ? { id, name: (await link.textContent()) ?? "" } : undefined;
  }
}

/** The partner console's tenants tab; its heading is the partner's name. */
export class PartnerTenantsPage extends DataViewList {
  constructor(page: Page, partnerName: string) {
    super(page, partnerName, "Tabla de organizaciones del socio");
  }

  async goto(partnerId: string) {
    await this.page.goto(`/socios/${partnerId}`);
  }
}
