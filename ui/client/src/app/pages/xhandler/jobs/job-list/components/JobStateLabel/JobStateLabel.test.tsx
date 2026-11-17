import { describe, expect, it } from "vitest";
import { render } from "vitest-browser-react";

import { JobStateLabel } from "./JobStateLabel";

describe("JobStateLabel", () => {
  it("shows a known state in Spanish", async () => {
    const screen = await render(<JobStateLabel state="failed" />);
    await expect.element(screen.getByText("Fallido")).toBeVisible();
  });

  it("shows a state it doesn't know as the server returned it", async () => {
    const screen = await render(<JobStateLabel state="skipped" />);
    await expect.element(screen.getByText("skipped")).toBeVisible();
  });
});
