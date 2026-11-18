import { describe, expect, it } from "vitest";
import { render } from "vitest-browser-react";

import { DeliveryStatusLabel } from "./DeliveryStatusLabel";

describe("DeliveryStatusLabel", () => {
  it("names the known statuses in Spanish", async () => {
    const screen = await render(<DeliveryStatusLabel status="failed" />);
    await expect.element(screen.getByText("Fallido")).toBeVisible();
  });

  it("shows an unknown status as returned", async () => {
    const screen = await render(<DeliveryStatusLabel status="leased" />);
    await expect.element(screen.getByText("leased")).toBeVisible();
  });
});
