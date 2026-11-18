import { describe, expect, it, vi } from "vitest";
import { render } from "vitest-browser-react";

import { useTenantRole } from "@app/hooks/useTenantRole";
import {
  useCredentialSetMutation,
  useCredentialsListQuery,
} from "@app/queries/xhandler/credentials";

import { CredentialsPanel } from "./CredentialsPanel";

vi.mock(import("@app/hooks/useNotifications"), () => ({
  useNotifications: vi.fn(() => ({ addNotification: vi.fn() })),
}));
vi.mock(import("@app/hooks/useTenantRole"), () => ({
  useTenantRole: vi.fn(),
}));
vi.mock(import("@app/queries/xhandler/credentials"), () => ({
  CREDENTIAL_KINDS: ["sol", "gre", "sire", "smtp", "lookup"] as const,
  useCredentialsListQuery: vi.fn(),
  useCredentialSetMutation: vi.fn(),
}));

const setUp = ({ canAdmin }: { canAdmin: boolean }) => {
  vi.mocked(useTenantRole).mockReturnValue({
    role: canAdmin ? "admin" : "viewer",
    can: () => canAdmin,
    disabledProps: () =>
      canAdmin
        ? {}
        : {
            isAriaDisabled: true,
            tooltipProps: { content: "Necesitas el rol Administrador" },
          },
  } as unknown as ReturnType<typeof useTenantRole>);
  vi.mocked(useCredentialsListQuery).mockReturnValue({
    data: [
      {
        kind: "sol",
        label: "2012345****",
        updated_at: "2026-11-01T10:00:00Z",
      },
    ],
    isLoading: false,
    error: null,
  } as unknown as ReturnType<typeof useCredentialsListQuery>);
  vi.mocked(useCredentialSetMutation).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useCredentialSetMutation>);
};

describe("CredentialsPanel", () => {
  it("shows which kinds are set, with the masked label only", async () => {
    setUp({ canAdmin: true });
    const screen = await render(<CredentialsPanel companyId="c1" />);
    const table = screen.getByRole("grid", { name: "Tabla de credenciales" });
    await expect.element(table.getByText("Configurada")).toBeVisible();
    await expect.element(table.getByText("2012345****")).toBeVisible();
    await expect
      .element(table.getByText("Sin configurar").first())
      .toBeVisible();
  });

  it("opens an empty form to replace a credential", async () => {
    setUp({ canAdmin: true });
    const screen = await render(<CredentialsPanel companyId="c1" />);
    await screen.getByRole("button", { name: "Kebab toggle" }).first().click();
    await screen.getByRole("menuitem", { name: "Reemplazar" }).click();
    await expect
      .element(screen.getByRole("textbox", { name: "Clave SOL", exact: true }))
      .toHaveValue("");
  });

  it("disables the action for roles below admin", async () => {
    setUp({ canAdmin: false });
    const screen = await render(<CredentialsPanel companyId="c1" />);
    await screen.getByRole("button", { name: "Kebab toggle" }).first().click();
    await expect
      .element(screen.getByRole("menuitem", { name: "Reemplazar" }))
      .toHaveAttribute("aria-disabled", "true");
  });
});
