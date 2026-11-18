import type { UseQueryOptions } from "@tanstack/react-query";
import {
  keepPreviousData,
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  type CreateWebhook,
  createWebhook,
  deleteWebhook,
  type DeliveryView,
  listWebhookDeliveries,
  type ListWebhookDeliveriesData,
  listWebhooks,
  type PageDeliveryView,
  redeliverWebhook,
  testWebhook,
  type WebhookView,
} from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";

import type { ListParams } from "../utils/listParams";
import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const WebhooksQueryKey = "webhooks";

/**
 * Event types a subscription can ask for (DESIGN.md §12, `WebhookEvents`); a pattern may also be
 * `*` or end in `.*`.
 */
export const WEBHOOK_EVENT_TYPES = [
  "document.accepted",
  "document.rejected",
  "document.rejected_after_delivery",
  "document.exception",
  "document.voided",
  "document.void_rejected",
  "document.delayed",
  "conformity.objected",
] as const;

/** Delivery statuses (`DeliveryView.status`); the spec types it as a string. */
export const DELIVERY_STATUSES = ["pending", "delivered", "failed"] as const;

export type DeliveryStatus = (typeof DELIVERY_STATUSES)[number];

export const isDeliveryStatus = (value: string): value is DeliveryStatus =>
  (DELIVERY_STATUSES as readonly string[]).includes(value);

export type IWebhookDeliveryListParams = ListParams<ListWebhookDeliveriesData>;

export const webhookKeys = {
  all: () => [WebhooksQueryKey] as const,
  list: (tenantId: string) => [...webhookKeys.all(), tenantId, "list"] as const,
  deliveries: (tenantId: string, webhookId: string) =>
    [...webhookKeys.all(), tenantId, "deliveries", webhookId] as const,

  deliveriesQuery: (
    tenantId: string,
    webhookId: string,
    params: IWebhookDeliveryListParams,
  ) => [...webhookKeys.deliveries(tenantId, webhookId), params] as const,
};

export const webhooksListQueryOptions = ({ tenantId }: { tenantId: string }) =>
  queryOptions<WebhookView[], XhandlerAxiosError>({
    queryKey: webhookKeys.list(tenantId),
    queryFn: async () =>
      (
        await listWebhooks({
          path: { tenantId },
          throwOnError: true,
        })
      ).data,
  });

/** `GET …/webhooks`: unpaged by the API (a tenant has a few subscriptions), so it is read whole. */
export const useWebhooksListQuery = () => {
  const tenantId = useSelectedTenantId();
  return useQuery({
    ...webhooksListQueryOptions({ tenantId }),
    enabled: !!tenantId,
  });
};

type DeliveryListOptions = Omit<
  UseQueryOptions<PageDeliveryView, XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

export const webhookDeliveriesQueryOptions = ({
  tenantId,
  webhookId,
  params,
  options,
}: {
  tenantId: string;
  webhookId: string;
  params: IWebhookDeliveryListParams;
  options?: DeliveryListOptions;
}) =>
  queryOptions<PageDeliveryView, XhandlerAxiosError>({
    placeholderData: keepPreviousData,
    ...options,
    queryKey: webhookKeys.deliveriesQuery(tenantId, webhookId, params),
    queryFn: async () =>
      (
        await listWebhookDeliveries({
          path: { tenantId, webhookId },
          query: params,
          throwOnError: true,
        })
      ).data,
  });

export const useWebhookDeliveriesQuery = ({
  webhookId,
  params,
  options,
}: {
  webhookId: string;
  params: IWebhookDeliveryListParams;
  options?: DeliveryListOptions;
}) => {
  const tenantId = useSelectedTenantId();
  return useQuery(
    webhookDeliveriesQueryOptions({ tenantId, webhookId, params, options }),
  );
};

/** `POST …/webhooks` (admin): `201` with the secret, shown only in this answer. */
export const useWebhookCreateMutation = (
  options?: IMutationCallbacks<WebhookView, CreateWebhook, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<WebhookView, XhandlerAxiosError, CreateWebhook>({
    mutationFn: async (body) =>
      (
        await createWebhook({
          path: { tenantId },
          body,
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      await queryClient.invalidateQueries({
        queryKey: webhookKeys.list(tenantId),
      });
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};

/** `DELETE …/webhooks/{id}` (admin). */
export const useWebhookDeleteMutation = (
  options?: IMutationCallbacks<void, WebhookView, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<void, XhandlerAxiosError, WebhookView>({
    mutationFn: async (webhook) => {
      await deleteWebhook({
        path: { tenantId, webhookId: webhook.id ?? "" },
        throwOnError: true,
      });
    },
    onSuccess: async (data, webhook) => {
      await queryClient.invalidateQueries({
        queryKey: webhookKeys.list(tenantId),
      });
      await options?.onSuccess?.(data, webhook);
    },
    onError: options?.onError,
  });
};

/** `POST …/webhooks/{id}/test` (admin): queues a `webhook.test` delivery. */
export const useWebhookTestMutation = (
  options?: IMutationCallbacks<DeliveryView, WebhookView, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<DeliveryView, XhandlerAxiosError, WebhookView>({
    mutationFn: async (webhook) =>
      (
        await testWebhook({
          path: { tenantId, webhookId: webhook.id ?? "" },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, webhook) => {
      await queryClient.invalidateQueries({
        queryKey: webhookKeys.deliveries(tenantId, webhook.id ?? ""),
      });
      await options?.onSuccess?.(data, webhook);
    },
    onError: options?.onError,
  });
};

/** `POST …/deliveries/{id}/redeliver` (operator): sends the delivery again. */
export const useWebhookRedeliverMutation = (
  webhookId: string,
  options?: IMutationCallbacks<DeliveryView, DeliveryView, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<DeliveryView, XhandlerAxiosError, DeliveryView>({
    mutationFn: async (delivery) =>
      (
        await redeliverWebhook({
          path: { tenantId, webhookId, deliveryId: delivery.id ?? "" },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, delivery) => {
      await queryClient.invalidateQueries({
        queryKey: webhookKeys.deliveries(tenantId, webhookId),
      });
      await options?.onSuccess?.(data, delivery);
    },
    onError: options?.onError,
  });
};
