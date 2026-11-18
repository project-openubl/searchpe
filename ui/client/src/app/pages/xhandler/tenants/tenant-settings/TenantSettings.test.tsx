import { describe, expect, it, vi } from "vitest";
import { render } from "vitest-browser-react";

import { useNotifications } from "@app/hooks/useNotifications";
import { useTenantRole } from "@app/hooks/useTenantRole";
import {
  useNotificationSettingsMutation,
  useNotificationSettingsQuery,
} from "@app/queries/xhandler/notifications";
import {
  useTenantDetailQuery,
  useTenantUpdateMutation,
} from "@app/queries/xhandler/tenants";

import { TenantSettings } from "./TenantSettings";

vi.mock(import("@app/components/TenantContextSelector"), () => ({
  TenantContextSelector: () => null,
}));
vi.mock(import("@app/hooks/useSelectedTenantId"), () => ({
  useSelectedTenantId: () => "t1",
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
vi.mock(import("@app/queries/xhandler/notifications"), () => ({
  useNotificationSettingsQuery: vi.fn(),
  useNotificationSettingsMutation: vi.fn(),
}));
vi.mock(import("@app/queries/xhandler/tenants"), () => ({
  useTenantDetailQuery: vi.fn(),
  useTenantUpdateMutation: vi.fn(),
}));

const setUp = ({ isAdmin }: { isAdmin: boolean }) => {
  vi.mocked(useTenantRole).mockReturnValue({
    role: isAdmin ? "admin" : "viewer",
    can: () => isAdmin,
    disabledProps: () =>
      isAdmin
        ? {}
        : {
            isAriaDisabled: true,
            tooltipProps: { content: "Necesitas el rol Administrador" },
          },
  } as unknown as ReturnType<typeof useTenantRole>);
  vi.mocked(useNotifications).mockReturnValue({
    addNotification: vi.fn(),
  } as unknown as ReturnType<typeof useNotifications>);
  vi.mocked(useTenantDetailQuery).mockReturnValue({
    data: { id: "t1", name: "Bodega SAC", status: "active", plan: "free" },
    isLoading: false,
    error: null,
  } as unknown as ReturnType<typeof useTenantDetailQuery>);
  vi.mocked(useNotificationSettingsQuery).mockReturnValue({
    data: { notification_email: "avisos@bodega.pe" },
    isLoading: false,
    error: null,
  } as unknown as ReturnType<typeof useNotificationSettingsQuery>);
  const rename = vi.fn();
  vi.mocked(useTenantUpdateMutation).mockReturnValue({
    mutate: rename,
    isPending: false,
  } as unknown as ReturnType<typeof useTenantUpdateMutation>);
  vi.mocked(useNotificationSettingsMutation).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useNotificationSettingsMutation>);
  return rename;
};

describe("TenantSettings", () => {
  it("starts the forms with the current name and notification email", async () => {
    setUp({ isAdmin: true });
    const screen = await render(<TenantSettings />);

    await expect
      .element(screen.getByRole("textbox", { name: "Nombre", exact: true }))
      .toHaveValue("Bodega SAC");
    await expect
      .element(screen.getByRole("textbox", { name: "Correo", exact: true }))
      .toHaveValue("avisos@bodega.pe");
  });

  it("renames the organization", async () => {
    const rename = setUp({ isAdmin: true });
    const screen = await render(<TenantSettings />);

    await screen
      .getByRole("textbox", { name: "Nombre", exact: true })
      .fill("Bodega Norte SAC");
    await screen.getByRole("button", { name: "Guardar nombre" }).click();

    await expect.poll(() => rename.mock.calls.length).toBe(1);
    expect(rename.mock.calls[0][0]).toEqual({ name: "Bodega Norte SAC" });
  });

  it("asks for a name", async () => {
    const rename = setUp({ isAdmin: true });
    const screen = await render(<TenantSettings />);

    await screen.getByRole("textbox", { name: "Nombre", exact: true }).fill("");
    await screen.getByRole("button", { name: "Guardar nombre" }).click();

    await expect
      .element(screen.getByText("Ingresa el nombre de la organización"))
      .toBeVisible();
    expect(rename).not.toHaveBeenCalled();
  });

  it("doesn't let a viewer rename", async () => {
    setUp({ isAdmin: false });
    const screen = await render(<TenantSettings />);

    await expect
      .element(screen.getByRole("button", { name: "Guardar nombre" }))
      .toHaveAttribute("aria-disabled", "true");
  });
});
