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

/** `POST …/series` (admin): a series in the company's current environment. */
export const useSeriesCreateMutation = (
  companyId: string,
  options?: IMutationCallbacks<SeriesView, CreateSeries, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<SeriesView, XhandlerAxiosError, CreateSeries>({
    mutationFn: async (body) =>
      (
        await createSeries({
          path: { tenantId, companyId },
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
