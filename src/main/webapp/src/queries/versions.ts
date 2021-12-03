import { useCallback } from "react";
import {
  UseQueryResult,
  useQuery,
  UseMutationResult,
  useMutation,
  useQueryClient,
} from "react-query";

import { CoreClusterResource, CoreClusterResourceKind } from "api-client";
import { Version } from "api/models";

import { ApiClientError } from "api-client/types";
import { useSearchpeClient } from "./fetchHelpers";

const versionResource = new CoreClusterResource(
  CoreClusterResourceKind.Version
);

export const useVersionsQuery = (): UseQueryResult<
  Version[],
  ApiClientError
> => {
  const sortVersionListByIdCallback = useCallback(
    (data: Version[]): Version[] => data.sort((a, b) => b.id - a.id),
    []
  );

  const client = useSearchpeClient();
  const result = useQuery<Version[], ApiClientError>({
    queryKey: "versions",
    queryFn: async () => {
      return (await client.list<Version[]>(versionResource)).data;
    },
    refetchInterval: (data) => {
      const flag = (data || []).every(
        (f) => f.status === "COMPLETED" || f.status === "ERROR"
      );
      if (!flag) {
        return 5_000;
      } else {
        return false;
      }
    },
    select: sortVersionListByIdCallback,
  });
  return result;
};

export const useCreateVersionMutation = (
  onSuccess?: (version: Version) => void
): UseMutationResult<Version, ApiClientError, void> => {
  const client = useSearchpeClient();
  const queryClient = useQueryClient();
  return useMutation<Version, ApiClientError, void>(
    async () => {
      return (await client.create<Version>(versionResource, {})).data;
    },
    {
      onSuccess: (response) => {
        queryClient.invalidateQueries("versions");
        onSuccess && onSuccess(response);
      },
    }
  );
};

export const useDeleteVersionMutation = (
  onSuccess?: () => void
): UseMutationResult<void, ApiClientError, Version, unknown> => {
  const client = useSearchpeClient();
  const queryClient = useQueryClient();
  return useMutation<void, ApiClientError, Version>(
    async (version: Version) => {
      await client.delete<void>(versionResource, `${version.id}`);
    },
    {
      onSuccess: () => {
        queryClient.invalidateQueries("versions");
        onSuccess && onSuccess();
      },
    }
  );
};
