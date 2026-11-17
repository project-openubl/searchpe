import type { UseQueryOptions } from "@tanstack/react-query";
import { queryOptions, useQuery } from "@tanstack/react-query";

import { SEARCHPE_HEALTH_PATH } from "@openubl-ui/common";

import { searchpeAxios } from "@app/axios-config/apiInit";

import type { SearchpeAxiosError } from "../utils/openapiErrorTypes";

/** SmallRye Health's answer; searchpe documents no status endpoint in its spec. */
export interface ISearchpeHealth {
  status: "UP" | "DOWN";
  checks?: { name: string; status: "UP" | "DOWN" }[];
}

export const SearchpeHealthQueryKey = "searchpe-health";

/**
 * `GET /q/health/ready` (public; the proxy maps exactly `/api/searchpe/q/health/ready`), gates the searchpe
 * navigation (ADR 0001). A `DOWN` answer is a 503, so it fails the query too.
 */
export const searchpeHealthQueryOptions = queryOptions<
  ISearchpeHealth,
  SearchpeAxiosError
>({
  queryKey: [SearchpeHealthQueryKey],
  queryFn: async () =>
    (await searchpeAxios.get<ISearchpeHealth>(SEARCHPE_HEALTH_PATH)).data,
  staleTime: 60 * 1000,
  retry: false,
  refetchInterval: 60 * 1000,
});

/** `options.enabled` is false when searchpe isn't deployed with this UI. */
export const useSearchpeHealthQuery = ({
  options,
}: {
  options?: Omit<
    UseQueryOptions<ISearchpeHealth, SearchpeAxiosError>,
    "queryKey" | "queryFn"
  >;
} = {}) => useQuery({ ...options, ...searchpeHealthQueryOptions });
