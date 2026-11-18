import { beforeEach, describe, expect, it, vi } from "vitest";
import { render } from "vitest-browser-react";

import { useTenantRole } from "@app/hooks/useTenantRole";
import { useMembersListQuery } from "@app/queries/xhandler/members";

import { MemberList } from "./MemberList";

vi.mock(import("@app/components/TenantContextSelector"), () => ({
  TenantContextSelector: () => null,
}));
vi.mock(import("./components/AddMemberModal"), () => ({
  AddMemberModal: () => null,
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
}));

const setUp = ({ isAdmin }: { isAdmin: boolean }) => {
  vi.mocked(useTenantRole).mockReturnValue({
    role: isAdmin ? "admin" : "operator",
    can: () => isAdmin,
    disabledProps: () =>
      isAdmin
        ? {}
        : {
            isAriaDisabled: true,
            tooltipProps: { content: "Necesitas el rol Administrador" },
          },
  } as unknown as ReturnType<typeof useTenantRole>);
  vi.mocked(useMembersListQuery).mockReturnValue({
    data: isAdmin
      ? [{ id: "m1", subject: "alice", kind: "user", role: "admin" }]
      : undefined,
    isLoading: false,
    error: null,
  } as unknown as ReturnType<typeof useMembersListQuery>);
};

describe("MemberList", () => {
  beforeEach(() => {
    vi.mocked(useMembersListQuery).mockClear();
  });

  it("lists the members for an admin", async () => {
    setUp({ isAdmin: true });
    const screen = await render(<MemberList />);

    await expect
      .element(screen.getByRole("gridcell", { name: "alice" }))
      .toBeVisible();
    expect(useMembersListQuery).toHaveBeenCalledWith({
      options: { enabled: true },
    });
  });

  it("shows the unauthorized state below admin, without reading the members", async () => {
    setUp({ isAdmin: false });
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
});
