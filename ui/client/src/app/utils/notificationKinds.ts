import type { NotificationKind } from "@app/queries/xhandler/notifications";

/** Spanish names of the notification kinds (DESIGN.md §26.6). */
export const NOTIFICATION_KIND_LABELS: Record<NotificationKind, string> = {
  "document.exception": "Comprobante con excepción",
  "document.at_risk": "Comprobante demorado o en riesgo de plazo",
  "document.rejected_after_delivery": "Rechazado después de entregado",
  "certificate.expiring": "Certificado por vencer",
  "sunat.outage": "Caída de SUNAT",
  quota: "Uso del plan",
  "pse.authorization_revoked": "Autorización PSE revocada",
  "conformity.objected": "Conformidad observada",
  "sire.deadline": "Plazos SIRE",
};

/** The kind's Spanish name, or the kind as returned for one the UI doesn't know. */
export const notificationKindLabel = (kind: string | undefined): string =>
  kind
    ? ((NOTIFICATION_KIND_LABELS as Record<string, string>)[kind] ?? kind)
    : "-";
