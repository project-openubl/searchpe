import { describe, expect, it, vi } from "vitest";
import { render } from "vitest-browser-react";

import { useNotifications } from "@app/hooks/useNotifications";
import { useDocumentVoidMutation } from "@app/queries/xhandler/documents";

import { VoidDocumentModal } from "./VoidDocumentModal";

vi.mock(import("@app/hooks/useNotifications"), () => ({
  useNotifications: vi.fn(),
}));
vi.mock(import("@app/queries/xhandler/documents"), () => ({
  useDocumentVoidMutation: vi.fn(),
  needsReversalDate: (document: { tipo_comprobante?: string }) =>
    document.tipo_comprobante === "20" || document.tipo_comprobante === "40",
}));

const setUp = () => {
  const mutate = vi.fn();
  vi.mocked(useNotifications).mockReturnValue({
    addNotification: vi.fn(),
  } as unknown as ReturnType<typeof useNotifications>);
  vi.mocked(useDocumentVoidMutation).mockReturnValue({
    mutate,
    isPending: false,
  } as unknown as ReturnType<typeof useDocumentVoidMutation>);
  return mutate;
};

describe("VoidDocumentModal", () => {
  it("asks for a motivo and the not-delivered attestation", async () => {
    const mutate = setUp();
    const screen = await render(
      <VoidDocumentModal
        document={{ id: "d1", serie_numero: "F001-1", tipo_comprobante: "01" }}
        onClose={vi.fn()}
      />,
    );
    await screen.getByRole("button", { name: "Anular" }).click();
    await expect
      .element(screen.getByText("Escribe el motivo de la anulación."))
      .toBeVisible();
    await expect
      .element(
        screen.getByText(
          "Confirma que el comprador no recibió el comprobante.",
        ),
      )
      .toBeVisible();
    expect(mutate).not.toHaveBeenCalled();
  });

  it("sends the void request with the attestation", async () => {
    const mutate = setUp();
    const screen = await render(
      <VoidDocumentModal
        document={{ id: "d1", serie_numero: "F001-1", tipo_comprobante: "01" }}
        onClose={vi.fn()}
      />,
    );
    await screen
      .getByRole("textbox", { name: "Motivo" })
      .fill("Error en el RUC");
    await screen
      .getByRole("checkbox", {
        name: "Confirmo que el comprador no recibió el comprobante",
      })
      .click();
    await screen.getByRole("button", { name: "Anular" }).click();

    await expect.poll(() => mutate.mock.calls.length).toBe(1);
    expect(mutate.mock.calls[0][0]).toEqual({
      documentId: "d1",
      body: {
        motivo: "Error en el RUC",
        not_delivered: true,
        reversal_communicated_at: undefined,
      },
    });
  });

  it("asks the reversal date for a retención", async () => {
    setUp();
    const screen = await render(
      <VoidDocumentModal
        document={{ id: "d2", serie_numero: "R001-1", tipo_comprobante: "20" }}
        onClose={vi.fn()}
      />,
    );
    await expect
      .element(screen.getByText("Fecha en que se comunicó la reversión"))
      .toBeVisible();
  });
});
