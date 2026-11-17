import type React from "react";

import { Label } from "@patternfly/react-core";

interface IEstadoContribuyenteLabelProps {
  estado: string | undefined;
}

/** SUNAT's `estado` as returned; only ACTIVO reads as good news. */
export const EstadoContribuyenteLabel: React.FC<
  IEstadoContribuyenteLabelProps
> = ({ estado }) =>
  estado ? (
    <Label color={estado === "ACTIVO" ? "green" : "orange"} isCompact>
      {estado}
    </Label>
  ) : (
    "-"
  );
