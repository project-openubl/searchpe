import type { UseQueryOptions } from "@tanstack/react-query";
import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  deleteVersionsById,
  getVersions,
  postVersions,
  putVersionsById,
  type Status,
  type VersionDto,
} from "@app/client/searchpe";

import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { SearchpeAxiosError } from "../utils/openapiErrorTypes";
import { contribuyenteKeys } from "./contribuyentes";

export const VersionsQueryKey = "versions";

/**
 * Whether each status is final, so no job works on the version anymore. Mirrors searchpe's
 * `Status#isFinished`; a `Record`, so a new status fails to compile until it's classified.
 */
const FINISHED_VERSION_STATUSES: Record<Status, boolean> = {
  SCHEDULED: false,
  DOWNLOADING: false,
  UNZIPPING: false,
  IMPORTING: false,
  INDEXING: false,
  DELETING: false,
  CANCELLING: false,
  COMPLETED: true,
  ERROR: true,
  CANCELLED: true,
};

export const isVersionFinished = (status: Status | undefined): boolean =>
  !!status && FINISHED_VERSION_STATUSES[status];

/** An unfinished import can be cancelled (searchpe's `afterCancellationRequest`). */
export const isVersionCancellable = (status: Status | undefined): boolean =>
  !!status &&
  !isVersionFinished(status) &&
  status !== "CANCELLING" &&
  status !== "DELETING";

/** A finished version can be deleted; deleting the active one leaves no padrón to query. */
export const isVersionDeletable = (status: Status | undefined): boolean =>
  isVersionFinished(status);

export const VERSIONS_REFETCH_INTERVAL = 5000;

export const versionKeys = {
  all: () => [VersionsQueryKey] as const,
  list: () => [...versionKeys.all(), "list"] as const,
};

type VersionListOptions = Omit<
  UseQueryOptions<VersionDto[], SearchpeAxiosError>,
  "queryKey" | "queryFn"
>;

/**
 * `GET /api/versions`: every padrón version, newest first. The endpoint has no paging; the
 * daily clean-up job keeps the list short (list-reads rule exception, see searchpe-api).
 */
export const versionsListQueryOptions = ({
  options,
}: {
  options?: VersionListOptions;
}) =>
  queryOptions<VersionDto[], SearchpeAxiosError>({
    ...options,
    queryKey: versionKeys.list(),
    queryFn: async () => (await getVersions({ throwOnError: true })).data,
  });

export const useVersionsListQuery = ({
  options,
}: {
  options?: VersionListOptions;
}) => useQuery(versionsListQueryOptions({ options }));

const invalidateVersions = async (
  queryClient: ReturnType<typeof useQueryClient>,
) => {
  await queryClient.invalidateQueries({ queryKey: versionKeys.all() });
  // The active version decides what every contribuyente read answers.
  void queryClient.invalidateQueries({ queryKey: contribuyenteKeys.all() });
};

/** `POST /api/versions`: creates a version and schedules its import. */
export const useVersionCreateMutation = (
  options?: IMutationCallbacks<VersionDto, void, SearchpeAxiosError>,
) => {
  const queryClient = useQueryClient();
  return useMutation<VersionDto, SearchpeAxiosError, void>({
    mutationFn: async () => (await postVersions({ throwOnError: true })).data,
    onSuccess: async (data, variables) => {
      await invalidateVersions(queryClient);
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};

/** `PUT /api/versions/{id}` with status `CANCELLING`: the only update searchpe accepts. */
export const useVersionCancelMutation = (
  options?: IMutationCallbacks<void, VersionDto, SearchpeAxiosError>,
) => {
  const queryClient = useQueryClient();
  return useMutation<void, SearchpeAxiosError, VersionDto>({
    mutationFn: async (version) => {
      await putVersionsById({
        path: { id: version.id ?? 0 },
        // The body is a VersionEntity in the spec; searchpe reads only `status`.
        body: {
          createdAt: version.createdAt ?? "",
          updatedAt: version.updatedAt ?? "",
          records: version.records ?? 0,
          status: "CANCELLING",
        },
        throwOnError: true,
      });
    },
    onSuccess: async (data, version) => {
      await invalidateVersions(queryClient);
      await options?.onSuccess?.(data, version);
    },
    onError: options?.onError,
  });
};

/** `DELETE /api/versions/{id}`: marks it `DELETING` and schedules the deletion. */
export const useVersionDeleteMutation = (
  options?: IMutationCallbacks<void, VersionDto, SearchpeAxiosError>,
) => {
  const queryClient = useQueryClient();
  return useMutation<void, SearchpeAxiosError, VersionDto>({
    mutationFn: async (version) => {
      await deleteVersionsById({
        path: { id: version.id ?? 0 },
        throwOnError: true,
      });
    },
    onSuccess: async (data, version) => {
      await invalidateVersions(queryClient);
      await options?.onSuccess?.(data, version);
    },
    onError: options?.onError,
  });
};
