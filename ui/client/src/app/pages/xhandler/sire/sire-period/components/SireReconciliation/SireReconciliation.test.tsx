import { describe, expect, it } from "vitest";
import { render } from "vitest-browser-react";

import { SireReconciliation } from "./SireReconciliation";

describe("SireReconciliation", () => {
  it("warns about the differences and lists them", async () => {
    const screen = await render(
      <SireReconciliation
        reconciliation={{
          sunat_count: 3,
          local_count: 2,
          matches: false,
          missing_in_sunat: [],
          missing_locally: ["01-F001-7"],
          differing: [
            { key: "01-F001-3", sunat_total: "118.00", local_total: "120.5" },
          ],
        }}
      />,
    );

    await expect
      .element(
        screen.getByText(
          "La propuesta de SUNAT no coincide con los comprobantes registrados",
        ),
      )
      .toBeVisible();
    await screen
      .getByRole("button", {
        name: "En SUNAT y no registrados en la plataforma (1)",
      })
      .click();
    await expect
      .element(screen.getByRole("gridcell", { name: "01-F001-7" }))
      .toBeVisible();
    await screen
      .getByRole("button", { name: "Con totales distintos (1)" })
      .click();
    await expect
      .element(screen.getByRole("gridcell", { name: "120.50" }))
      .toBeVisible();
    // Nothing missing in SUNAT: no empty section.
    await expect
      .element(screen.getByText(/Registrados que SUNAT no tiene/))
      .not.toBeInTheDocument();
  });

  it("confirms a matching proposal", async () => {
    const screen = await render(
      <SireReconciliation
        reconciliation={{ sunat_count: 2, local_count: 2, matches: true }}
      />,
    );

    await expect
      .element(
        screen.getByText(
          "La propuesta de SUNAT coincide con los comprobantes registrados",
        ),
      )
      .toBeVisible();
  });
});
