import { describe, expect, it } from "vitest";
import { render } from "vitest-browser-react";

import { ConformityStateLabel } from "./ConformityStateLabel";

describe("ConformityStateLabel", () => {
  it("labels an objection in Spanish", async () => {
    const screen = await render(<ConformityStateLabel state="objected" />);

    await expect.element(screen.getByText("Disconforme")).toBeVisible();
  });

  it("shows an unknown state as returned", async () => {
    const screen = await render(<ConformityStateLabel state="otro" />);

    await expect.element(screen.getByText("otro")).toBeVisible();
  });
});
