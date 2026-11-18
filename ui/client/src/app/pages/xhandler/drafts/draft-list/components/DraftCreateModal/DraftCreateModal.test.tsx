import { describe, expect, it, vi } from "vitest";
import { render } from "vitest-browser-react";

import { useNotifications } from "@app/hooks/useNotifications";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useCompaniesListQuery } from "@app/queries/xhandler/companies";
import { useDraftCreateMutation } from "@app/queries/xhandler/drafts";

import { DraftCreateModal } from "./DraftCreateModal";

vi.mock(import("@tanstack/react-router"), () => ({
  useNavigate: vi.fn(() => vi.fn()),
}));
vi.mock(import("@app/hooks/useNotifications"), () => ({
  useNotifications: vi.fn(),
}));
vi.mock(import("@app/hooks/useSelectedTenantId"), () => ({
  useSelectedTenantId: vi.fn(),
}));
vi.mock(import("@app/queries/xhandler/companies"), () => ({
  useCompaniesListQuery: vi.fn(),
}));
vi.mock(import("@app/queries/xhandler/drafts"), () => ({
  useDraftCreateMutation: vi.fn(),
}));

const setUp = () => {
  const mutate = vi.fn();
  vi.mocked(useNotifications).mockReturnValue({
    addNotification: vi.fn(),
  } as unknown as ReturnType<typeof useNotifications>);
  vi.mocked(useSelectedTenantId).mockReturnValue("t1");
  vi.mocked(useCompaniesListQuery).mockReturnValue({
    data: {
      items: [
        { id: "c1", ruc: "20123456789", razon_social: "Bodega Lima SAC" },
        { id: "c2", ruc: "20999999999", razon_social: "Ferretería Sur SAC" },
      ],
      limit: 10,
      offset: 0,
    },
    isLoading: false,
  } as unknown as ReturnType<typeof useCompaniesListQuery>);
  vi.mocked(useDraftCreateMutation).mockReturnValue({
    mutate,
    isPending: false,
  } as unknown as ReturnType<typeof useDraftCreateMutation>);
  return mutate;
};

describe("DraftCreateModal", () => {
  it("starts a factura of the first company by default", async () => {
    const mutate = setUp();
    const screen = await render(<DraftCreateModal isOpen onClose={vi.fn()} />);

    await screen.getByRole("button", { name: "Crear borrador" }).click();

    await expect.poll(() => mutate.mock.calls.length).toBe(1);
    const variables = mutate.mock.calls[0][0] as Record<string, unknown>;
    expect(variables).toMatchObject({
      companyId: "c1",
      body: {
        kind: "Invoice",
        spec: { tipo_comprobante: "01", moneda: "PEN" },
      },
    });
    expect(typeof variables.idempotencyKey).toBe("string");
  });

  it("starts a credit note as its own kind", async () => {
    const mutate = setUp();
    const screen = await render(<DraftCreateModal isOpen onClose={vi.fn()} />);

    await screen
      .getByRole("combobox", { name: "Empresa" })
      .selectOptions("Ferretería Sur SAC (RUC 20999999999)");
    await screen
      .getByRole("combobox", { name: "Tipo de comprobante" })
      .selectOptions("Nota de crédito");
    await screen.getByRole("button", { name: "Crear borrador" }).click();

    await expect.poll(() => mutate.mock.calls.length).toBe(1);
    expect(mutate.mock.calls[0][0]).toMatchObject({
      companyId: "c2",
      body: { kind: "CreditNote", spec: { moneda: "PEN" } },
    });
  });
});
