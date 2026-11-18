import type { WEBHOOK_EVENT_TYPES } from "@app/queries/xhandler/webhooks";

/** Spanish names of the event types a subscription can pick; `*` means every event. */
export const WEBHOOK_EVENT_LABELS: Record<
  (typeof WEBHOOK_EVENT_TYPES)[number] | "*",
  string
> = {
  "*": "Todos los eventos",
  "document.accepted": "Comprobante aceptado",
  "document.rejected": "Comprobante rechazado",
  "document.rejected_after_delivery": "Rechazado después de entregado",
  "document.exception": "Comprobante con excepción",
  "document.voided": "Comprobante anulado",
  "document.void_rejected": "Anulación rechazada",
  "document.delayed": "Comprobante demorado",
  "conformity.objected": "Conformidad observada",
};

/** The event's Spanish name, or the type as returned for one the UI doesn't know. */
export const webhookEventLabel = (type: string): string =>
  (WEBHOOK_EVENT_LABELS as Record<string, string>)[type] ?? type;
