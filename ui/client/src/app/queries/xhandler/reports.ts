import type { UseQueryOptions } from "@tanstack/react-query";
import {
  keepPreviousData,
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  createReport,
  downloadReportFile,
  listReports,
  type ListReportsData,
  type PageReportView,
  type ReportRequest,
  type ReportView,
} from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";

import { downloadFile } from "../utils/fileDownload";
import type { ListParams } from "../utils/listParams";
import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";
import { jobKeys } from "./jobs";

export const ReportsQueryKey = "reports";

/** One page of the list: `limit` and `offset`. */
export type IReportListParams = ListParams<ListReportsData>;

/** `ReportRequest.register` (DESIGN.md §11.9). */
export const REPORT_REGISTERS = ["sales", "purchases"] as const;

export type ReportRegister = (typeof REPORT_REGISTERS)[number];

export const isReportRegister = (value: string): value is ReportRegister =>
  (REPORT_REGISTERS as readonly string[]).includes(value);

/** `ReportRequest.format`. */
export const REPORT_FORMATS = ["csv", "xlsx", "ple", "sire"] as const;

export type ReportFormat = (typeof REPORT_FORMATS)[number];

export const isReportFormat = (value: string): value is ReportFormat =>
  (REPORT_FORMATS as readonly string[]).includes(value);

/** `report.status` (migration V19). */
export const REPORT_STATUSES = ["pending", "done", "failed"] as const;

export type ReportStatus = (typeof REPORT_STATUSES)[number];

export const isReportStatus = (value: string): value is ReportStatus =>
  (REPORT_STATUSES as readonly string[]).includes(value);

export const reportKeys = {
  all: () => [ReportsQueryKey] as const,
  list: (tenantId: string, companyId: string) =>
    [...reportKeys.all(), tenantId, companyId, "list"] as const,

  listQuery: (tenantId: string, companyId: string, params: IReportListParams) =>
    [...reportKeys.list(tenantId, companyId), params] as const,
};

type ReportListOptions = Omit<
  UseQueryOptions<PageReportView, XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

/** `GET …/reports`: one page of the company's reports, newest first. */
export const reportsListQueryOptions = ({
  tenantId,
  companyId,
  params,
  options,
}: {
  tenantId: string;
  companyId: string;
  params: IReportListParams;
  options?: ReportListOptions;
}) =>
  queryOptions<PageReportView, XhandlerAxiosError>({
    placeholderData: keepPreviousData,
    ...options,
    queryKey: reportKeys.listQuery(tenantId, companyId, params),
    queryFn: async () =>
      (
        await listReports({
          path: { tenantId, companyId },
          query: params,
          throwOnError: true,
        })
      ).data,
  });

export const useReportsListQuery = ({
  companyId,
  params,
  options,
}: {
  companyId: string;
  params: IReportListParams;
  options?: ReportListOptions;
}) => {
  const tenantId = useSelectedTenantId();
  return useQuery(
    reportsListQueryOptions({ tenantId, companyId, params, options }),
  );
};

export interface IReportCreateVariables {
  companyId: string;
  body: ReportRequest;
  /** One per user intent, reused on a retry of the same submission. */
  idempotencyKey: string;
}

/** `POST …/reports`: `202` with the report, which a job generates (`job_id`). */
export const useReportCreateMutation = (
  options?: IMutationCallbacks<
    ReportView,
    IReportCreateVariables,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<ReportView, XhandlerAxiosError, IReportCreateVariables>({
    mutationFn: async ({ companyId, body, idempotencyKey }) =>
      (
        await createReport({
          path: { tenantId, companyId },
          body,
          headers: { "Idempotency-Key": idempotencyKey },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      await queryClient.invalidateQueries({
        queryKey: reportKeys.list(tenantId, variables.companyId),
      });
      void queryClient.invalidateQueries({ queryKey: jobKeys.list(tenantId) });
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};

export interface IReportFileVariables {
  companyId: string;
  report: ReportView;
}

/** `GET …/reports/{id}/file` saved under the service's file name. */
export const useReportFileDownloadMutation = (
  options?: IMutationCallbacks<void, IReportFileVariables, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  return useMutation<void, XhandlerAxiosError, IReportFileVariables>({
    mutationFn: ({ companyId, report }) =>
      downloadFile({
        // The spec answers text for CSV/PLE, but Excel and SIRE are ZIP-like binaries.
        request: () =>
          downloadReportFile({
            path: { tenantId, companyId, reportId: report.id ?? "" },
            responseType: "blob",
            throwOnError: true,
          }),
        fallbackName: report.file_name ?? `reporte-${report.period ?? ""}`,
      }),
    onSuccess: options?.onSuccess,
    onError: options?.onError,
  });
};
