import { describe, expect, it, vi } from "vitest";
import { render } from "vitest-browser-react";

import { useNotifications } from "@app/hooks/useNotifications";
import { useCredentialSetMutation } from "@app/queries/xhandler/credentials";

import { CredentialModal } from "./CredentialModal";

vi.mock(import("@app/hooks/useNotifications"), () => ({
  useNotifications: vi.fn(),
}));
vi.mock(import("@app/queries/xhandler/credentials"), () => ({
  useCredentialSetMutation: vi.fn(),
}));

const setUp = () => {
  const mutate = vi.fn();
  vi.mocked(useNotifications).mockReturnValue({
    addNotification: vi.fn(),
  } as unknown as ReturnType<typeof useNotifications>);
  vi.mocked(useCredentialSetMutation).mockReturnValue({
    mutate,
    isPending: false,
  } as unknown as ReturnType<typeof useCredentialSetMutation>);
  return mutate;
};

describe("CredentialModal", () => {
  it("starts with empty, masked secret fields", async () => {
    setUp();
    const screen = await render(
      <CredentialModal kind="gre" companyId="c1" onClose={vi.fn()} />,
    );
    const password = screen.getByRole("textbox", {
      name: "Clave SOL",
      exact: true,
    });
    await expect.element(password).toHaveValue("");
    await expect.element(password).toHaveAttribute("type", "password");
    await expect
      .element(
        screen.getByRole("textbox", { name: "Client secret", exact: true }),
      )
      .toHaveAttribute("type", "password");
  });

  it("asks for what the kind requires", async () => {
    const mutate = setUp();
    const screen = await render(
      <CredentialModal kind="sol" companyId="c1" onClose={vi.fn()} />,
    );
    await screen.getByRole("button", { name: "Guardar" }).click();
    await expect.element(screen.getByText("Ingresa el usuario")).toBeVisible();
    await expect
      .element(screen.getByText("Ingresa la contraseña"))
      .toBeVisible();
    expect(mutate).not.toHaveBeenCalled();
  });

  it("sends the kind with its fields", async () => {
    const mutate = setUp();
    const screen = await render(
      <CredentialModal kind="sol" companyId="c1" onClose={vi.fn()} />,
    );
    await screen
      .getByRole("textbox", { name: "Usuario SOL", exact: true })
      .fill("20123456789MODDATOS");
    await screen
      .getByRole("textbox", { name: "Clave SOL", exact: true })
      .fill("moddatos");
    await screen.getByRole("button", { name: "Guardar" }).click();

    await expect.poll(() => mutate.mock.calls.length).toBe(1);
    expect(mutate.mock.calls[0][0]).toEqual({
      kind: "sol",
      body: { username: "20123456789MODDATOS", password: "moddatos" },
    });
  });
});
