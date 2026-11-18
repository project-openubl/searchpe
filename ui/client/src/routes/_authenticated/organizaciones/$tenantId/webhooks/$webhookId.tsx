import { createFileRoute } from "@tanstack/react-router";

import { DetailRouteError } from "@app/components/DetailRouteError";
import { DetailRoutePending } from "@app/components/DetailRoutePending";
import { WebhookDeliveriesRoute } from "@app/pages/xhandler/webhooks/webhook-deliveries/WebhookDeliveriesRoute";
import { WEBHOOK_DELIVERIES_STATE } from "@app/pages/xhandler/webhooks/webhook-deliveries/webhookDeliveriesState";
import { webhookDetailQueryOptions } from "@app/queries/xhandler/webhooks";
import { listSearch } from "@app/utils/listState";

const { validateSearch, search } = listSearch(WEBHOOK_DELIVERIES_STATE);

export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/webhooks/$webhookId",
)({
  validateSearch,
  search,
  // An unknown or deleted webhook is a 404, shown by DetailRouteError.
  loader: async ({
    context: { queryClient },
    params: { tenantId, webhookId },
  }) => {
    await queryClient.query({
      ...webhookDetailQueryOptions({ tenantId, webhookId }),
      staleTime: "static",
    });
    return { webhookId };
  },
  pendingComponent: () => <DetailRoutePending label="Cargando webhook" />,
  errorComponent: DetailRouteError,
  component: WebhookDeliveriesRoute,
});
