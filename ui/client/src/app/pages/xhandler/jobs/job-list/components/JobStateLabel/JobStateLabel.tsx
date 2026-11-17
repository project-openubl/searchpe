import type React from "react";

import { Label } from "@patternfly/react-core";

import { isJobState } from "@app/queries/xhandler/jobs";

import { JOB_STATE_PRESENTATION } from "./jobStatePresentation";

interface IJobStateLabelProps {
  state: string | undefined;
}

/** A job's state as the server reports it (ADR 0007); an unknown state shows as returned. */
export const JobStateLabel: React.FC<IJobStateLabelProps> = ({ state }) => {
  if (!state) return "-";
  if (!isJobState(state)) {
    return <Label isCompact>{state}</Label>;
  }
  const { label, color, icon } = JOB_STATE_PRESENTATION[state];
  return (
    <Label color={color} icon={icon} isCompact>
      {label}
    </Label>
  );
};
