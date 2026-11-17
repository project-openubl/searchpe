import type React from "react";

import { type LabelProps, Spinner } from "@patternfly/react-core";
import BanIcon from "@patternfly/react-icons/dist/esm/icons/ban-icon";
import CheckCircleIcon from "@patternfly/react-icons/dist/esm/icons/check-circle-icon";
import ExclamationCircleIcon from "@patternfly/react-icons/dist/esm/icons/exclamation-circle-icon";
import PendingIcon from "@patternfly/react-icons/dist/esm/icons/pending-icon";

import type { JobState } from "@app/queries/xhandler/jobs";

const SPINNER = <Spinner size="sm" aria-hidden="true" />;

/** Every job state has a Spanish label (ADR 0005); a `Record`, so a new state fails to compile. */
export const JOB_STATE_PRESENTATION: Record<
  JobState,
  { label: string; color: LabelProps["color"]; icon: React.ReactNode }
> = {
  waiting: { label: "En espera", color: "grey", icon: <PendingIcon /> },
  running: { label: "En ejecución", color: "blue", icon: SPINNER },
  completed: {
    label: "Completado",
    color: "green",
    icon: <CheckCircleIcon />,
  },
  failed: { label: "Fallido", color: "red", icon: <ExclamationCircleIcon /> },
  canceling: { label: "Cancelando", color: "orange", icon: SPINNER },
  canceled: { label: "Cancelado", color: "grey", icon: <BanIcon /> },
};
