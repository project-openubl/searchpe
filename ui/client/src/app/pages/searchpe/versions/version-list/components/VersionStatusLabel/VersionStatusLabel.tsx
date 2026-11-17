import type React from "react";

import { Label, type LabelProps, Spinner } from "@patternfly/react-core";
import BanIcon from "@patternfly/react-icons/dist/esm/icons/ban-icon";
import CheckCircleIcon from "@patternfly/react-icons/dist/esm/icons/check-circle-icon";
import DownloadIcon from "@patternfly/react-icons/dist/esm/icons/download-icon";
import ExclamationCircleIcon from "@patternfly/react-icons/dist/esm/icons/exclamation-circle-icon";
import PendingIcon from "@patternfly/react-icons/dist/esm/icons/pending-icon";
import SyncAltIcon from "@patternfly/react-icons/dist/esm/icons/sync-alt-icon";

import type { Status } from "@app/client/searchpe";

interface IStatusPresentation {
  label: string;
  color: LabelProps["color"];
  icon: React.ReactNode;
}

const SPINNER = <Spinner size="sm" aria-hidden="true" />;

/** Every status has a Spanish label; a `Record`, so a new status fails to compile. */
const VERSION_STATUS_PRESENTATION: Record<Status, IStatusPresentation> = {
  SCHEDULED: { label: "Programada", color: "grey", icon: <PendingIcon /> },
  DOWNLOADING: {
    label: "Descargando",
    color: "blue",
    icon: <DownloadIcon />,
  },
  UNZIPPING: { label: "Descomprimiendo", color: "blue", icon: SPINNER },
  IMPORTING: { label: "Importando", color: "blue", icon: <SyncAltIcon /> },
  INDEXING: { label: "Indexando", color: "blue", icon: SPINNER },
  COMPLETED: {
    label: "Completada",
    color: "green",
    icon: <CheckCircleIcon />,
  },
  ERROR: { label: "Fallida", color: "red", icon: <ExclamationCircleIcon /> },
  CANCELLED: { label: "Cancelada", color: "grey", icon: <BanIcon /> },
  CANCELLING: { label: "Cancelando", color: "orange", icon: SPINNER },
  DELETING: { label: "Eliminando", color: "orange", icon: SPINNER },
};

interface IVersionStatusLabelProps {
  status: Status | undefined;
}

/** A padrón version's import status, as searchpe reports it. */
export const VersionStatusLabel: React.FC<IVersionStatusLabelProps> = ({
  status,
}) => {
  if (!status) return "-";
  const { label, color, icon } = VERSION_STATUS_PRESENTATION[status];
  return (
    <Label color={color} icon={icon} isCompact>
      {label}
    </Label>
  );
};
