import type React from "react";

import { type LabelProps, Spinner } from "@patternfly/react-core";
import BanIcon from "@patternfly/react-icons/dist/esm/icons/ban-icon";
import CheckCircleIcon from "@patternfly/react-icons/dist/esm/icons/check-circle-icon";
import ExclamationCircleIcon from "@patternfly/react-icons/dist/esm/icons/exclamation-circle-icon";
import ExclamationTriangleIcon from "@patternfly/react-icons/dist/esm/icons/exclamation-triangle-icon";
import OutlinedQuestionCircleIcon from "@patternfly/react-icons/dist/esm/icons/outlined-question-circle-icon";
import PencilAltIcon from "@patternfly/react-icons/dist/esm/icons/pencil-alt-icon";
import PendingIcon from "@patternfly/react-icons/dist/esm/icons/pending-icon";

import type { DocumentStatus } from "@app/queries/xhandler/documents";

const SPINNER = <Spinner size="sm" aria-hidden="true" />;

/** Every document status has a Spanish label (DESIGN.md §9.1); a `Record`, so a new status fails to compile. */
export const DOCUMENT_STATUS_PRESENTATION: Record<
  DocumentStatus,
  { label: string; color: LabelProps["color"]; icon: React.ReactNode }
> = {
  draft: { label: "Borrador", color: "grey", icon: <PencilAltIcon /> },
  pending_send: { label: "Por enviar", color: "blue", icon: <PendingIcon /> },
  in_progress: { label: "En proceso", color: "blue", icon: SPINNER },
  accepted: { label: "Aceptado", color: "green", icon: <CheckCircleIcon /> },
  accepted_with_observations: {
    label: "Aceptado con observaciones",
    color: "yellow",
    icon: <ExclamationTriangleIcon />,
  },
  rejected: {
    label: "Rechazado",
    color: "red",
    icon: <ExclamationCircleIcon />,
  },
  exception: {
    label: "Con error",
    color: "red",
    icon: <ExclamationCircleIcon />,
  },
  unknown: {
    label: "Por confirmar",
    color: "orange",
    icon: <OutlinedQuestionCircleIcon />,
  },
  voiding: { label: "En anulación", color: "orange", icon: SPINNER },
  voided: { label: "Anulado", color: "grey", icon: <BanIcon /> },
};

/** `hold_reason` values (DESIGN.md §9.1): documents that need a person. */
export const HOLD_REASON_LABELS: Record<string, string> = {
  needs_attention: "Requiere atención",
  operator: "Retenido",
};
