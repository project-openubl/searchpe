import { expect, type Locator, type Page } from "@playwright/test";

/** A list page: its `h1` and its data-view table (a `grid` named by its aria-label). */
export class DataViewList {
  readonly heading: Locator;
  readonly table: Locator;

  constructor(
    readonly page: Page,
    headingName: string,
    tableName: string,
  ) {
    this.heading = page.getByRole("heading", { level: 1, name: headingName });
    this.table = page.getByRole("grid", { name: tableName });
  }

  async expectLoaded() {
    await expect(this.heading).toBeVisible();
    await expect(this.table).toBeVisible();
  }

  /** Data rows, header excluded. */
  rows(): Locator {
    return this.table.locator("tbody").getByRole("row");
  }
}
