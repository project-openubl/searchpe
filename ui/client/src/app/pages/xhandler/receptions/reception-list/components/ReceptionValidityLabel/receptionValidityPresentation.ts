import type { LabelProps } from "@patternfly/react-core";

import type { ReceptionValidityState } from "@app/queries/xhandler/receptions";

/** What SUNAT's validity API said about a supplier document, in Spanish. */
export const RECEPTION_VALIDITY_PRESENTATION: Record<
  ReceptionValidityState,
  { label: string; color: LabelProps["color"] }
> = {
  unchecked: { label: "Sin consultar", color: "grey" },
  valid: { label: "Válido", color: "green" },
  not_found: { label: "No existe en SUNAT", color: "red" },
  voided: { label: "Anulado", color: "orange" },
  rejected: { label: "Rechazado", color: "red" },
  unknown: { label: "Por confirmar", color: "orange" },
};
