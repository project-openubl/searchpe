import { beforeEach, describe, expect, it, vi } from "vitest";
import { render } from "vitest-browser-react";

import { useNotifications } from "@app/hooks/useNotifications";
import { useTenantRole } from "@app/hooks/useTenantRole";
import {
  useMemberRemoveMutation,
  useMembersListQuery,
} from "@app/queries/xhandler/members";

import { MemberList } from "./MemberList";

vi.mock(import("@app/components/TenantContextSelector"), () => ({
  TenantContextSelector: () => null,
}));
vi.mock(import("./components/AddMemberModal"), () => ({
  AddMemberModal: () => null,
}));
vi.mock(import("./components/ChangeMemberRoleModal"), () => ({
  ChangeMemberRoleModal: () => null,
}));
vi.mock(import("@app/hooks/useNotifications"), () => ({
  useNotifications: vi.fn(),
}));
vi.mock(import("@app/hooks/useTenantRole"), () => ({
  TENANT_ROLE_LABELS: {
    owner: "Propietario",
    admin: "Administrador",
    operator: "Operador",
    viewer: "Lector",
    support: "Soporte",
  },
  useTenantRole: vi.fn(),
}));
vi.mock(import("@app/queries/xhandler/members"), () => ({
  useMembersListQuery: vi.fn(),
  useMemberRemoveMutation: vi.fn(),
}));

const RANK = ["owner", "admin", "operator", "viewer"];

const setUp = ({ role }: { role: "owner" | "admin" | "operator" }) => {
  const can = (minimum: string) => RANK.indexOf(role) <= RANK.indexOf(minimum);
  vi.mocked(useTenantRole).mockReturnValue({
    role,
    can,
    disabledProps: (minimum: string) =>
      can(minimum)
        ? {}
        : {
            isAriaDisabled: true,
            tooltipProps: { content: "Necesitas otro rol" },
          },
  } as unknown as ReturnType<typeof useTenantRole>);
  vi.mocked(useNotifications).mockReturnValue({
    addNotification: vi.fn(),
  } as unknown as ReturnType<typeof useNotifications>);
  const mutate = vi.fn();
  vi.mocked(useMemberRemoveMutation).mockReturnValue({
    mutate,
    isPending: false,
  } as unknown as ReturnType<typeof useMemberRemoveMutation>);
  vi.mocked(useMembersListQuery).mockReturnValue({
    data:
      role === "operator"
        ? undefined
        : [
            { id: "m1", subject: "alice", kind: "user", role: "owner" },
            { id: "m2", subject: "bob", kind: "user", role: "operator" },
          ],
    isLoading: false,
    error: null,
  } as unknown as ReturnType<typeof useMembersListQuery>);
  return mutate;
};

const openActions = async (
  screen: Awaited<ReturnType<typeof render>>,
  index: number,
) => {
  await screen.getByRole("button", { name: "Kebab toggle" }).nth(index).click();
};

describe("MemberList", () => {
  beforeEach(() => {
    vi.mocked(useMembersListQuery).mockClear();
  });

  it("lists the members for an admin", async () => {
    setUp({ role: "admin" });
    const screen = await render(<MemberList />);

    await expect
      .element(screen.getByRole("gridcell", { name: "alice" }))
      .toBeVisible();
    expect(useMembersListQuery).toHaveBeenCalledWith({
      options: { enabled: true },
    });
  });

  it("shows the unauthorized state below admin, without reading the members", async () => {
    setUp({ role: "operator" });
    const screen = await render(<MemberList />);

    await expect
      .element(screen.getByText("403: Acceso denegado"))
      .toBeVisible();
    expect(
      screen.getByRole("grid", { name: "Tabla de miembros" }).elements(),
    ).toHaveLength(0);
    expect(useMembersListQuery).toHaveBeenCalledWith({
      options: { enabled: false },
    });
  });

  it("doesn't let an admin act on an owner", async () => {
    setUp({ role: "admin" });
    const screen = await render(<MemberList />);

    await openActions(screen, 0);
    await expect
      .element(screen.getByRole("menuitem", { name: "Quitar" }))
      .toHaveAttribute("aria-disabled", "true");
    await expect
      .element(screen.getByRole("menuitem", { name: "Cambiar rol" }))
      .toHaveAttribute("aria-disabled", "true");
  });

  it("lets an admin act on a member below owner", async () => {
    setUp({ role: "admin" });
    const screen = await render(<MemberList />);

    await openActions(screen, 1);
    await expect
      .element(screen.getByRole("menuitem", { name: "Quitar" }))
      .not.toHaveAttribute("aria-disabled", "true");
  });

  it("removes a member after confirming", async () => {
    const mutate = setUp({ role: "owner" });
    const screen = await render(<MemberList />);

    await openActions(screen, 1);
    await screen.getByRole("menuitem", { name: "Quitar" }).click();
    await expect
      .element(
        screen.getByText("bob dejará de tener acceso a esta organización."),
      )
      .toBeVisible();
    await screen.getByRole("button", { name: "Quitar", exact: true }).click();

    expect(mutate).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({ id: "m2", subject: "bob" }),
      expect.anything(),
    );
  });
});
