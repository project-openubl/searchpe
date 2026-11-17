import type { UseQueryOptions } from "@tanstack/react-query";
import {
  keepPreviousData,
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  cancelJob,
  type JobView,
  listJobs,
  type ListJobsData,
  type PageJobView,
} from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";

import type { ListParams } from "../utils/listParams";
import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const JobsQueryKey = "jobs";

export type IJobListParams = ListParams<ListJobsData>;

/** A job's states (DESIGN.md §10.4); the spec types `state` as a plain string. */
export const JOB_STATES = [
  "waiting",
  "running",
  "completed",
  "failed",
  "canceling",
  "canceled",
] as const;

export type JobState = (typeof JOB_STATES)[number];

export const isJobState = (value: string): value is JobState =>
  (JOB_STATES as readonly string[]).includes(value);

/** Jobs no worker will touch again; `canceling` still runs until its worker stops. */
export const isTerminalJobState = (state: string | undefined): boolean =>
  state === "completed" || state === "failed" || state === "canceled";

/** Whether a cancel request makes sense for the job (the server decides for good). */
export const isJobCancelable = (state: string | undefined): boolean =>
  state === "waiting" || state === "running";

export const jobKeys = {
  all: () => [JobsQueryKey] as const,
  list: (tenantId: string) => [...jobKeys.all(), tenantId, "list"] as const,
  detail: (tenantId: string, jobId: string) =>
    [...jobKeys.all(), tenantId, "detail", jobId] as const,

  listQuery: (tenantId: string, params: IJobListParams) =>
    [...jobKeys.list(tenantId), params] as const,
};

type JobListOptions = Omit<
  UseQueryOptions<PageJobView, XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

export const jobsListQueryOptions = ({
  tenantId,
  params,
  options,
}: {
  tenantId: string;
  params: IJobListParams;
  options?: JobListOptions;
}) =>
  queryOptions<PageJobView, XhandlerAxiosError>({
    placeholderData: keepPreviousData,
    ...options,
    queryKey: jobKeys.listQuery(tenantId, params),
    queryFn: async () =>
      (
        await listJobs({
          path: { tenantId },
          query: params,
          throwOnError: true,
        })
      ).data,
  });

export const useJobsListQuery = ({
  params,
  options,
}: {
  params: IJobListParams;
  options?: JobListOptions;
}) => {
  const tenantId = useSelectedTenantId();
  return useQuery(jobsListQueryOptions({ tenantId, params, options }));
};

/** `POST …/jobs/{id}/cancel`: answers the job, now `canceling` or `canceled`. */
export const useJobCancelMutation = (
  options?: IMutationCallbacks<JobView, JobView, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<JobView, XhandlerAxiosError, JobView>({
    mutationFn: async (job) =>
      (
        await cancelJob({
          path: { tenantId, jobId: job.id ?? "" },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, job) => {
      await queryClient.invalidateQueries({ queryKey: jobKeys.list(tenantId) });
      void queryClient.invalidateQueries({
        queryKey: jobKeys.detail(tenantId, job.id ?? ""),
      });
      await options?.onSuccess?.(data, job);
    },
    onError: options?.onError,
  });
};
