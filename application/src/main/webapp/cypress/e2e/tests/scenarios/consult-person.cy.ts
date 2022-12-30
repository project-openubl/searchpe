/// <reference types="cypress" />

import { VersionsPage } from "../../models/version";
import { ConsultaPage } from "../../models/consulta";

describe("Flows", () => {
  const versionsPage = new VersionsPage();
  const consultaPage = new ConsultaPage();

  it("Create version and consult a person", () => {
    versionsPage.create();

    // Verify table
    cy.get(".pf-c-table")
      .pf4_table_rows()
      .eq(0)
      .find("td[data-label='Estado']")
      .contains("Completed", { timeout: 60000 });
    cy.get(".pf-c-table")
      .pf4_table_rows()
      .eq(0)
      .find("td[data-label='Labels']")
      .should("contain", "Activo");

    // Consultar
    consultaPage.consultar("20506866473");
    cy.get("dd").contains("WINIADAEWOO ELECTRONICS PERU S.A.C.");
  });
});
