import { describe, expect, it, vi } from "vitest";
import { render } from "vitest-browser-react";

import { GatedButton } from "./GatedButton";

describe("GatedButton", () => {
  it("runs onClick when not disabled", async () => {
    const onClick = vi.fn();
    const screen = await render(
      <GatedButton onClick={onClick}>Nueva versión</GatedButton>,
    );

    await screen.getByRole("button", { name: "Nueva versión" }).click();

    expect(onClick).toHaveBeenCalledOnce();
  });

  it("stays focusable, ignores clicks and shows the reason when aria-disabled", async () => {
    const onClick = vi.fn();
    const screen = await render(
      <GatedButton
        onClick={onClick}
        isAriaDisabled
        tooltipProps={{ content: "Necesitas el permiso version:write" }}
      >
        Nueva versión
      </GatedButton>,
    );
    const button = screen.getByRole("button", { name: "Nueva versión" });

    await expect.element(button).toHaveAttribute("aria-disabled", "true");
    await button.hover();
    await expect
      .element(screen.getByText("Necesitas el permiso version:write"))
      .toBeVisible();
    await button.click({ force: true });
    expect(onClick).not.toHaveBeenCalled();
  });
});
