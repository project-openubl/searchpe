import type React from "react";

import { Label } from "@patternfly/react-core";

import { isConformityState } from "@app/queries/xhandler/conformity";

import { CONFORMITY_STATE_PRESENTATION } from "./conformityStatePresentation";

interface IConformityStateLabelProps {
  state: string | undefined;
}

/** A factura negociable's conformity in Spanish; a state the UI doesn't know shows as returned. */
export const ConformityStateLabel: React.FC<IConformityStateLabelProps> = ({
  state,
}) => {
  if (!state || !isConformityState(state)) {
    return <Label color="grey">{state ?? "-"}</Label>;
  }
  const { label, color } = CONFORMITY_STATE_PRESENTATION[state];
  return <Label color={color}>{label}</Label>;
};
