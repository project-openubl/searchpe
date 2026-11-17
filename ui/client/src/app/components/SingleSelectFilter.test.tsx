import { describe, expect, it, vi } from "vitest";
import { render } from "vitest-browser-react";

import { Toolbar, ToolbarContent } from "@patternfly/react-core";

import { SingleSelectFilter } from "./SingleSelectFilter";

const OPTIONS = [
  { value: "failed", label: "Fallido" },
  { value: "waiting", label: "En espera" },
];

const renderFilter = async (value: string[]) => {
  const onChange = vi.fn();
  const screen = await render(
    <Toolbar>
      <ToolbarContent>
        <SingleSelectFilter
          filterId="estado"
          title="Estado"
          placeholder="Filtrar por estado"
          options={OPTIONS}
          value={value}
          onChange={onChange}
        />
      </ToolbarContent>
    </Toolbar>,
  );
  return { screen, onChange };
};

describe("SingleSelectFilter", () => {
  it("replaces the current value with the picked one", async () => {
    const { screen, onChange } = await renderFilter(["failed"]);

    await screen.getByRole("button", { name: "Filtrar por estado" }).click();
    await screen.getByRole("option", { name: "En espera" }).click();

    expect(onChange).toHaveBeenCalledExactlyOnceWith(undefined, ["waiting"]);
  });

  it("clears the value when the picked option is picked again", async () => {
    const { screen, onChange } = await renderFilter(["failed"]);

    await screen.getByRole("button", { name: "Filtrar por estado" }).click();
    await screen.getByRole("option", { name: "Fallido" }).click();

    expect(onChange).toHaveBeenCalledExactlyOnceWith(undefined, []);
  });

  it("shows the picked option's label on the toggle", async () => {
    const { screen } = await renderFilter(["waiting"]);

    await expect
      .element(screen.getByRole("button", { name: "Filtrar por estado" }))
      .toHaveTextContent("En espera");
  });
});
