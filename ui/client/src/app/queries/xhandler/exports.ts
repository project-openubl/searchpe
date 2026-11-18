import type { UseQueryOptions } from "@tanstack/react-query";
import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  createExport,
  downloadExportPart,
  type ExitRequest,
  exitTenant,
  type ExitTenantResponses,
  type ExportView,
  listExports,
} from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";

import { downloadFile } from "../utils/fileDownload";
import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";
import { jobKeys } from "./jobs";
import { tenantKeys } from "./tenants";

export const ExportsQueryKey = "exports";

/** `tenant_export.status` (migration V22). */
export const EXPORT_STATUSES = ["pending", "done", "failed"] as const;

export type ExportStatus = (typeof EXPORT_STATUSES)[number];

export const isExportStatus = (value: string): value is ExportStatus =>
  (EXPORT_STATUSES as readonly string[]).includes(value);

/** One ZIP of an export; the spec types parts as free maps (`List<Map<String, Object>>`). */
export interface IExportPart {
  index: number;
  name: string;
  size: number | undefined;
  documents: number | undefined;
}

const numberOrUndefined = (value: unknown): number | undefined =>
  typeof value === "number" ? value : undefined;

/** The parts to download, the index (`indice.zip`) first, as `TenantExit.Export` writes them. */
export const exportParts = (exportView: ExportView): IExportPart[] =>
  (exportView.parts ?? []).map((part, index) => ({
    index,
    name: typeof part.name === "string" ? part.name : `parte-${index + 1}.zip`,
    size: numberOrUndefined(part.size),
    documents: numberOrUndefined(part.documents),
  }));

export const exportKeys = {
  all: () => [ExportsQueryKey] as const,
  list: (tenantId: string) => [...exportKeys.all(), tenantId, "list"] as const,
};

type ExportListOptions = Omit<
  UseQueryOptions<ExportView[], XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

/** `GET /exports`: the tenant's latest 20 exports, unpaged by the API. */
export const exportsListQueryOptions = ({
  tenantId,
  options,
}: {
  tenantId: string;
  options?: ExportListOptions;
}) =>
  queryOptions<ExportView[], XhandlerAxiosError>({
    ...options,
    queryKey: exportKeys.list(tenantId),
    queryFn: async () =>
      (await listExports({ path: { tenantId }, throwOnError: true })).data,
  });

export const useExportsListQuery = ({
  options,
}: {
  options?: ExportListOptions;
}) => {
  const tenantId = useSelectedTenantId();
  return useQuery(exportsListQueryOptions({ tenantId, options }));
};

/** `POST /exports`: `202`, a job builds the ZIPs; one per hour (409 `export_recent`). */
export const useExportCreateMutation = (
  options?: IMutationCallbacks<ExportView, string, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<ExportView, XhandlerAxiosError, string>({
    mutationFn: async (idempotencyKey) =>
      (
        await createExport({
          path: { tenantId },
          headers: { "Idempotency-Key": idempotencyKey },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, idempotencyKey) => {
      await queryClient.invalidateQueries({
        queryKey: exportKeys.list(tenantId),
      });
      void queryClient.invalidateQueries({ queryKey: jobKeys.list(tenantId) });
      await options?.onSuccess?.(data, idempotencyKey);
    },
    onError: options?.onError,
  });
};

export interface IExportPartVariables {
  exportId: string;
  part: IExportPart;
}

/** `GET /exports/{id}/parts/{index}`: one ZIP (downloads are audited). */
export const useExportPartDownloadMutation = (
  options?: IMutationCallbacks<void, IExportPartVariables, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  return useMutation<void, XhandlerAxiosError, IExportPartVariables>({
    mutationFn: ({ exportId, part }) =>
      downloadFile({
        request: () =>
          downloadExportPart({
            path: { tenantId, exportId, index: part.index },
            throwOnError: true,
          }),
        fallbackName: part.name,
      }),
    onSuccess: options?.onSuccess,
    onError: options?.onError,
  });
};

export type ExitTenantResult = ExitTenantResponses[200];

/**
 * `POST /exit` (owner): the organization leaves the platform. The service refuses while
 * documents wait for SUNAT or without a recent export covering every document.
 */
export const useTenantExitMutation = (
  options?: IMutationCallbacks<
    ExitTenantResult,
    ExitRequest,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<ExitTenantResult, XhandlerAxiosError, ExitRequest>({
    mutationFn: async (body) =>
      (await exitTenant({ path: { tenantId }, body, throwOnError: true })).data,
    onSuccess: async (data, body) => {
      await queryClient.invalidateQueries({ queryKey: tenantKeys.all() });
      await options?.onSuccess?.(data, body);
    },
    onError: options?.onError,
  });
};
