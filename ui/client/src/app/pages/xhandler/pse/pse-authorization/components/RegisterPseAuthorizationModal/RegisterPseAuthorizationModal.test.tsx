import { describe, expect, it, vi } from "vitest";
import { render } from "vitest-browser-react";

import { useNotifications } from "@app/hooks/useNotifications";
import { useAuthorizePseMutation } from "@app/queries/xhandler/pse";

import { RegisterPseAuthorizationModal } from "./RegisterPseAuthorizationModal";

vi.mock(import("@app/hooks/useNotifications"), () => ({
  useNotifications: vi.fn(),
}));
vi.mock(import("@app/queries/xhandler/pse"), () => ({
  useAuthorizePseMutation: vi.fn(),
}));

describe("RegisterPseAuthorizationModal", () => {
  it("asks for the date and SUNAT's evidence", async () => {
    const mutate = vi.fn();
    vi.mocked(useNotifications).mockReturnValue({
      addNotification: vi.fn(),
    } as unknown as ReturnType<typeof useNotifications>);
    vi.mocked(useAuthorizePseMutation).mockReturnValue({
      mutate,
      isPending: false,
    } as unknown as ReturnType<typeof useAuthorizePseMutation>);

    const screen = await render(
      <RegisterPseAuthorizationModal companyId="c1" isOpen onClose={vi.fn()} />,
    );
    await screen.getByRole("button", { name: "Registrar" }).click();

    await expect
      .element(screen.getByText("Ingresa la fecha de la autorización"))
      .toBeVisible();
    await expect
      .element(screen.getByText("Adjunta la constancia de SUNAT"))
      .toBeVisible();
    expect(mutate).not.toHaveBeenCalled();
  });
});
