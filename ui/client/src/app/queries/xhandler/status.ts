import { queryOptions, useQuery } from "@tanstack/react-query";

import { getStatus, type Status } from "@app/client/xhandler";

import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const XhandlerStatusQueryKey = "xhandler-status";

/** `GET /api/v1/status`: public, gates the xhandler navigation (ADR 0001). */
export const xhandlerStatusQueryOptions = queryOptions<
  Status,
  XhandlerAxiosError
>({
  queryKey: [XhandlerStatusQueryKey],
  queryFn: async () => (await getStatus({ throwOnError: true })).data,
  staleTime: 60 * 1000,
  // A failed status is shown at once; the next poll tries again.
  retry: false,
  refetchInterval: 60 * 1000,
});

export const useXhandlerStatusQuery = () =>
  useQuery(xhandlerStatusQueryOptions);
