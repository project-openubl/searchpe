import { UseQueryResult, useQuery } from "react-query";

import { CoreClusterResource, CoreClusterResourceKind } from "api-client";
import { ApiClientError } from "api-client/types";

import { User } from "api/models";
import { useSearchpeClient } from "./fetchHelpers";

const whoAmIResource = new CoreClusterResource(CoreClusterResourceKind.WhoAmI);

export const useWhoAmIQuery = (): UseQueryResult<User, ApiClientError> => {
  const client = useSearchpeClient();
  const result = useQuery<User, ApiClientError>({
    queryKey: "whoami",
    queryFn: async () => {
      return (await client.get<User>(whoAmIResource, "")).data;
    },
    refetchInterval: 60_000,
    retry: process.env.NODE_ENV === "development" ? false : undefined,
  });
  return result;
};
