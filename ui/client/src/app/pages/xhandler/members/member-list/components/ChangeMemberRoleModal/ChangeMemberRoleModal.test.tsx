import { describe, expect, it, vi } from "vitest";
import { render } from "vitest-browser-react";

import { useNotifications } from "@app/hooks/useNotifications";
import { useMemberUpdateMutation } from "@app/queries/xhandler/members";

import { ChangeMemberRoleModal } from "./ChangeMemberRoleModal";

vi.mock(import("@app/hooks/useNotifications"), () => ({
  useNotifications: vi.fn(),
}));
vi.mock(import("@app/queries/xhandler/members"), () => ({
  ASSIGNABLE_MEMBER_ROLES: ["admin", "operator", "viewer"] as const,
  useMemberUpdateMutation: vi.fn(),
}));

const setUp = () => {
  const mutate = vi.fn();
  vi.mocked(useNotifications).mockReturnValue({
    addNotification: vi.fn(),
  } as unknown as ReturnType<typeof useNotifications>);
  vi.mocked(useMemberUpdateMutation).mockReturnValue({
    mutate,
    isPending: false,
  } as unknown as ReturnType<typeof useMemberUpdateMutation>);
  return mutate;
};

describe("ChangeMemberRoleModal", () => {
  it("starts on the member's role and sends the chosen one", async () => {
    const mutate = setUp();
    const member = { id: "m2", subject: "bob", kind: "user", role: "viewer" };
    const screen = await render(
      <ChangeMemberRoleModal member={member} onClose={vi.fn()} />,
    );
    const select = screen.getByRole("combobox", { name: "Rol" });
    await expect.element(select).toHaveValue("viewer");

    await select.selectOptions("operator");
    await screen.getByRole("button", { name: "Guardar" }).click();

    await expect.poll(() => mutate.mock.calls.length).toBe(1);
    expect(mutate.mock.calls[0][0]).toEqual({ member, role: "operator" });
  });

  it("offers admin for an owner, since owner isn't granted here", async () => {
    setUp();
    const screen = await render(
      <ChangeMemberRoleModal
        member={{ id: "m1", subject: "alice", kind: "user", role: "owner" }}
        onClose={vi.fn()}
      />,
    );
    await expect
      .element(screen.getByRole("combobox", { name: "Rol" }))
      .toHaveValue("admin");
    expect(
      screen.getByRole("option", { name: "Propietario" }).elements(),
    ).toHaveLength(0);
  });

  it("stays closed without a member", async () => {
    setUp();
    const screen = await render(
      <ChangeMemberRoleModal member={null} onClose={vi.fn()} />,
    );
    expect(screen.getByRole("dialog").elements()).toHaveLength(0);
  });
});
