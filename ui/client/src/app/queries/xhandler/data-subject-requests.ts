import type { UseQueryOptions } from "@tanstack/react-query";
import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  closeDataSubjectRequest,
  type CloseDataSubjectRequestResponses,
  createDataSubjectRequest,
  type CreateDataSubjectRequestResponses,
  getDataSubjectPersonalData,
  type GetDataSubjectPersonalDataResponses,
  listDataSubjectRequests,
  type ListDataSubjectRequestsResponses,
  type NewRequest,
} from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";

import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const DataSubjectRequestsQueryKey = "data-subject-requests";

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

/** The spec types requests as free maps (the resource returns `Map<String, Object>`). */
export type DataSubjectRequestRecord =
  ListDataSubjectRequestsResponses[200][number];

/** The fields `DataSubjectRequests.view` writes, read defensively off the free map. */
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
  record: DataSubjectRequestRecord,
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
  (Array.isArray(data.documents) ? (data.documents as unknown[]) : [])
    .filter(
      (item): item is Record<string, unknown> =>
        typeof item === "object" && item !== null,
    )
    .map((item) => ({
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

export const dataSubjectRequestKeys = {
  all: () => [DataSubjectRequestsQueryKey] as const,
  list: (tenantId: string) =>
    [...dataSubjectRequestKeys.all(), tenantId, "list"] as const,
  personalData: (tenantId: string, requestId: string) =>
    [
      ...dataSubjectRequestKeys.all(),
      tenantId,
      "personal-data",
      requestId,
    ] as const,
};

type DataSubjectRequestListOptions = Omit<
  UseQueryOptions<DataSubjectRequestRecord[], XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

/** `GET /data-subject-requests`: the latest 200, unpaged by the API. */
export const dataSubjectRequestsListQueryOptions = ({
  tenantId,
  options,
}: {
  tenantId: string;
  options?: DataSubjectRequestListOptions;
}) =>
  queryOptions<DataSubjectRequestRecord[], XhandlerAxiosError>({
    ...options,
    queryKey: dataSubjectRequestKeys.list(tenantId),
    queryFn: async () =>
      (
        await listDataSubjectRequests({
          path: { tenantId },
          throwOnError: true,
        })
      ).data,
  });

export const useDataSubjectRequestsListQuery = ({
  options,
}: {
  options?: DataSubjectRequestListOptions;
}) => {
  const tenantId = useSelectedTenantId();
  return useQuery(dataSubjectRequestsListQueryOptions({ tenantId, options }));
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
    CreateDataSubjectRequestResponses[200],
    IDataSubjectRequestCreateVariables,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<
    CreateDataSubjectRequestResponses[200],
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
    CloseDataSubjectRequestResponses[200],
    IDataSubjectRequestCloseVariables,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<
    CloseDataSubjectRequestResponses[200],
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
