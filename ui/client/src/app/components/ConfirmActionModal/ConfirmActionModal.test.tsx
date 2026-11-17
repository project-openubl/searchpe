import { describe, expect, it, vi } from "vitest";
import { userEvent } from "vitest/browser";
import { render } from "vitest-browser-react";

import { ConfirmActionModal } from "./ConfirmActionModal";

// The confirm dialog is pure presentation (the caller owns the mutation/toasts),
// so its content, button styling and dismissal are component-tested here; the real
// confirm round-trip and server failures stay in e2e.
describe("ConfirmActionModal", () => {
  it("shows the title, body and confirm/cancel buttons", async () => {
    const screen = await render(
      <ConfirmActionModal
        isOpen
        title="Eliminar versión"
        body={
          'Are you sure you want to delete "alice"? This action cannot be undone.'
        }
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    await expect
      .element(screen.getByRole("heading", { name: "Eliminar versión" }))
      .toBeVisible();
    await expect
      .element(screen.getByText(/Are you sure you want to delete "alice"/))
      .toBeVisible();
    await expect
      .element(screen.getByRole("button", { name: "Eliminar", exact: true }))
      .toBeVisible();
    await expect
      .element(screen.getByRole("button", { name: "Cancelar", exact: true }))
      .toBeVisible();
  });

  it("uses the given confirm label and a primary (non-danger) remove", async () => {
    const screen = await render(
      <ConfirmActionModal
        isOpen
        title="Remove Role"
        body="Remove the role?"
        confirmLabel="Remove"
        confirmVariant="primary"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    const remove = screen.getByRole("button", { name: "Remove", exact: true });
    await expect.element(remove).toBeVisible();
    await expect.element(remove).toHaveClass(/pf-m-primary/);
  });

  it("calls onConfirm when the confirm button is clicked", async () => {
    const onConfirm = vi.fn();
    const screen = await render(
      <ConfirmActionModal
        isOpen
        title="Eliminar versión"
        body="body"
        onConfirm={onConfirm}
        onCancel={vi.fn()}
      />,
    );
    await screen.getByRole("button", { name: "Eliminar", exact: true }).click();
    expect(onConfirm).toHaveBeenCalledOnce();
  });

  it("dismisses with Cancel, the × button and Escape", async () => {
    for (const dismiss of ["Cancelar", "Close"] as const) {
      const onCancel = vi.fn();
      const screen = await render(
        <ConfirmActionModal
          isOpen
          title="Eliminar versión"
          body="body"
          onConfirm={vi.fn()}
          onCancel={onCancel}
        />,
      );
      await screen.getByRole("button", { name: dismiss, exact: true }).click();
      expect(onCancel).toHaveBeenCalledOnce();
    }
    const onCancel = vi.fn();
    await render(
      <ConfirmActionModal
        isOpen
        title="Eliminar versión"
        body="body"
        onConfirm={vi.fn()}
        onCancel={onCancel}
      />,
    );
    await userEvent.keyboard("{Escape}");
    expect(onCancel).toHaveBeenCalledOnce();
  });
});
