import { describe, expect, it } from "vitest";
import { render } from "vitest-browser-react";

import { DocumentTimeline } from "./DocumentTimeline";

describe("DocumentTimeline", () => {
  it("lists each event with its Spanish status, SUNAT code and message", async () => {
    const screen = await render(
      <DocumentTimeline
        events={[
          { at: "2026-01-05T10:00:00Z", status: "pending_send", actor: "api" },
          {
            at: "2026-01-05T10:01:00Z",
            status: "rejected",
            code: 2017,
            message: "El número de RUC del receptor no existe",
          },
        ]}
      />,
    );
    await expect.element(screen.getByText("Por enviar")).toBeVisible();
    await expect.element(screen.getByText("Rechazado")).toBeVisible();
    await expect.element(screen.getByText(/Código SUNAT 2017/)).toBeVisible();
    await expect
      .element(screen.getByText(/El número de RUC del receptor no existe/))
      .toBeVisible();
  });

  it("shows an event without a status by its message", async () => {
    const screen = await render(
      <DocumentTimeline events={[{ message: "delivered (client)" }]} />,
    );
    await expect.element(screen.getByText("delivered (client)")).toBeVisible();
  });

  it("says when there is nothing yet", async () => {
    const screen = await render(<DocumentTimeline events={[]} />);
    await expect.element(screen.getByText("Aún no hay eventos.")).toBeVisible();
  });
});
