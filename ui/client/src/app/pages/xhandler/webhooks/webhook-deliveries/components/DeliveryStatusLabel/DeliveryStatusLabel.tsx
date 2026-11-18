import type React from "react";

import { Label } from "@patternfly/react-core";

import { isDeliveryStatus } from "@app/queries/xhandler/webhooks";

import { DELIVERY_STATUS_PRESENTATION } from "./deliveryStatusPresentation";

interface IDeliveryStatusLabelProps {
  status: string | undefined;
}

/** A webhook delivery's status in Spanish; an unknown one shows as returned. */
export const DeliveryStatusLabel: React.FC<IDeliveryStatusLabelProps> = ({
  status,
}) => {
  if (!status) return "-";
  if (!isDeliveryStatus(status)) return <Label isCompact>{status}</Label>;
  const { label, color } = DELIVERY_STATUS_PRESENTATION[status];
  return (
    <Label color={color} isCompact>
      {label}
    </Label>
  );
};
