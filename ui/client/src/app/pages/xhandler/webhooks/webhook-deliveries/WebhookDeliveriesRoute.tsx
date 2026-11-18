import type React from "react";

import { getRouteApi } from "@tanstack/react-router";

import { WebhookDeliveries } from "./WebhookDeliveries";

const routeApi = getRouteApi(
  "/_authenticated/organizaciones/$tenantId/webhooks/$webhookId",
);

export const WebhookDeliveriesRoute: React.FC = () => {
  const { webhookId } = routeApi.useLoaderData();
  return <WebhookDeliveries webhookId={webhookId} />;
};
