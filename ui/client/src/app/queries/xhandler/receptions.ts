import type { UseQueryOptions } from "@tanstack/react-query";
import {
  keepPreviousData,
  queryOptions,
  useMutation,
  useQuery,
} from "@tanstack/react-query";

import {
  downloadReceptionFile,
  getReception,
  listReceptions,
  type ListReceptionsData,
  type PageReceptionView,
  type ReceptionView,
} from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";

import { downloadFile } from "../utils/fileDownload";
import type { ListParams } from "../utils/listParams";
import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const ReceptionsQueryKey = "receptions";

export type IReceptionListParams = ListParams<ListReceptionsData>;

/** `reception.validity_state` (migration V16): what SUNAT's validity API said. */
export const RECEPTION_VALIDITY_STATES = [
  "unchecked",
  "valid",
  "not_found",
  "voided",
  "rejected",
  "unknown",
] as const;

export type ReceptionValidityState = (typeof RECEPTION_VALIDITY_STATES)[number];

export const isReceptionValidityState = (
  value: string,
): value is ReceptionValidityState =>
  (RECEPTION_VALIDITY_STATES as readonly string[]).includes(value);

/** Kinds of `GET …/receptions/{id}/files/{kind}`. */
export const RECEPTION_FILE_KINDS = ["xml", "pdf", "cdr"] as const;

export type ReceptionFileKind = (typeof RECEPTION_FILE_KINDS)[number];

/** Whether the reception stored that file (the XML always). */
export const hasReceptionFile = ({
  reception,
  kind,
}: {
  reception: ReceptionView;
  kind: ReceptionFileKind;
}): boolean =>
  kind === "xml" ||
  (kind === "pdf" && !!reception.has_pdf) ||
  (kind === "cdr" && !!reception.has_cdr);

export const receptionKeys = {
  all: () => [ReceptionsQueryKey] as const,
  list: (tenantId: string, companyId: string) =>
    [...receptionKeys.all(), tenantId, companyId, "list"] as const,

  detail: (tenantId: string, companyId: string, receptionId: string) =>
    [
      ...receptionKeys.all(),
      tenantId,
      companyId,
      "detail",
      receptionId,
    ] as const,

  listQuery: (
    tenantId: string,
    companyId: string,
    params: IReceptionListParams,
  ) => [...receptionKeys.list(tenantId, companyId), params] as const,
};

type ReceptionListOptions = Omit<
  UseQueryOptions<PageReceptionView, XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

export const receptionsListQueryOptions = ({
  tenantId,
  companyId,
  params,
  options,
}: {
  tenantId: string;
  companyId: string;
  params: IReceptionListParams;
  options?: ReceptionListOptions;
}) =>
  queryOptions<PageReceptionView, XhandlerAxiosError>({
    placeholderData: keepPreviousData,
    ...options,
    queryKey: receptionKeys.listQuery(tenantId, companyId, params),
    queryFn: async () =>
      (
        await listReceptions({
          path: { tenantId, companyId },
          query: params,
          throwOnError: true,
        })
      ).data,
  });

export const useReceptionsListQuery = ({
  companyId,
  params,
  options,
}: {
  companyId: string;
  params: IReceptionListParams;
  options?: ReceptionListOptions;
}) => {
  const tenantId = useSelectedTenantId();
  return useQuery(
    receptionsListQueryOptions({ tenantId, companyId, params, options }),
  );
};

/** `GET …/receptions/{id}`: one received document, with its latest validity check. */
export const receptionDetailQueryOptions = ({
  tenantId,
  companyId,
  receptionId,
}: {
  tenantId: string;
  companyId: string;
  receptionId: string;
}) =>
  queryOptions<ReceptionView, XhandlerAxiosError>({
    queryKey: receptionKeys.detail(tenantId, companyId, receptionId),
    queryFn: async () =>
      (
        await getReception({
          path: { tenantId, companyId, receptionId },
          throwOnError: true,
        })
      ).data,
  });

/** The detail of a reception opened from its list row: the row shows until the read answers. */
export const useReceptionQuery = ({
  companyId,
  reception,
}: {
  companyId: string;
  reception: ReceptionView | null;
}) => {
  const tenantId = useSelectedTenantId();
  const receptionId = reception?.id ?? "";
  return useQuery({
    ...receptionDetailQueryOptions({ tenantId, companyId, receptionId }),
    placeholderData: reception ?? undefined,
    enabled: !!tenantId && !!receptionId,
  });
};

export interface IReceptionFileVariables {
  companyId: string;
  reception: ReceptionView;
  kind: ReceptionFileKind;
}

/** `GET …/receptions/{id}/files/{kind}`: supplier files are untrusted, so only saved, never shown. */
export const useReceptionFileDownloadMutation = (
  options?: IMutationCallbacks<
    void,
    IReceptionFileVariables,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  return useMutation<void, XhandlerAxiosError, IReceptionFileVariables>({
    mutationFn: ({ companyId, reception, kind }) =>
      downloadFile({
        request: () =>
          downloadReceptionFile({
            path: {
              tenantId,
              companyId,
              receptionId: reception.id ?? "",
              kind,
            },
            throwOnError: true,
          }),
        fallbackName: `${reception.id ?? "recepcion"}.${kind === "cdr" ? "zip" : kind}`,
      }),
    onSuccess: options?.onSuccess,
    onError: options?.onError,
  });
};
