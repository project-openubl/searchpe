import { describe, expect, it, vi } from "vitest";
import { render } from "vitest-browser-react";

import type { PublicDocument } from "@app/client/xhandler";
import { useNotifications } from "@app/hooks/useNotifications";
import {
  usePublicDocumentFileDownloadMutation,
  usePublicDocumentQuery,
} from "@app/queries/xhandler/public-documents";

import { DocumentAccess } from "./DocumentAccess";

vi.mock(import("@app/hooks/useNotifications"), () => ({
  useNotifications: vi.fn(),
}));
vi.mock(import("@app/queries/xhandler/public-documents"), () => ({
  usePublicDocumentQuery: vi.fn(),
  usePublicDocumentFileDownloadMutation: vi.fn(),
  isPublicDocumentFileKind: (value: string): value is "pdf" | "xml" | "cdr" =>
    ["pdf", "xml", "cdr"].includes(value),
  PUBLIC_DOCUMENT_FILE_KINDS: ["pdf", "xml", "cdr"] as const,
}));

const DOCUMENT: PublicDocument = {
  emisor_ruc: "20123456789",
  emisor_razon_social: "Bodega Lima SAC",
  tipo_comprobante: "01",
  serie_numero: "F001-25",
  fecha_emision: "2026-11-17",
  moneda: "PEN",
  total: 118,
  status: "aceptado",
  files: ["cdr", "pdf", "xml"],
  libro_reclamaciones_url: "https://bodega.example/reclamaciones",
};

const setUp = ({ data, error }: { data?: PublicDocument; error?: unknown }) => {
  const mutate = vi.fn();
  vi.mocked(useNotifications).mockReturnValue({
    addNotification: vi.fn(),
  } as unknown as ReturnType<typeof useNotifications>);
  vi.mocked(usePublicDocumentQuery).mockReturnValue({
    data,
    error,
    isLoading: false,
  } as unknown as ReturnType<typeof usePublicDocumentQuery>);
  vi.mocked(usePublicDocumentFileDownloadMutation).mockReturnValue({
    mutate,
    isPending: false,
  } as unknown as ReturnType<typeof usePublicDocumentFileDownloadMutation>);
  return mutate;
};

describe("DocumentAccess", () => {
  it("offers the document's files, PDF first", async () => {
    const mutate = setUp({ data: DOCUMENT });
    const screen = await render(<DocumentAccess token="t0k3n" />);

    await expect.element(screen.getByText("Factura F001-25")).toBeVisible();
    await expect.element(screen.getByText("Aceptado por SUNAT")).toBeVisible();
    const buttons = screen
      .getByRole("button", { name: /^Descargar/ })
      .elements()
      .map((button) => button.textContent);
    expect(buttons).toEqual([
      "Descargar PDF",
      "Descargar XML",
      "Descargar CDR",
    ]);

    await screen.getByRole("button", { name: "Descargar XML" }).click();
    expect(mutate).toHaveBeenCalledExactlyOnceWith({
      kind: "xml",
      serieNumero: "F001-25",
    });
  });

  it("links the issuer's Libro de Reclamaciones in a new tab", async () => {
    setUp({ data: DOCUMENT });
    const screen = await render(<DocumentAccess token="t0k3n" />);

    const link = screen.getByRole("link", {
      name: "Libro de Reclamaciones de Bodega Lima SAC",
    });
    await expect
      .element(link)
      .toHaveAttribute("href", "https://bodega.example/reclamaciones");
    await expect.element(link).toHaveAttribute("target", "_blank");
  });

  it("has no Libro de Reclamaciones link when the issuer set none", async () => {
    setUp({ data: { ...DOCUMENT, libro_reclamaciones_url: undefined } });
    const screen = await render(<DocumentAccess token="t0k3n" />);

    await expect.element(screen.getByText("Factura F001-25")).toBeVisible();
    expect(
      screen.getByRole("link", { name: /Libro de Reclamaciones/ }).elements(),
    ).toHaveLength(0);
  });

  it("warns that a test document has no tax validity", async () => {
    setUp({ data: { ...DOCUMENT, status: "documento_de_prueba" } });
    const screen = await render(<DocumentAccess token="t0k3n" />);

    await expect
      .element(
        screen.getByText(
          "Este comprobante se emitió en un ambiente de pruebas y no tiene validez tributaria.",
        ),
      )
      .toBeVisible();
  });

  it("explains an invalid link instead of failing", async () => {
    setUp({
      error: { isAxiosError: true, response: { status: 404 } },
    });
    const screen = await render(<DocumentAccess token="t0k3n" />);

    await expect.element(screen.getByText("Enlace no válido")).toBeVisible();
  });
});
