import { describe, expect, it, vi } from "vitest";
import { render } from "vitest-browser-react";

import { useNotifications } from "@app/hooks/useNotifications";
import { useDocumentEmailMutation } from "@app/queries/xhandler/documents";

import { EmailDocumentModal } from "./EmailDocumentModal";

vi.mock(import("@app/hooks/useNotifications"), () => ({
  useNotifications: vi.fn(),
}));
vi.mock(import("@app/queries/xhandler/documents"), () => ({
  useDocumentEmailMutation: vi.fn(),
}));

const setUp = () => {
  const mutate = vi.fn();
  vi.mocked(useNotifications).mockReturnValue({
    addNotification: vi.fn(),
  } as unknown as ReturnType<typeof useNotifications>);
  vi.mocked(useDocumentEmailMutation).mockReturnValue({
    mutate,
    isPending: false,
  } as unknown as ReturnType<typeof useDocumentEmailMutation>);
  return mutate;
};

describe("EmailDocumentModal", () => {
  it("sends the recipients as lists", async () => {
    const mutate = setUp();
    const screen = await render(
      <EmailDocumentModal
        document={{ id: "d1", serie_numero: "F001-1" }}
        onClose={vi.fn()}
      />,
    );
    await screen
      .getByRole("textbox", { name: "Para", exact: true })
      .fill("compras@cliente.pe, pagos@cliente.pe");
    await screen
      .getByRole("checkbox", { name: "Adjuntar el CDR de SUNAT" })
      .click();
    await screen.getByRole("button", { name: "Enviar" }).click();

    await expect.poll(() => mutate.mock.calls.length).toBe(1);
    expect(mutate.mock.calls[0][0]).toEqual({
      documentId: "d1",
      body: {
        to: ["compras@cliente.pe", "pagos@cliente.pe"],
        cc: undefined,
        include_cdr: true,
      },
    });
  });

  it("refuses an invalid address", async () => {
    const mutate = setUp();
    const screen = await render(
      <EmailDocumentModal
        document={{ id: "d1", serie_numero: "F001-1" }}
        onClose={vi.fn()}
      />,
    );
    await screen
      .getByRole("textbox", { name: "Para", exact: true })
      .fill("compras");
    await screen.getByRole("button", { name: "Enviar" }).click();
    await expect.element(screen.getByText(/correos válidos/)).toBeVisible();
    expect(mutate).not.toHaveBeenCalled();
  });
});
