import { describe, expect, it, vi } from "vitest";
import { userEvent } from "vitest/browser";
import { render } from "vitest-browser-react";

import type { DraftView } from "@app/client/xhandler";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useTenantRole } from "@app/hooks/useTenantRole";
import { useSuspenseCompanyDetailQuery } from "@app/queries/xhandler/companies";
import { useDocumentDetailQuery } from "@app/queries/xhandler/documents";
import {
  useDraftDeleteMutation,
  useDraftIssueMutation,
  useDraftUpdateMutation,
  useSuspenseDraftDetailQuery,
} from "@app/queries/xhandler/drafts";
import { useSeriesListQuery } from "@app/queries/xhandler/series";

import { DraftEdit } from "./DraftEdit";

vi.mock(import("@tanstack/react-router"), () => ({
  Link: (({ children }: { children: React.ReactNode }) => (
    <a href="/borradores">{children}</a>
  )) as never,
  useNavigate: vi.fn(() => vi.fn()),
}));
vi.mock(import("@app/hooks/useNotifications"), () => ({
  useNotifications: vi.fn(),
}));
vi.mock(import("@app/hooks/useSelectedTenantId"), () => ({
  useSelectedTenantId: vi.fn(),
}));
vi.mock(import("@app/hooks/useTenantRole"), () => ({
  useTenantRole: vi.fn(),
}));
vi.mock(import("@app/queries/xhandler/companies"), () => ({
  useSuspenseCompanyDetailQuery: vi.fn(),
}));
vi.mock(import("@app/queries/xhandler/documents"), () => ({
  useDocumentDetailQuery: vi.fn(),
}));
vi.mock(import("@app/queries/xhandler/series"), () => ({
  useSeriesListQuery: vi.fn(),
}));
vi.mock(import("@app/queries/xhandler/drafts"), () => ({
  useSuspenseDraftDetailQuery: vi.fn(),
  useDraftUpdateMutation: vi.fn(),
  useDraftIssueMutation: vi.fn(),
  useDraftDeleteMutation: vi.fn(),
}));

const DRAFT: DraftView = {
  id: "d1",
  company_id: "c1",
  kind: "Invoice",
  tipo_comprobante: "01",
  status: "draft",
  version: 4,
  spec: {
    tipo_comprobante: "01",
    moneda: "PEN",
    cliente: {
      tipo_documento_identidad: "6",
      numero_documento_identidad: "20123456789",
      nombre: "Cliente SAC",
    },
    detalles: [{ descripcion: "Servicio", cantidad: 1, precio: 100 }],
  },
};

const setUp = ({
  draft = DRAFT,
  canWrite = true,
}: { draft?: DraftView; canWrite?: boolean } = {}) => {
  const update = vi.fn();
  const updateAsync = vi.fn().mockResolvedValue({ ...draft, version: 5 });
  const issueAsync = vi
    .fn()
    .mockResolvedValue({ id: "doc1", serie_numero: "F001-1" });
  vi.mocked(useNotifications).mockReturnValue({
    addNotification: vi.fn(),
  } as unknown as ReturnType<typeof useNotifications>);
  vi.mocked(useSelectedTenantId).mockReturnValue("t1");
  vi.mocked(useTenantRole).mockReturnValue({
    role: canWrite ? "operator" : "viewer",
    can: () => canWrite,
    disabledProps: () =>
      canWrite
        ? {}
        : {
            isAriaDisabled: true,
            tooltipProps: { content: "Necesitas el rol Operador" },
          },
  } as unknown as ReturnType<typeof useTenantRole>);
  vi.mocked(useSuspenseDraftDetailQuery).mockReturnValue({
    data: draft,
  } as unknown as ReturnType<typeof useSuspenseDraftDetailQuery>);
  vi.mocked(useSuspenseCompanyDetailQuery).mockReturnValue({
    data: {
      id: "c1",
      ruc: "20111111111",
      razon_social: "Bodega Lima SAC",
      sunat_env: "beta",
    },
  } as unknown as ReturnType<typeof useSuspenseCompanyDetailQuery>);
  vi.mocked(useDocumentDetailQuery).mockReturnValue({
    data: { id: "doc1", serie_numero: "F001-7" },
  } as unknown as ReturnType<typeof useDocumentDetailQuery>);
  vi.mocked(useSeriesListQuery).mockReturnValue({
    data: [
      {
        serie: "F001",
        tipo_comprobante: "01",
        sunat_env: "beta",
        active: true,
      },
      {
        serie: "F009",
        tipo_comprobante: "01",
        sunat_env: "production",
        active: true,
      },
      {
        serie: "B001",
        tipo_comprobante: "03",
        sunat_env: "beta",
        active: true,
      },
    ],
  } as unknown as ReturnType<typeof useSeriesListQuery>);
  vi.mocked(useDraftUpdateMutation).mockReturnValue({
    mutate: update,
    mutateAsync: updateAsync,
    isPending: false,
  } as unknown as ReturnType<typeof useDraftUpdateMutation>);
  vi.mocked(useDraftIssueMutation).mockReturnValue({
    mutateAsync: issueAsync,
    isPending: false,
  } as unknown as ReturnType<typeof useDraftIssueMutation>);
  vi.mocked(useDraftDeleteMutation).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useDraftDeleteMutation>);
  return { update, updateAsync, issueAsync };
};

