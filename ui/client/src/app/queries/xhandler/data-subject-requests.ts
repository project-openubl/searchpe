import type { UseQueryOptions } from "@tanstack/react-query";
import {
  keepPreviousData,
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  closeDataSubjectRequest,
  createDataSubjectRequest,
  type DataSubjectRequestView,
  getDataSubjectPersonalData,
  type GetDataSubjectPersonalDataResponses,
  listDataSubjectRequests,
  type ListDataSubjectRequestsData,
  type NewRequest,
  type PageDataSubjectRequestView,
} from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";

import type { ListParams } from "../utils/listParams";
import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const DataSubjectRequestsQueryKey = "data-subject-requests";

/** One page of the list: the `status` filter, `limit` and `offset`. */
export type IDataSubjectRequestListParams =
  ListParams<ListDataSubjectRequestsData>;

/** `NewRequest.kind`: the ARCO rights of Ley 29733. */
export const DATA_SUBJECT_REQUEST_KINDS = [
  "access",
  "rectification",
  "cancellation",
  "opposition",
] as const;

export type DataSubjectRequestKind =
  (typeof DATA_SUBJECT_REQUEST_KINDS)[number];

export const isDataSubjectRequestKind = (
  value: string,
): value is DataSubjectRequestKind =>
  (DATA_SUBJECT_REQUEST_KINDS as readonly string[]).includes(value);

/** `data_subject_request.status` (migration V22). */
export const DATA_SUBJECT_REQUEST_STATUSES = ["open", "closed"] as const;

export type DataSubjectRequestStatus =
  (typeof DATA_SUBJECT_REQUEST_STATUSES)[number];

export const isDataSubjectRequestStatus = (
  value: string,
): value is DataSubjectRequestStatus =>
  (DATA_SUBJECT_REQUEST_STATUSES as readonly string[]).includes(value);

/** A request with every field present (missing text as ""), as the list and dialogs show it. */
export interface IDataSubjectRequestRow {
  id: string;
  subjectDocType: string;
  subjectDocNumber: string;
  subjectName: string;
  kind: string;
  channel: string;
  details: string;
  receivedAt: string;
  responseDueAt: string;
  status: string;
  resolution: string;
  closedAt: string;
}

const text = (value: unknown): string =>
  typeof value === "string" ? value : "";

export const toDataSubjectRequestRow = (
  record: DataSubjectRequestView,
): IDataSubjectRequestRow => ({
  id: text(record.id),
  subjectDocType: text(record.subject_doc_type),
  subjectDocNumber: text(record.subject_doc_number),
  subjectName: text(record.subject_name),
  kind: text(record.kind),
  channel: text(record.channel),
  details: text(record.details),
  receivedAt: text(record.received_at),
  responseDueAt: text(record.response_due_at),
  status: text(record.status),
  resolution: text(record.resolution),
  closedAt: text(record.closed_at),
});

/** A document issued to the person, as `getDataSubjectPersonalData` lists it. */
export interface IPersonalDataDocument {
  id: string;
  issuerRuc: string;
  tipoComprobante: string;
  serieNumero: string;
  fechaEmision: string;
  nombre: string;
  moneda: string;
  total: string;
  status: string;
}

export type PersonalDataRecord = GetDataSubjectPersonalDataResponses[200];

export const personalDataDocuments = (
  data: PersonalDataRecord,
): IPersonalDataDocument[] =>
  (data.documents ?? []).map((item) => ({
    id: text(item.id),
    issuerRuc: text(item.issuer_ruc),
    tipoComprobante: text(item.tipo_comprobante),
    serieNumero: text(item.serie_numero),
    fechaEmision: text(item.fecha_emision),
    nombre: text(item.nombre),
    moneda: text(item.moneda),
    total: text(item.total),
    status: text(item.status),
  }));

/** A draft that names the person as buyer, as `getDataSubjectPersonalData` lists it (deleted ones too). */
export interface IPersonalDataDraft {
  id: string;
  issuerRuc: string;
  tipoComprobante: string;
  serie: string;
  nombre: string;
  status: string;
  createdAt: string;
  deletedAt: string;
}

export const personalDataDrafts = (
  data: PersonalDataRecord,
): IPersonalDataDraft[] =>
  (data.drafts ?? []).map((item) => ({
    id: text(item.id),
    issuerRuc: text(item.issuer_ruc),
    tipoComprobante: text(item.tipo_comprobante),
    serie: text(item.serie),
    nombre: text(item.nombre),
    status: text(item.status),
    createdAt: text(item.created_at),
    deletedAt: text(item.deleted_at),
  }));

