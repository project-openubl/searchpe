import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  type CreateSeries,
  createSeries,
  listSeries,
  type SeriesView,
  type UpdateSeries,
  updateSeries,
} from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";

import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const SeriesQueryKey = "series";

export const seriesKeys = {
  all: () => [SeriesQueryKey] as const,
  list: (tenantId: string, companyId: string) =>
    [...seriesKeys.all(), tenantId, companyId, "list"] as const,
};

export const seriesListQueryOptions = ({
  tenantId,
  companyId,
}: {
  tenantId: string;
  companyId: string;
}) =>
  queryOptions<SeriesView[], XhandlerAxiosError>({
    queryKey: seriesKeys.list(tenantId, companyId),
    queryFn: async () =>
      (
        await listSeries({
          path: { tenantId, companyId },
          throwOnError: true,
        })
      ).data,
  });

/** `GET …/series`: unpaged by the API (a company has a handful of series), so it is read whole. */
export const useSeriesListQuery = ({ companyId }: { companyId: string }) => {
  const tenantId = useSelectedTenantId();
  return useQuery({
    ...seriesListQueryOptions({ tenantId, companyId }),
    enabled: !!tenantId && !!companyId,
  });
};

export interface ISeriesCreateVariables {
  body: CreateSeries;
  /** A fresh UUID per user intent, reused when the same submission is retried. */
  idempotencyKey: string;
}

/** `POST …/series` (admin): a series in the company's current environment. */
export const useSeriesCreateMutation = (
  companyId: string,
  options?: IMutationCallbacks<
    SeriesView,
    ISeriesCreateVariables,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<SeriesView, XhandlerAxiosError, ISeriesCreateVariables>({
    mutationFn: async ({ body, idempotencyKey }) =>
      (
        await createSeries({
          path: { tenantId, companyId },
          body,
          headers: { "Idempotency-Key": idempotencyKey },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      await queryClient.invalidateQueries({
        queryKey: seriesKeys.list(tenantId, companyId),
      });
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};

export interface ISeriesUpdateVariables {
  seriesId: string;
  body: UpdateSeries;
}

/**
 * `PUT …/series/{id}` (admin): `next_number` only moves forward (`409 series_number_used`
 * below the numbers already used) and `active: false` makes emits refuse the series.
 */
export const useSeriesUpdateMutation = (
  companyId: string,
  options?: IMutationCallbacks<
    SeriesView,
    ISeriesUpdateVariables,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<SeriesView, XhandlerAxiosError, ISeriesUpdateVariables>({
    mutationFn: async ({ seriesId, body }) =>
      (
        await updateSeries({
          path: { tenantId, companyId, seriesId },
          body,
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      await queryClient.invalidateQueries({
        queryKey: seriesKeys.list(tenantId, companyId),
      });
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};
