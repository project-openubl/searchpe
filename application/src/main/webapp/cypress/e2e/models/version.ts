/// <reference types="cypress" />

export class VersionsPage {
  openPage(): void {
    // Interceptors
    cy.intercept("POST", "/api/versions").as("postVersion");
    cy.intercept("GET", "/api/versions").as("getVersions");

    // Open page
    cy.visit("#/versiones");
    cy.wait("@getVersions");
  }

  create(): void {
    this.openPage();

    cy.get("button[aria-label='New version']").click();

    cy.wait("@postVersion");
    cy.wait("@getVersions");
  }
}
