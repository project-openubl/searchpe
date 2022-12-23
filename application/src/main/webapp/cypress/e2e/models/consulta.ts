/// <reference types="cypress" />

export class ConsultaPage {
  openPage(): void {
    // Interceptors
    cy.intercept("GET", "/api/contribuyentes/*").as("getContribuyentes");

    // Open page
    cy.visit("#/");
  }

  // protected fillForm(formValue: IFormValue): void {
  //   cy.get("input[name='name']").clear().type(formValue.name);
  // }

  consultar(numeroDocumento: string): void {
    this.openPage();

    cy.get("input[name='filterText']")
      .clear()
      .type(numeroDocumento)
      .type("{enter}");

    cy.wait("@getContribuyentes");
  }
}
