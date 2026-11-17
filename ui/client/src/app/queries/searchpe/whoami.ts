import { queryOptions, useQuery } from "@tanstack/react-query";

import { type BasicUserDto, getWhoami } from "@app/client/searchpe";
import { ENV } from "@app/env";

import type { SearchpeAxiosError } from "../utils/openapiErrorTypes";

export const WhoamiQueryKey = "searchpe-whoami";

/** `GET /api/whoami`: the user as searchpe sees them, with the permissions (realm roles) it grants. */
export const whoamiQueryOptions = queryOptions<
  BasicUserDto,
  SearchpeAxiosError
>({
  queryKey: [WhoamiQueryKey],
  queryFn: async () => (await getWhoami({ throwOnError: true })).data,
  staleTime: 5 * 60 * 1000,
});

/** Never runs without searchpe deployed (`SEARCHPE_ENABLED`); `enabled` gates it further. */
export const useWhoamiQuery = ({
  enabled = true,
}: { enabled?: boolean } = {}) =>
  useQuery({
    ...whoamiQueryOptions,
    enabled: ENV.SEARCHPE_ENABLED === "true" && enabled,
  });
