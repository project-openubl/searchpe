import type React from "react";

import { Label } from "@patternfly/react-core";

import { isDraftStatus } from "@app/queries/xhandler/drafts";

import { DRAFT_STATUS_PRESENTATION } from "./draftStatusPresentation";

interface IDraftStatusLabelProps {
  status: string | undefined;
}

/** A draft's status; an unknown one shows as the API sends it. */
export const DraftStatusLabel: React.FC<IDraftStatusLabelProps> = ({
  status,
}) => {
  if (!status) return "-";
  if (!isDraftStatus(status)) return <Label isCompact>{status}</Label>;
  const { label, color } = DRAFT_STATUS_PRESENTATION[status];
  return (
    <Label color={color} isCompact>
      {label}
    </Label>
  );
};
