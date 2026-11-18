import { describe, expect, it, vi } from "vitest";
import { render } from "vitest-browser-react";

import { useNotifications } from "@app/hooks/useNotifications";
import { useSeriesUpdateMutation } from "@app/queries/xhandler/series";

import { SeriesEditModal } from "./SeriesEditModal";

vi.mock(import("@app/hooks/useNotifications"), () => ({
  useNotifications: vi.fn(),
}));
vi.mock(import("@app/queries/xhandler/series"), () => ({
  useSeriesUpdateMutation: vi.fn(),
}));

const series = {
  id: "s1",
  serie: "F001",
  tipo_comprobante: "01",
  next_number: 42,
  active: true,
  is_default: true,
  sunat_env: "fake",
};

const setUp = () => {
  const mutate = vi.fn();
  vi.mocked(useNotifications).mockReturnValue({
    addNotification: vi.fn(),
  } as unknown as ReturnType<typeof useNotifications>);
  vi.mocked(useSeriesUpdateMutation).mockReturnValue({
    mutate,
    isPending: false,
  } as unknown as ReturnType<typeof useSeriesUpdateMutation>);
  return mutate;
};

describe("SeriesEditModal", () => {
  it("starts at the series' next number and refuses to go back", async () => {
    const mutate = setUp();
    const screen = await render(
      <SeriesEditModal companyId="c1" series={series} onClose={vi.fn()} />,
    );
    const field = screen.getByRole("textbox", {
      name: "Siguiente número",
      exact: true,
    });
    await expect.element(field).toHaveValue("42");
    await field.fill("41");
    await screen.getByRole("button", { name: "Guardar" }).click();
    await expect
      .element(
        screen.getByText("El siguiente número no puede ser menor que 42"),
      )
      .toBeVisible();
    expect(mutate).not.toHaveBeenCalled();
  });

  it("sends only the new next number", async () => {
    const mutate = setUp();
    const screen = await render(
      <SeriesEditModal companyId="c1" series={series} onClose={vi.fn()} />,
    );
    await screen
      .getByRole("textbox", { name: "Siguiente número", exact: true })
      .fill("500");
    await screen.getByRole("button", { name: "Guardar" }).click();

    await expect.poll(() => mutate.mock.calls.length).toBe(1);
    expect(mutate.mock.calls[0][0]).toEqual({
      seriesId: "s1",
      body: { next_number: 500 },
    });
  });
});
