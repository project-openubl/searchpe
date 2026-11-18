import { describe, expect, it, vi } from "vitest";
import { render } from "vitest-browser-react";

import { useNotifications } from "@app/hooks/useNotifications";
import { useCreatePartnerTenantMutation } from "@app/queries/xhandler/partners";

import { CreatePartnerTenantModal } from "./CreatePartnerTenantModal";

vi.mock(import("@app/hooks/useNotifications"), () => ({
  useNotifications: vi.fn(),
}));
vi.mock(import("@app/queries/xhandler/partners"), () => ({
  useCreatePartnerTenantMutation: vi.fn(),
}));

const setUp = () => {
  const mutate = vi.fn();
  vi.mocked(useNotifications).mockReturnValue({
    addNotification: vi.fn(),
  } as unknown as ReturnType<typeof useNotifications>);
  vi.mocked(useCreatePartnerTenantMutation).mockReturnValue({
    mutate,
    isPending: false,
  } as unknown as ReturnType<typeof useCreatePartnerTenantMutation>);
  return mutate;
};

describe("CreatePartnerTenantModal", () => {
  it("asks for the organization's name", async () => {
    const mutate = setUp();
    const screen = await render(
      <CreatePartnerTenantModal partnerId="p1" isOpen onClose={vi.fn()} />,
    );

    await screen.getByRole("button", { name: "Crear" }).click();

    await expect
      .element(screen.getByText("Ingresa el nombre de la organización"))
      .toBeVisible();
    expect(mutate).not.toHaveBeenCalled();
  });

  it("sends the owner only when one is given", async () => {
    const mutate = setUp();
    const screen = await render(
      <CreatePartnerTenantModal partnerId="p1" isOpen onClose={vi.fn()} />,
    );

    await screen
      .getByRole("textbox", { name: "Nombre de la organización", exact: true })
      .fill("Bodega Lima");
    await screen.getByRole("button", { name: "Crear" }).click();

    await expect.poll(() => mutate.mock.calls.length).toBe(1);
    expect(mutate.mock.calls[0][0]).toEqual({ name: "Bodega Lima" });
  });
});
