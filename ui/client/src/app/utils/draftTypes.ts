import type { DraftView } from "@app/client/xhandler";

/** The documents the console issues by hand (DESIGN.md §11.10): xbuilder kind and catalog 01 code. */
export const DRAFT_TYPES = [
  { tipo: "01", kind: "Invoice", label: "Factura" },
  { tipo: "03", kind: "Invoice", label: "Boleta de venta" },
  { tipo: "07", kind: "CreditNote", label: "Nota de crédito" },
  { tipo: "08", kind: "DebitNote", label: "Nota de débito" },
] as const;

export type DraftTipo = (typeof DRAFT_TYPES)[number]["tipo"];

export const isDraftTipo = (value: string): value is DraftTipo =>
  DRAFT_TYPES.some((type) => type.tipo === value);

/** The draft's document type as the user reads it; another kind shows as the API names it. */
export const draftTypeLabel = (draft: DraftView): string =>
  DRAFT_TYPES.find((type) => type.tipo === draft.tipo_comprobante)?.label ??
  draft.kind ??
  "-";

/** Notes (07, 08) refer to the document they correct. */
export const isNoteTipo = (tipo: string | undefined): boolean =>
  tipo === "07" || tipo === "08";
