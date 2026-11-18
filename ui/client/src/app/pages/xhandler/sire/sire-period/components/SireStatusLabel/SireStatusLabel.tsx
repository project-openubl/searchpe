import type React from "react";

import { Label, type LabelProps } from "@patternfly/react-core";

import { isSireStatus, type SireStatus } from "@app/queries/xhandler/sire";

interface ISireStatusLabelProps {
  status: string | undefined;
}

const PRESENTATION: Record<
  SireStatus,
  { label: string; color: LabelProps["color"] }
> = {
  requested: { label: "Propuesta solicitada", color: "blue" },
  proposal_ready: { label: "Propuesta lista para revisar", color: "teal" },
  submitting: { label: "Enviando reemplazo", color: "blue" },
  submitted: { label: "Reemplazo enviado", color: "blue" },
  accepted: { label: "Aceptado por SUNAT", color: "green" },
  error: { label: "Error", color: "red" },
  credentials: { label: "Faltan credenciales SIRE", color: "orange" },
  unknown: { label: "Resultado desconocido", color: "orange" },
};

/** A SIRE period's state in Spanish; a state the UI doesn't know shows as returned. */
export const SireStatusLabel: React.FC<ISireStatusLabelProps> = ({
  status,
}) => {
  if (!status || !isSireStatus(status)) {
    return <Label color="grey">{status ?? "-"}</Label>;
  }
  const { label, color } = PRESENTATION[status];
  return <Label color={color}>{label}</Label>;
};
