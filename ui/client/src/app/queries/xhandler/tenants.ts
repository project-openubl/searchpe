import type { UseQueryOptions } from "@tanstack/react-query";
import {
  queryOptions,
  useQuery,
  useSuspenseQuery,
} from "@tanstack/react-query";

import {
  getV1Tenants,
  getV1TenantsByTenantId,
  type TenantSummary,
  type TenantView,
} from "@app/client/xhandler";

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
  queryFn: async () => (await getV1Tenants({ throwOnError: true })).data,
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
        await getV1TenantsByTenantId({
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
