import type { LabelProps } from "@patternfly/react-core";

import type { ConformityState } from "@app/queries/xhandler/conformity";

/** Every conformity state has a Spanish label (ADR 0005); a `Record`, so a new state fails to compile. */
export const CONFORMITY_STATE_PRESENTATION: Record<
  ConformityState,
  { label: string; color: LabelProps["color"] }
> = {
  pending: { label: "En plazo", color: "blue" },
  agreed: { label: "Conforme", color: "green" },
  objected: { label: "Disconforme", color: "red" },
  silent_agreement: { label: "Conformidad presunta", color: "teal" },
};
