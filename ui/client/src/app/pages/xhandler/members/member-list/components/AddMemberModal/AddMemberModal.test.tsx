import { describe, expect, it, vi } from "vitest";
import { render } from "vitest-browser-react";

import { useNotifications } from "@app/hooks/useNotifications";
import { useMemberAddMutation } from "@app/queries/xhandler/members";

import { AddMemberModal } from "./AddMemberModal";

vi.mock(import("@app/hooks/useNotifications"), () => ({
  useNotifications: vi.fn(),
}));
vi.mock(import("@app/queries/xhandler/members"), () => ({
  ASSIGNABLE_MEMBER_ROLES: ["admin", "operator", "viewer"] as const,
  MEMBER_KINDS: ["user", "client"] as const,
  useMemberAddMutation: vi.fn(),
}));

const setUp = () => {
  const mutate = vi.fn();
  vi.mocked(useNotifications).mockReturnValue({
    addNotification: vi.fn(),
  } as unknown as ReturnType<typeof useNotifications>);
  vi.mocked(useMemberAddMutation).mockReturnValue({
    mutate,
    isPending: false,
  } as unknown as ReturnType<typeof useMemberAddMutation>);
  return mutate;
};

describe("AddMemberModal", () => {
  it("asks for the member's identifier", async () => {
    const mutate = setUp();
    const screen = await render(<AddMemberModal isOpen onClose={vi.fn()} />);
    await screen.getByRole("button", { name: "Agregar" }).click();
    await expect
      .element(screen.getByText("Ingresa el identificador"))
      .toBeVisible();
    expect(mutate).not.toHaveBeenCalled();
  });

  it("adds an API client by its client id with the chosen role", async () => {
    const mutate = setUp();
    const screen = await render(<AddMemberModal isOpen onClose={vi.fn()} />);
    await screen.getByRole("radio", { name: "Cliente de API" }).click();
    await screen
      .getByRole("textbox", { name: "Client ID", exact: true })
      .fill("erp-bodega");
    await screen
      .getByRole("combobox", { name: "Rol" })
      .selectOptions("operator");
    await screen.getByRole("button", { name: "Agregar" }).click();

    await expect.poll(() => mutate.mock.calls.length).toBe(1);
    expect(mutate.mock.calls[0][0]).toEqual({
      kind: "client",
      subject: "erp-bodega",
      role: "operator",
    });
  });
});
