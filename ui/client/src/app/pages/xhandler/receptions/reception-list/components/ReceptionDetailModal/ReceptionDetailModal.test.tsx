import { describe, expect, it, vi } from "vitest";
import { render } from "vitest-browser-react";

import { ReceptionDetailModal } from "./ReceptionDetailModal";

describe("ReceptionDetailModal", () => {
  it("offers only the files the reception stored", async () => {
    const onDownload = vi.fn();
    const screen = await render(
      <ReceptionDetailModal
        reception={{
          id: "r1",
          serie_numero: "F001-12",
          supplier_ruc: "20123456789",
          tipo_comprobante: "01",
          validity_state: "valid",
          has_pdf: true,
          has_cdr: false,
        }}
        downloadingKind={undefined}
        onDownload={onDownload}
        onClose={vi.fn()}
      />,
    );

    await expect.element(screen.getByText("Válido")).toBeVisible();
    await expect
      .element(screen.getByRole("button", { name: "Descargar PDF" }))
      .toBeVisible();
    await expect
      .element(screen.getByRole("button", { name: "Descargar CDR" }))
      .not.toBeInTheDocument();

    await screen.getByRole("button", { name: "Descargar XML" }).click();
    expect(onDownload).toHaveBeenCalledExactlyOnceWith("xml");
  });
});
