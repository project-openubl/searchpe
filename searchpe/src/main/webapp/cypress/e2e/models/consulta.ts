/// <reference types="cypress" />

export class ConsultaPage {
  openPage(): void {
    // Interceptors
    cy.intercept("GET", "/api/contribuyentes/*").as("getContribuyentes");

    // Open page
    cy.visit("#/");

    // Route changes are transitions (React Router 7+): wait until the page is
    // actually rendered so we don't grab the previous page's search input
    cy.contains("h1", "Buscar por 'Número de documento'");
  }

  consultar(numeroDocumento: string): void {
    this.openPage();

    cy.get("input[name='filterText']").clear();
    cy.get("input[name='filterText']").type(`${numeroDocumento}{enter}`);

    cy.wait("@getContribuyentes");
  }
}
