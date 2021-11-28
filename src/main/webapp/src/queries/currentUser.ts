import { UseQueryResult, useQuery } from "react-query";

import {
  CurrentUserClusterResource,
  CurrentUserClusterResourceKind,
} from "api-client";
import { ApiClientError } from "api-client/types";

import { User } from "api/models";
import { useClientInstance } from "shared/hooks";

const whoAmIResource = new CurrentUserClusterResource(
  CurrentUserClusterResourceKind.WhoAmI
);

export const useCurrentUserQuery = (): UseQueryResult<User, ApiClientError> => {
  const client = useClientInstance();
  const result = useQuery<User, ApiClientError>("currentUser", async () => {
    return (await client.get(whoAmIResource, "")).data;
  });
  return result;
};
