/// <reference types="cypress" />

export class ConsultaPage {
  openPage(): void {
    // Interceptors
    cy.intercept("GET", "/api/contribuyentes/*").as("getContribuyentes");

    // Open page
    cy.visit("#/");
  }

  consultar(numeroDocumento: string): void {
    this.openPage();

    cy.get("input[name='filterText']")
      .clear()
      .type(numeroDocumento)
      .type("{enter}");

    cy.wait("@getContribuyentes");
  }
}
