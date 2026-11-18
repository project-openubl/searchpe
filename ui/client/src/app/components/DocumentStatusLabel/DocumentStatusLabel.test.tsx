import { describe, expect, it } from "vitest";
import { render } from "vitest-browser-react";

import { DocumentStatusLabel } from "./DocumentStatusLabel";

describe("DocumentStatusLabel", () => {
  it("shows a known status in Spanish", async () => {
    const screen = await render(<DocumentStatusLabel status="voiding" />);
    await expect.element(screen.getByText("En anulación")).toBeVisible();
  });

  it("shows a status it doesn't know as returned", async () => {
    const screen = await render(<DocumentStatusLabel status="archived" />);
    await expect.element(screen.getByText("archived")).toBeVisible();
  });

  it("adds the hold reason next to the status", async () => {
    const screen = await render(
      <DocumentStatusLabel status="exception" holdReason="needs_attention" />,
    );
    await expect.element(screen.getByText("Con error")).toBeVisible();
    await expect.element(screen.getByText("Requiere atención")).toBeVisible();
  });
});
