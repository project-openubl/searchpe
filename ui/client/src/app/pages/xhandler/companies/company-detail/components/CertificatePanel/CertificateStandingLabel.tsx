import type React from "react";

import { Label } from "@patternfly/react-core";

import type { CertificateStanding } from "@app/queries/xhandler/certificates";

const PRESENTATION: Record<
  CertificateStanding,
  { color: "green" | "orange" | "red"; label: (days: number) => string }
> = {
  valid: { color: "green", label: () => "Vigente" },
  expiring: {
    color: "orange",
    label: (days) =>
      days === 0
        ? "Vence hoy"
        : `Vence en ${days} ${days === 1 ? "día" : "días"}`,
  },
  expired: { color: "red", label: () => "Vencido" },
};

interface ICertificateStandingLabelProps {
  standing: CertificateStanding | undefined;
  daysLeft: number | undefined;
}

/** Whether the certificate can still sign; notices start 30 days before it expires. */
export const CertificateStandingLabel: React.FC<
  ICertificateStandingLabelProps
> = ({ standing, daysLeft }) => {
  if (!standing || daysLeft === undefined) return "-";
  const { color, label } = PRESENTATION[standing];
  return (
    <Label color={color} isCompact>
      {label(daysLeft)}
    </Label>
  );
};
