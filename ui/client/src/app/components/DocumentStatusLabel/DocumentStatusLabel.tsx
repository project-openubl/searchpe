import type React from "react";

import { Label, LabelGroup } from "@patternfly/react-core";

import { isDocumentStatus } from "@app/queries/xhandler/documents";

import {
  DOCUMENT_STATUS_PRESENTATION,
  HOLD_REASON_LABELS,
} from "./documentStatusPresentation";

interface IDocumentStatusLabelProps {
  status: string | undefined;
  holdReason?: string;
}

/** A document's status as the server reports it, plus its hold; unknown values show as returned. */
export const DocumentStatusLabel: React.FC<IDocumentStatusLabelProps> = ({
  status,
  holdReason,
}) => {
  if (!status) return "-";
  const statusLabel = isDocumentStatus(status) ? (
    <Label
      color={DOCUMENT_STATUS_PRESENTATION[status].color}
      icon={DOCUMENT_STATUS_PRESENTATION[status].icon}
      isCompact
    >
      {DOCUMENT_STATUS_PRESENTATION[status].label}
    </Label>
  ) : (
    <Label isCompact>{status}</Label>
  );
  if (!holdReason) return statusLabel;
  return (
    <LabelGroup>
      {statusLabel}
      <Label color="orange" variant="outline" isCompact>
        {HOLD_REASON_LABELS[holdReason] ?? holdReason}
      </Label>
    </LabelGroup>
  );
};
