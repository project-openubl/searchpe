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

import { useClientInstance } from "shared/hooks";
import { ApiClientError } from "api-client/types";

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

  const client = useClientInstance();
  const result = useQuery<Version[], ApiClientError>(
    "versions",
    async () => {
      return (await client.list(versionResource)).data;
    },
    {
      refetchInterval: (data) => {
        const flag = (data || []).every(
          (f) =>
            f.status === "COMPLETED" ||
            f.status === "ERROR" ||
            f.status === "DELETING"
        );
        if (!flag) {
          return 5_000;
        } else {
          return false;
        }
      },
      select: sortVersionListByIdCallback,
    }
  );
  return result;
};

export const useCreateVersionMutation = (
  onSuccess?: (version: Version) => void
): UseMutationResult<Version, ApiClientError, void> => {
  const client = useClientInstance();
  const queryClient = useQueryClient();
  return useMutation<Version, ApiClientError, void>(
    async () => {
      return (await client.create(versionResource, {})).data;
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
  const client = useClientInstance();
  const queryClient = useQueryClient();
  return useMutation<void, ApiClientError, Version>(
    (version: Version) => {
      return client.delete(versionResource, `${version.id}`);
    },
    {
      onSuccess: () => {
        queryClient.invalidateQueries("versions");
        onSuccess && onSuccess();
      },
    }
  );
};
