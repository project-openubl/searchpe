import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  type BillingDetails,
  type BillingView,
  getBilling,
  updateBillingDetails,
} from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";

import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const BillingQueryKey = "billing";

/** Share of a quota used, 0–100, or undefined for an unlimited quota. */
export const quotaPercent = ({
  used,
  limit,
}: {
  used: number | undefined;
  limit: number | null | undefined;
}): number | undefined => {
  if (limit === null || limit === undefined || limit <= 0) return undefined;
  return Math.min(100, Math.round(((used ?? 0) / limit) * 100));
};

export const billingKeys = {
  all: () => [BillingQueryKey] as const,
  detail: (tenantId: string) => [...billingKeys.all(), tenantId] as const,
};

export const billingQueryOptions = ({ tenantId }: { tenantId: string }) =>
  queryOptions<BillingView, XhandlerAxiosError>({
    queryKey: billingKeys.detail(tenantId),
    queryFn: async () =>
      (
        await getBilling({
          path: { tenantId },
          throwOnError: true,
        })
      ).data,
  });

/** `GET …/billing`: plan, usage and billing details (DESIGN.md §14). */
export const useBillingQuery = () => {
  const tenantId = useSelectedTenantId();
  return useQuery({
    ...billingQueryOptions({ tenantId }),
    enabled: !!tenantId,
  });
};

/** `PUT …/billing` (admin): replaces the billing details, answers the whole view. */
export const useBillingDetailsMutation = (
  options?: IMutationCallbacks<BillingView, BillingDetails, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<BillingView, XhandlerAxiosError, BillingDetails>({
    mutationFn: async (body) =>
      (
        await updateBillingDetails({
          path: { tenantId },
          body,
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      queryClient.setQueryData(billingKeys.detail(tenantId), data);
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};
