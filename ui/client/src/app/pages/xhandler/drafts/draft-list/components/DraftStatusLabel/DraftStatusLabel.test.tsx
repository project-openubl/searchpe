import { describe, expect, it } from "vitest";
import { render } from "vitest-browser-react";

import { DraftStatusLabel } from "./DraftStatusLabel";

describe("DraftStatusLabel", () => {
  it("labels the draft statuses in Spanish", async () => {
    const screen = await render(
      <>
        <DraftStatusLabel status="draft" />
        <DraftStatusLabel status="issued" />
      </>,
    );
    await expect.element(screen.getByText("Borrador")).toBeVisible();
    await expect.element(screen.getByText("Emitido")).toBeVisible();
  });

  it("shows an unknown status as sent", async () => {
    const screen = await render(<DraftStatusLabel status="archived" />);
    await expect.element(screen.getByText("archived")).toBeVisible();
  });
});
