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

  /** The toolbar shows one filter at a time: switches from the `current` one to `next`. */
  async chooseFilter(current: string, next: string) {
    await this.page.getByRole("button", { name: current, exact: true }).click();
    await this.page.getByRole("menuitem", { name: next, exact: true }).click();
  }

  /**
   * The first data row, or the empty state titled `emptyTitle`: visible once the table settles.
   * The empty state renders inside a row of its own, and loading skeleton rows carry no text.
   */
  firstRowOrEmpty(emptyTitle: string | RegExp): Locator {
    const empty = this.page.getByRole("heading", { name: emptyTitle });
    return this.rows()
      .filter({ hasNot: empty })
      .filter({ hasText: /\S/ })
      .first()
      .or(empty);
  }
}
