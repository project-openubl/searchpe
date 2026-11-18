import { describe, expect, it, vi } from "vitest";
import { render } from "vitest-browser-react";

import { PartnerBrandingForm } from "./PartnerBrandingForm";

describe("PartnerBrandingForm", () => {
  it("rejects a color that isn't #RRGGBB", async () => {
    const onSave = vi.fn();
    const screen = await render(
      <PartnerBrandingForm
        submitError={null}
        isSaving={false}
        gateProps={{}}
        onSave={onSave}
      />,
    );

    await screen.getByRole("textbox", { name: "Color principal" }).fill("azul");
    await screen.getByRole("button", { name: "Guardar marca" }).click();

    await expect
      .element(screen.getByText(/Escribe el color como #RRGGBB/))
      .toBeVisible();
    expect(onSave).not.toHaveBeenCalled();
  });

  it("starts with the current branding", async () => {
    const screen = await render(
      <PartnerBrandingForm
        branding={{ color: "#AA0000", footer: "Por Contasoft" }}
        submitError={null}
        isSaving={false}
        gateProps={{}}
        onSave={vi.fn()}
      />,
    );

    await expect
      .element(screen.getByRole("textbox", { name: "Color principal" }))
      .toHaveValue("#AA0000");
    await expect
      .element(screen.getByRole("textbox", { name: "Pie de página" }))
      .toHaveValue("Por Contasoft");
    await expect
      .element(screen.getByRole("textbox", { name: "Nombre del remitente" }))
      .toHaveValue("");
  });

  it("sends only the filled fields", async () => {
    const onSave = vi.fn();
    const screen = await render(
      <PartnerBrandingForm
        submitError={null}
        isSaving={false}
        gateProps={{}}
        onSave={onSave}
      />,
    );

    await screen
      .getByRole("textbox", { name: "Nombre del remitente" })
      .fill(" Contasoft ");
    await screen
      .getByRole("textbox", { name: "Color principal" })
      .fill("#1F6FB2");
    await screen.getByRole("button", { name: "Guardar marca" }).click();

    await expect.poll(() => onSave.mock.calls.length).toBe(1);
    expect(onSave).toHaveBeenCalledWith({
      sender_name: "Contasoft",
      color: "#1F6FB2",
    });
  });

  it("is disabled with the reason for a partner viewer", async () => {
    const screen = await render(
      <PartnerBrandingForm
        submitError={null}
        isSaving={false}
        gateProps={{
          isAriaDisabled: true,
          tooltipProps: {
            content: "Necesitas el rol Administrador en este socio.",
          },
        }}
        onSave={vi.fn()}
      />,
    );

    await expect
      .element(screen.getByRole("button", { name: "Guardar marca" }))
      .toHaveAttribute("aria-disabled", "true");
  });
});
