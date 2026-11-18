import { createFileRoute } from "@tanstack/react-router";

import { WebhookList } from "@app/pages/xhandler/webhooks/webhook-list/WebhookList";

export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/webhooks/",
)({
  component: WebhookList,
});
