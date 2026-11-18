import type { DocumentView } from "@app/client/xhandler";
import {
  isDocumentRetryable,
  isDocumentVoidable,
} from "@app/queries/xhandler/documents";

/** Why an action is unavailable for the document, or undefined when the server may accept it. */
export interface IDocumentActionAvailability {
  retry: string | undefined;
  void: string | undefined;
  cancelVoid: string | undefined;
  cdr: string | undefined;
  files: string | undefined;
}

/**
 * What the detail page offers, from the guards the UI can see (DESIGN.md §9.2, §9.3, §11.3);
 * the service still decides and answers 409 with its reason.
 */
export const documentActionAvailability = ({
  document,
  voidingDocument,
}: {
  document: DocumentView;
  /** The RA, RC or RR that voids it, once loaded. */
  voidingDocument: DocumentView | undefined;
}): IDocumentActionAvailability => ({
  retry: isDocumentRetryable(document)
    ? undefined
    : "Solo se reenvía un comprobante por enviar, en proceso, por confirmar o con error.",
  void: isDocumentVoidable(document)
    ? undefined
    : document.delivered_at
      ? "El comprador ya recibió el comprobante: emite una nota de crédito."
      : "Solo se anula un comprobante aceptado que no sea una guía ni un resumen.",
  cancelVoid:
    document.status === "voiding" && voidingDocument?.status === "exception"
      ? undefined
      : "Solo se cancela una anulación cuyo resumen quedó con error.",
  cdr: document.cdr_received_at ? undefined : "SUNAT aún no envió el CDR.",
  files:
    document.status === "draft" ? "El borrador aún no se firmó." : undefined,
});
