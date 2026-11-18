import { createFileRoute, notFound } from "@tanstack/react-router";

import { DetailRouteError } from "@app/components/DetailRouteError";
import { DetailRoutePending } from "@app/components/DetailRoutePending";
import { WebhookDeliveriesRoute } from "@app/pages/xhandler/webhooks/webhook-deliveries/WebhookDeliveriesRoute";
import { WEBHOOK_DELIVERIES_STATE } from "@app/pages/xhandler/webhooks/webhook-deliveries/webhookDeliveriesState";
import { webhooksListQueryOptions } from "@app/queries/xhandler/webhooks";
import { listSearch } from "@app/utils/listState";

const { validateSearch, search } = listSearch(WEBHOOK_DELIVERIES_STATE);

export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/webhooks/$webhookId",
)({
  validateSearch,
  search,
  // The API has no read of one webhook: the (unpaged) list primes the page.
  loader: async ({
    context: { queryClient },
    params: { tenantId, webhookId },
  }) => {
    const webhooks = await queryClient.query({
      ...webhooksListQueryOptions({ tenantId }),
      staleTime: "static",
    });
    const webhook = webhooks.find((item) => item.id === webhookId);
    if (!webhook) throw notFound();
    return { webhook };
  },
  pendingComponent: () => <DetailRoutePending label="Cargando webhook" />,
  errorComponent: DetailRouteError,
  component: WebhookDeliveriesRoute,
});