describe("DraftEdit", () => {
  it("offers only the company's series of this type and environment", async () => {
    setUp();
    const screen = await render(<DraftEdit draftId="d1" />);

    const options = screen
      .getByRole("combobox", { name: "Serie" })
      .getByRole("option")
      .elements()
      .map((option) => option.textContent);
    expect(options).toEqual(["Serie por defecto", "F001"]);
  });

  it("checks SUNAT's formats before saving", async () => {
    const { update } = setUp();
    const screen = await render(<DraftEdit draftId="d1" />);

    await screen
      .getByRole("textbox", { name: "Número de documento", exact: true })
      .fill("123");
    await screen
      .getByRole("textbox", { name: "Descripción", exact: true })
      .fill("");
    await screen.getByRole("button", { name: "Guardar borrador" }).click();

    await expect
      .element(screen.getByText("El RUC tiene 11 dígitos"))
      .toBeVisible();
    await expect
      .element(screen.getByText("Ingresa la descripción"))
      .toBeVisible();
    expect(update).not.toHaveBeenCalled();
  });

  it("saves the edited version", async () => {
    const { update } = setUp();
    const screen = await render(<DraftEdit draftId="d1" />);

    await screen
      .getByRole("textbox", { name: "Nombre o razón social", exact: true })
      .fill("Otro Cliente SAC");
    await screen.getByRole("button", { name: "Agregar detalle" }).click();
    await screen
      .getByRole("group", { name: "Detalle 2" })
      .getByRole("textbox", { name: "Descripción", exact: true })
      .fill("Envío");
    await screen
      .getByRole("group", { name: "Detalle 2" })
      .getByRole("textbox", { name: "Valor unitario (sin IGV)" })
      .fill("15.50");
    // Leave the last field first: its blur re-lays the narrow test viewport out under the pointer.
    await userEvent.tab();
    await screen.getByRole("button", { name: "Guardar borrador" }).click();

    await expect.poll(() => update.mock.calls.length).toBe(1);
    expect(update.mock.calls[0][0]).toMatchObject({
      kind: "Invoice",
      version: 4,
      spec: {
        cliente: { nombre: "Otro Cliente SAC" },
        detalles: [
          { descripcion: "Servicio", cantidad: 1, precio: 100 },
          { descripcion: "Envío", cantidad: 1, precio: 15.5 },
        ],
      },
    });
  });

  it("issues after a confirmation, saving pending changes first", async () => {
    const { updateAsync, issueAsync } = setUp();
    const screen = await render(<DraftEdit draftId="d1" />);

    await screen
      .getByRole("textbox", { name: "Nombre o razón social", exact: true })
      .fill("Cliente Final SAC");
    await screen.getByRole("button", { name: "Emitir", exact: true }).click();
    const dialog = screen.getByRole("dialog");
    await expect
      .element(dialog.getByText("¿Emitir el comprobante?"))
      .toBeVisible();
    await dialog.getByRole("button", { name: "Emitir", exact: true }).click();

    await expect.poll(() => issueAsync.mock.calls.length).toBe(1);
    expect(updateAsync).toHaveBeenCalledOnce();
    expect(updateAsync.mock.calls[0][0]).toMatchObject({ version: 4 });
    // The version just saved is the one issued.
    expect(issueAsync.mock.calls[0][0]).toMatchObject({
      draftId: "d1",
      version: 5,
    });
  });

  it("shows an issue failure in the form", async () => {
    const { issueAsync } = setUp();
    issueAsync.mockRejectedValue({
      isAxiosError: true,
      response: {
        status: 422,
        data: {
          kind: "invalid_document",
          message: "El documento no cumple las validaciones de SUNAT.",
          detail: "3105: falta algo",
        },
      },
    });
    const screen = await render(<DraftEdit draftId="d1" />);

    await screen.getByRole("button", { name: "Emitir", exact: true }).click();
    await screen
      .getByRole("dialog")
      .getByRole("button", { name: "Emitir", exact: true })
      .click();

    await expect
      .element(screen.getByText("No se pudo emitir el comprobante"))
      .toBeVisible();
    await expect.element(screen.getByText("3105: falta algo")).toBeVisible();
  });

  it("disables the writes for a viewer", async () => {
    setUp({ canWrite: false });
    const screen = await render(<DraftEdit draftId="d1" />);

    await expect
      .element(screen.getByRole("button", { name: "Emitir", exact: true }))
      .toHaveAttribute("aria-disabled", "true");
    await expect
      .element(screen.getByRole("button", { name: "Guardar borrador" }))
      .toHaveAttribute("aria-disabled", "true");
  });

  it("shows an issued draft read-only, with its document", async () => {
    setUp({ draft: { ...DRAFT, status: "issued", document_id: "doc1" } });
    const screen = await render(<DraftEdit draftId="d1" />);

    await expect
      .element(
        screen.getByText(
          "Este borrador ya se emitió: los comprobantes emitidos no se modifican.",
        ),
      )
      .toBeVisible();
    await expect.element(screen.getByText("F001-7")).toBeVisible();
    expect(
      screen.getByRole("button", { name: "Guardar borrador" }).elements(),
    ).toHaveLength(0);
  });
});
