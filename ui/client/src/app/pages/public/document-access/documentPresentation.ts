import {
  PUBLIC_DOCUMENT_FILE_KINDS,
  type PublicDocumentFileKind,
} from "@app/queries/xhandler/public-documents";

/** The public statuses of `PublicDocumentResource.publicStatus` (plus test documents). */
export const PUBLIC_DOCUMENT_STATUS_LABELS: Record<string, string> = {
  aceptado: "Aceptado por SUNAT",
  rechazado: "Rechazado por SUNAT",
  anulado: "Anulado",
  en_anulacion: "En proceso de anulación",
  pendiente: "Pendiente de validación por SUNAT",
  documento_de_prueba: "Documento de prueba (sin validez tributaria)",
};

/** The status' Spanish label; an unknown status shows as sent. */
export const publicDocumentStatusLabel = (
  status: string | undefined,
): string => (status ? (PUBLIC_DOCUMENT_STATUS_LABELS[status] ?? status) : "-");

/** How the buyer reads each downloadable file. */
export const PUBLIC_DOCUMENT_FILE_LABELS: Record<
  PublicDocumentFileKind,
  string
> = {
  pdf: "PDF",
  xml: "XML",
  cdr: "CDR",
};

/** PDF first (what a buyer usually wants), then XML and CDR. */
export const publicFileOrder = (
  kinds: readonly PublicDocumentFileKind[],
): PublicDocumentFileKind[] =>
  PUBLIC_DOCUMENT_FILE_KINDS.filter((kind) => kinds.includes(kind));
