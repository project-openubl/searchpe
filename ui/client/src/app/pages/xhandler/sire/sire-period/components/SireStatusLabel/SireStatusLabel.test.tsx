import { describe, expect, it } from "vitest";
import { render } from "vitest-browser-react";

import { SireStatusLabel } from "./SireStatusLabel";

describe("SireStatusLabel", () => {
  it("labels a known state in Spanish", async () => {
    const screen = await render(<SireStatusLabel status="proposal_ready" />);

    await expect
      .element(screen.getByText("Propuesta lista para revisar"))
      .toBeVisible();
  });

  it("shows an unknown state as returned", async () => {
    const screen = await render(<SireStatusLabel status="paused" />);

    await expect.element(screen.getByText("paused")).toBeVisible();
  });
});