export const dataSubjectRequestKeys = {
  all: () => [DataSubjectRequestsQueryKey] as const,
  list: (tenantId: string) =>
    [...dataSubjectRequestKeys.all(), tenantId, "list"] as const,
  listQuery: (tenantId: string, params: IDataSubjectRequestListParams) =>
    [...dataSubjectRequestKeys.list(tenantId), params] as const,
  personalData: (tenantId: string, requestId: string) =>
    [
      ...dataSubjectRequestKeys.all(),
      tenantId,
      "personal-data",
      requestId,
    ] as const,
};

type DataSubjectRequestListOptions = Omit<
  UseQueryOptions<PageDataSubjectRequestView, XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

/** `GET /data-subject-requests`: one page of the requests, newest first. */
export const dataSubjectRequestsListQueryOptions = ({
  tenantId,
  params,
  options,
}: {
  tenantId: string;
  params: IDataSubjectRequestListParams;
  options?: DataSubjectRequestListOptions;
}) =>
  queryOptions<PageDataSubjectRequestView, XhandlerAxiosError>({
    placeholderData: keepPreviousData,
    ...options,
    queryKey: dataSubjectRequestKeys.listQuery(tenantId, params),
    queryFn: async () =>
      (
        await listDataSubjectRequests({
          path: { tenantId },
          query: params,
          throwOnError: true,
        })
      ).data,
  });

export const useDataSubjectRequestsListQuery = ({
  params,
  options,
}: {
  params: IDataSubjectRequestListParams;
  options?: DataSubjectRequestListOptions;
}) => {
  const tenantId = useSelectedTenantId();
  return useQuery(
    dataSubjectRequestsListQueryOptions({ tenantId, params, options }),
  );
};

export const personalDataQueryOptions = ({
  tenantId,
  requestId,
}: {
  tenantId: string;
  requestId: string;
}) =>
  queryOptions<PersonalDataRecord, XhandlerAxiosError>({
    queryKey: dataSubjectRequestKeys.personalData(tenantId, requestId),
    queryFn: async () =>
      (
        await getDataSubjectPersonalData({
          path: { tenantId, id: requestId },
          throwOnError: true,
        })
      ).data,
    // Each read is audited: don't re-read on focus.
    staleTime: Infinity,
  });

/** What the platform holds about the request's person (each read is audited). */
export const usePersonalDataQuery = ({ requestId }: { requestId: string }) => {
  const tenantId = useSelectedTenantId();
  return useQuery({
    ...personalDataQueryOptions({ tenantId, requestId }),
    enabled: !!requestId,
  });
};

export interface IDataSubjectRequestCreateVariables {
  body: NewRequest;
  idempotencyKey: string;
}

/** `POST /data-subject-requests` (admin): records it and starts its legal term. */
export const useDataSubjectRequestCreateMutation = (
  options?: IMutationCallbacks<
    DataSubjectRequestView,
    IDataSubjectRequestCreateVariables,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<
    DataSubjectRequestView,
    XhandlerAxiosError,
    IDataSubjectRequestCreateVariables
  >({
    mutationFn: async ({ body, idempotencyKey }) =>
      (
        await createDataSubjectRequest({
          path: { tenantId },
          body,
          headers: { "Idempotency-Key": idempotencyKey },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      await queryClient.invalidateQueries({
        queryKey: dataSubjectRequestKeys.list(tenantId),
      });
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};

export interface IDataSubjectRequestCloseVariables {
  requestId: string;
  resolution: string;
}

/** `POST /data-subject-requests/{id}/close` (admin): an open request, with its resolution. */
export const useDataSubjectRequestCloseMutation = (
  options?: IMutationCallbacks<
    DataSubjectRequestView,
    IDataSubjectRequestCloseVariables,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<
    DataSubjectRequestView,
    XhandlerAxiosError,
    IDataSubjectRequestCloseVariables
  >({
    mutationFn: async ({ requestId, resolution }) =>
      (
        await closeDataSubjectRequest({
          path: { tenantId, id: requestId },
          body: { resolution },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      await queryClient.invalidateQueries({
        queryKey: dataSubjectRequestKeys.list(tenantId),
      });
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};
