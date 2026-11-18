import type { Page } from "@playwright/test";

import { DataViewList } from "./DataViewList";

/** Page objects of the tenant administration screens (U1a). */

export class MembersPage extends DataViewList {
  constructor(page: Page) {
    super(page, "Miembros", "Tabla de miembros");
  }

  async goto(tenantId: string) {
    await this.page.goto(`/organizaciones/${tenantId}/miembros`);
  }

  /** The row of a member, by its subject. */
  row(subject: string) {
    return this.rows().filter({ hasText: subject });
  }

  /** Opens a member row's actions menu and picks `action`. */
  async rowAction(subject: string, action: string) {
    await this.row(subject)
      .getByRole("button", { name: "Kebab toggle" })
      .click();
    await this.page.getByRole("menuitem", { name: action }).click();
  }
}

export class WebhooksPage extends DataViewList {
  constructor(page: Page) {
    super(page, "Webhooks", "Tabla de webhooks");
  }

  async goto(tenantId: string) {
    await this.page.goto(`/organizaciones/${tenantId}/webhooks`);
  }
}

export class NotificationsPage extends DataViewList {
  constructor(page: Page) {
    super(page, "Notificaciones", "Tabla de notificaciones");
  }

  async goto(tenantId: string) {
    await this.page.goto(`/organizaciones/${tenantId}/notificaciones`);
  }
}

export class NotificationPreferencesPage extends DataViewList {
  constructor(page: Page) {
    super(
      page,
      "Preferencias de notificación",
      "Tabla de preferencias de notificación",
    );
  }

  async goto(tenantId: string) {
    await this.page.goto(
      `/organizaciones/${tenantId}/notificaciones/preferencias`,
    );
  }
}

export class ServiceStatusPage extends DataViewList {
  constructor(page: Page) {
    super(page, "Estado del servicio", "Tabla de colas");
  }

  async goto() {
    await this.page.goto("/estado");
  }
}

/** A random, well-formed RUC (20 + 9 digits), so runs never collide on a shared stack. */
export const randomRuc = (): string =>
  `20${String(Math.floor(Math.random() * 1e9)).padStart(9, "0")}`;
