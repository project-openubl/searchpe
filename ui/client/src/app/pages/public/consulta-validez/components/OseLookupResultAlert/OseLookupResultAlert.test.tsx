import { describe, expect, it } from "vitest";
import { render } from "vitest-browser-react";

import { OseLookupResultAlert } from "./OseLookupResultAlert";

describe("OseLookupResultAlert", () => {
  it("tells a valid comprobante with its CDR date", async () => {
    const screen = await render(
      <OseLookupResultAlert
        result={{ state: "con_cdr", checked_at: "2026-03-04T15:00:00Z" }}
      />,
    );

    await expect.element(screen.getByText("Comprobante válido")).toBeVisible();
    await expect.element(screen.getByText(/04\/03\/2026/)).toBeVisible();
  });

  it('ignores the server\'s "null" date', async () => {
    const screen = await render(
      <OseLookupResultAlert
        result={{ state: "con_inconsistencia", checked_at: "null" }}
      />,
    );

    await expect
      .element(
        screen.getByText(
          "El OSE revisó el comprobante y comunicó inconsistencias.",
        ),
      )
      .toBeVisible();
  });

  it("shows an unknown state as returned", async () => {
    const screen = await render(
      <OseLookupResultAlert result={{ state: "otro_estado" }} />,
    );

    await expect.element(screen.getByText("otro_estado")).toBeVisible();
  });
});
