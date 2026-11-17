import type React from "react";

import { Label } from "@patternfly/react-core";

interface ISunatEnvLabelProps {
  sunatEnv: string | undefined;
}

const PRESENTATION: Record<
  string,
  { label: string; color: "green" | "blue" | "grey" }
> = {
  production: { label: "Producción", color: "green" },
  beta: { label: "Beta", color: "blue" },
  fake: { label: "Simulado", color: "grey" },
};

/** A company's SUNAT environment; production stands out from the test ones. */
export const SunatEnvLabel: React.FC<ISunatEnvLabelProps> = ({ sunatEnv }) => {
  if (!sunatEnv) return "-";
  const presentation = PRESENTATION[sunatEnv] ?? {
    label: sunatEnv,
    color: "grey" as const,
  };
  return (
    <Label color={presentation.color} isCompact>
      {presentation.label}
    </Label>
  );
};
