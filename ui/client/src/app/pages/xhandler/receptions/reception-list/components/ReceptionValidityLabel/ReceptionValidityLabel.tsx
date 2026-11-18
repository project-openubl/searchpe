import type React from "react";

import { Label } from "@patternfly/react-core";

import { isReceptionValidityState } from "@app/queries/xhandler/receptions";

import { RECEPTION_VALIDITY_PRESENTATION } from "./receptionValidityPresentation";

interface IReceptionValidityLabelProps {
  state: string | undefined;
}

/** A reception's validity state; an unknown value shows as returned. */
export const ReceptionValidityLabel: React.FC<IReceptionValidityLabelProps> = ({
  state,
}) => {
  if (!state) return "-";
  if (!isReceptionValidityState(state)) return <Label isCompact>{state}</Label>;
  const { label, color } = RECEPTION_VALIDITY_PRESENTATION[state];
  return (
    <Label color={color} isCompact>
      {label}
    </Label>
  );
};
