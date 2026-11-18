import type { UseQueryOptions } from "@tanstack/react-query";
import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
  useSuspenseQuery,
} from "@tanstack/react-query";

import {
  getTenant,
  listTenants,
  type TenantSummary,
  type TenantView,
  type UpdateTenant,
  updateTenant,
} from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";

import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const TenantsQueryKey = "tenants";

/** Query-key factory mirroring the REST paths; a prefix invalidates every variant. */
export const tenantKeys = {
  all: () => [TenantsQueryKey] as const,
  mine: () => [...tenantKeys.all(), "mine"] as const,
  detail: (tenantId: string) =>
    [...tenantKeys.all(), "detail", tenantId] as const,
};

/**
 * `GET /api/v1/tenants`: the tenants the user is a member of, with their role. Unpaged by the
 * API (one row per membership), so it is read whole.
 */
export const myTenantsQueryOptions = queryOptions<
  TenantSummary[],
  XhandlerAxiosError
>({
  queryKey: tenantKeys.mine(),
  queryFn: async () => (await listTenants({ throwOnError: true })).data,
  staleTime: 60 * 1000,
});

/** `options.enabled` lets the shell skip it while xhandler-service is down. */
export const useMyTenantsQuery = ({
  options,
}: {
  options?: Omit<
    UseQueryOptions<TenantSummary[], XhandlerAxiosError>,
    "queryKey" | "queryFn"
  >;
} = {}) => useQuery({ ...options, ...myTenantsQueryOptions });

export const tenantDetailQueryOptions = ({ tenantId }: { tenantId: string }) =>
  queryOptions<TenantView, XhandlerAxiosError>({
    queryKey: tenantKeys.detail(tenantId),
    queryFn: async () =>
      (
        await getTenant({
          path: { tenantId },
          throwOnError: true,
        })
      ).data,
  });

export const useTenantDetailQuery = ({ tenantId }: { tenantId: string }) =>
  useQuery({ ...tenantDetailQueryOptions({ tenantId }), enabled: !!tenantId });

export const useSuspenseTenantDetailQuery = ({
  tenantId,
}: {
  tenantId: string;
}) => useSuspenseQuery(tenantDetailQueryOptions({ tenantId }));

/** `PUT /tenants/{tenantId}` (admin): renames the organization, answers the tenant. */
export const useTenantUpdateMutation = (
  options?: IMutationCallbacks<TenantView, UpdateTenant, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<TenantView, XhandlerAxiosError, UpdateTenant>({
    mutationFn: async (body) =>
      (
        await updateTenant({
          path: { tenantId },
          body,
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      queryClient.setQueryData(tenantKeys.detail(tenantId), data);
      // The tenant switcher lists names
      await queryClient.invalidateQueries({ queryKey: tenantKeys.mine() });
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};
