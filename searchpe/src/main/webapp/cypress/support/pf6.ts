/// <reference types="cypress" />

declare global {
  namespace Cypress {
    interface Chainable<Subject> {
      pf6_label_group_labels(): Chainable<any>;
      pf6_pagination_goToPage(pageNumer: number): Chainable<any>;
      pf6_table_rows(): Chainable<any>;
      pf6_table_row_check(rowIndex: number): Chainable<any>;
      pf6_table_action_select(
        rowIndex: number,
        actionName: string
      ): Chainable<any>;
      pf6_table_row_expand(rowIndex: number): Chainable<any>;
      pf6_table_column_toggle(column: string): Chainable<any>;
      pf6_table_column_isAsc(column: string): Chainable<any>;
      pf6_table_column_isDesc(column: string): Chainable<any>;
    }
  }
}

// Labels (PatternFly 6 replacement of Chips)

Cypress.Commands.add(
  "pf6_label_group_labels",
  { prevSubject: "element" },
  (labelGroup) => {
    return cy.wrap(labelGroup).find(".pf-v6-c-label-group__list-item");
  }
);

// Pagination

Cypress.Commands.add(
  "pf6_pagination_goToPage",
  { prevSubject: "element" },
  (container, pageNumber) => {
    cy.wrap(container)
      .find(
        ".pf-v6-c-pagination__nav-page-select input[aria-label='Current page']"
      )
      .clear()
      .type(`${pageNumber}`)
      .type("{enter}");
  }
);

// Table

/**
 * Using cy.wait() to avoid race conditions. This is a workaround
 * until https://github.com/cypress-io/cypress/issues/7306 is fixed.
 */
Cypress.Commands.add("pf6_table_rows", { prevSubject: "element" }, (table) => {
  cy.wait(250);
  return cy
    .wrap(table)
    .find("tbody > tr")
    .not(".pf-m-expanded")
    .not(".pf-v6-c-table__expandable-row");
});

Cypress.Commands.add(
  "pf6_table_row_check",
  { prevSubject: "element" },
  (table, rowIndex) => {
    cy.wrap(table)
      .find("tbody > tr > td.pf-v6-c-table__check input")
      .eq(rowIndex)
      .check();
  }
);

Cypress.Commands.add(
  "pf6_table_action_select",
  { prevSubject: "element" },
  (table, rowIndex, actionName) => {
    cy.wrap(table)
      .find("tbody > tr > td.pf-v6-c-table__action button")
      .eq(rowIndex)
      .click();

    cy.get(".pf-v6-c-menu__item").contains(actionName).click();
  }
);

Cypress.Commands.add(
  "pf6_table_row_expand",
  { prevSubject: "element" },
  (table, rowIndex) => {
    cy.wrap(table)
      .find("tbody > tr > td.pf-v6-c-table__toggle > button")
      .eq(rowIndex)
      .click();
  }
);

Cypress.Commands.add(
  "pf6_table_column_toggle",
  { prevSubject: "element" },
  (table, column) => {
    cy.wrap(table)
      .find("thead > tr > th.pf-v6-c-table__sort")
      .contains(column)
      .click();
  }
);

Cypress.Commands.add(
  "pf6_table_column_isAsc",
  { prevSubject: "element" },
  (table, column) => {
    cy.wrap(table)
      .find("thead > tr > th.pf-v6-c-table__sort.pf-m-selected")
      .should("have.attr", "aria-sort", "ascending")
      .get("button")
      .contains(column);
  }
);

Cypress.Commands.add(
  "pf6_table_column_isDesc",
  { prevSubject: "element" },
  (table, column) => {
    cy.wrap(table)
      .find("thead > tr > th.pf-v6-c-table__sort.pf-m-selected")
      .should("have.attr", "aria-sort", "descending")
      .get("button")
      .contains(column);
  }
);

export {};
