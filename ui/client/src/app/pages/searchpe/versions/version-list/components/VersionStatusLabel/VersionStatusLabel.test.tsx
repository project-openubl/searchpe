import { describe, expect, it } from "vitest";
import { render } from "vitest-browser-react";

import { VersionStatusLabel } from "./VersionStatusLabel";

describe("VersionStatusLabel", () => {
  it("shows the status in Spanish", async () => {
    const screen = await render(<VersionStatusLabel status="COMPLETED" />);
    await expect.element(screen.getByText("Completada")).toBeVisible();
  });

  it("shows a dash when the status is unknown", async () => {
    const screen = await render(<VersionStatusLabel status={undefined} />);
    await expect.element(screen.getByText("-")).toBeVisible();
  });
});
