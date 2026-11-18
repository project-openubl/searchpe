import { describe, expect, it } from "vitest";
import { render } from "vitest-browser-react";

import { ReceptionValidityLabel } from "./ReceptionValidityLabel";

describe("ReceptionValidityLabel", () => {
  it("shows SUNAT's answer in Spanish", async () => {
    const screen = await render(<ReceptionValidityLabel state="not_found" />);
    await expect.element(screen.getByText("No existe en SUNAT")).toBeVisible();
  });

  it("shows a state it doesn't know as returned", async () => {
    const screen = await render(<ReceptionValidityLabel state="other" />);
    await expect.element(screen.getByText("other")).toBeVisible();
  });
});
