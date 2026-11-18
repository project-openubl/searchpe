import type { DeliveryStatus } from "@app/queries/xhandler/webhooks";

export const DELIVERY_STATUS_PRESENTATION: Record<
  DeliveryStatus,
  { label: string; color: "blue" | "green" | "red" }
> = {
  pending: { label: "Pendiente", color: "blue" },
  delivered: { label: "Entregado", color: "green" },
  failed: { label: "Fallido", color: "red" },
};
