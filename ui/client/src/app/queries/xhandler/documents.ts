import type { UseQueryOptions } from "@tanstack/react-query";
import {
  keepPreviousData,
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
  useSuspenseQuery,
} from "@tanstack/react-query";

import {
  type AccessLink,
  cancelVoid,
  createDocumentAccessLink,
  type DocumentView,
  downloadDocumentFile,
  emailDocument,
  type EmailRequest,
  type Event,
  getDocument,
  listDocumentEvents,
  listDocuments,
  type ListDocumentsData,
  type PageDocumentView,
  type Queued,
  retryDocument,
  revokeDocumentAccessLinks,
  voidDocument,
  type VoidRequest,
} from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";

import { downloadFile } from "../utils/fileDownload";
import type { ListParams } from "../utils/listParams";
import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const DocumentsQueryKey = "documents";

export type IDocumentListParams = ListParams<ListDocumentsData>;

/** A document's statuses (DESIGN.md §9.1); the spec types `status` as a plain string. */
export const DOCUMENT_STATUSES = [
  "draft",
  "pending_send",
  "in_progress",
  "accepted",
  "accepted_with_observations",
  "rejected",
  "exception",
  "unknown",
  "voiding",
  "voided",
] as const;

export type DocumentStatus = (typeof DOCUMENT_STATUSES)[number];

export const isDocumentStatus = (value: string): value is DocumentStatus =>
  (DOCUMENT_STATUSES as readonly string[]).includes(value);

/** SUNAT catalog 01 codes the service issues, plus its summaries (RC, RA, RR). */
export const TIPOS_COMPROBANTE = [
  "01",
  "03",
  "07",
  "08",
  "09",
  "31",
  "20",
  "40",
  "RC",
  "RA",
  "RR",
] as const;

export type TipoComprobante = (typeof TIPOS_COMPROBANTE)[number];

export const isTipoComprobante = (value: string): value is TipoComprobante =>
  (TIPOS_COMPROBANTE as readonly string[]).includes(value);

/** The kinds of `GET …/files/{kind}` the console downloads. */
export const DOCUMENT_FILE_KINDS = ["xml", "cdr", "pdf"] as const;

export type DocumentFileKind = (typeof DOCUMENT_FILE_KINDS)[number];

/** Statuses that change without anyone acting: the UI refreshes while a document is in one. */
export const isDocumentSettling = (status: string | undefined): boolean =>
  status === "pending_send" ||
  status === "in_progress" ||
  status === "unknown" ||
  status === "voiding";

/** `DocumentControl.RETRY_FROM`: where a resend makes sense (the server decides for good). */
export const isDocumentRetryable = (document: DocumentView): boolean =>
  (document.status === "pending_send" ||
    document.status === "in_progress" ||
    document.status === "unknown" ||
    document.status === "exception") &&
  document.code_class !== "ticket_lost";

/** The §9.3 voiding guards the UI can see: accepted, not delivered, not a summary or a guía. */
export const isDocumentVoidable = (document: DocumentView): boolean =>
  (document.status === "accepted" ||
    document.status === "accepted_with_observations") &&
  !document.delivered_at &&
  !["RC", "RA", "RR", "09", "31"].includes(document.tipo_comprobante ?? "");

/** Retenciones and percepciones are voided by an RR, which needs the reversal's date. */
export const needsReversalDate = (document: DocumentView): boolean =>
  document.tipo_comprobante === "20" || document.tipo_comprobante === "40";

export const documentKeys = {
  all: () => [DocumentsQueryKey] as const,
  list: (tenantId: string) =>
    [...documentKeys.all(), tenantId, "list"] as const,
  detail: (tenantId: string, documentId: string) =>
    [...documentKeys.all(), tenantId, "detail", documentId] as const,
  events: (tenantId: string, documentId: string) =>
    [...documentKeys.detail(tenantId, documentId), "events"] as const,

  listQuery: (tenantId: string, params: IDocumentListParams) =>
    [...documentKeys.list(tenantId), params] as const,
};

type DocumentListOptions = Omit<
  UseQueryOptions<PageDocumentView, XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

export const documentsListQueryOptions = ({
  tenantId,
  params,
  options,
}: {
  tenantId: string;
  params: IDocumentListParams;
  options?: DocumentListOptions;
}) =>
  queryOptions<PageDocumentView, XhandlerAxiosError>({
    placeholderData: keepPreviousData,
    ...options,
    queryKey: documentKeys.listQuery(tenantId, params),
    queryFn: async () =>
      (
        await listDocuments({
          path: { tenantId },
          query: params,
          throwOnError: true,
        })
      ).data,
  });

export const useDocumentsListQuery = ({
  params,
  options,
}: {
  params: IDocumentListParams;
  options?: DocumentListOptions;
}) => {
  const tenantId = useSelectedTenantId();
  return useQuery(documentsListQueryOptions({ tenantId, params, options }));
};

type DocumentDetailOptions = Omit<
  UseQueryOptions<DocumentView, XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

export const documentDetailQueryOptions = ({
  tenantId,
  documentId,
}: {
  tenantId: string;
  documentId: string;
}) =>
  queryOptions<DocumentView, XhandlerAxiosError>({
    queryKey: documentKeys.detail(tenantId, documentId),
    queryFn: async () =>
      (
        await getDocument({
          path: { tenantId, documentId },
          throwOnError: true,
        })
      ).data,
  });

/** The detail page's document, refreshed through `options` while its status can change. */
export const useSuspenseDocumentDetailQuery = ({
  documentId,
  options,
}: {
  documentId: string;
  options?: Omit<DocumentDetailOptions, "enabled" | "placeholderData">;
}) => {
  const tenantId = useSelectedTenantId();
  return useSuspenseQuery({
    ...options,
    ...documentDetailQueryOptions({ tenantId, documentId }),
  });
};

/** Another document by id (the RA/RC/RR voiding one), skipped while there is none. */
export const useDocumentDetailQuery = ({
  documentId,
  options,
}: {
  documentId: string | undefined;
  options?: DocumentDetailOptions;
}) => {
  const tenantId = useSelectedTenantId();
  return useQuery({
    ...options,
    ...documentDetailQueryOptions({ tenantId, documentId: documentId ?? "" }),
    enabled: !!documentId && (options?.enabled ?? true),
  });
};

type DocumentEventsOptions = Omit<
  UseQueryOptions<Event[], XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

export const documentEventsQueryOptions = ({
  tenantId,
  documentId,
  options,
}: {
  tenantId: string;
  documentId: string;
  options?: DocumentEventsOptions;
}) =>
  queryOptions<Event[], XhandlerAxiosError>({
    ...options,
    queryKey: documentKeys.events(tenantId, documentId),
    queryFn: async () =>
      (
        await listDocumentEvents({
          path: { tenantId, documentId },
          throwOnError: true,
        })
      ).data,
  });

/** The document's timeline, oldest first (unpaged by the API: one row per status change). */
export const useDocumentEventsQuery = ({
  documentId,
  options,
}: {
  documentId: string;
  options?: DocumentEventsOptions;
}) => {
  const tenantId = useSelectedTenantId();
  return useQuery(
    documentEventsQueryOptions({ tenantId, documentId, options }),
  );
};

/** Refreshes what a write to one document changes: its detail, timeline and every list page. */
const useInvalidateDocument = () => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return async (documentId: string) => {
    await queryClient.invalidateQueries({
      queryKey: documentKeys.detail(tenantId, documentId),
    });
    void queryClient.invalidateQueries({
      queryKey: documentKeys.list(tenantId),
    });
  };
};

/** `POST …/documents/{id}/retry`: sends again now (an `exception` keeps its number). */
export const useDocumentRetryMutation = (
  options?: IMutationCallbacks<DocumentView, DocumentView, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const invalidate = useInvalidateDocument();
  return useMutation<DocumentView, XhandlerAxiosError, DocumentView>({
    mutationFn: async (document) =>
      (
        await retryDocument({
          path: { tenantId, documentId: document.id ?? "" },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, document) => {
      await invalidate(document.id ?? "");
      await options?.onSuccess?.(data, document);
    },
    onError: options?.onError,
  });
};

export interface IDocumentVoidVariables {
  documentId: string;
  body: VoidRequest;
}

/**
 * `POST …/documents/{id}/void`: `202` with the original document, now `voiding` until SUNAT
 * answers its RA, RC or RR (DESIGN.md §11.3).
 */
export const useDocumentVoidMutation = (
  options?: IMutationCallbacks<
    DocumentView,
    IDocumentVoidVariables,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  const invalidate = useInvalidateDocument();
  return useMutation<DocumentView, XhandlerAxiosError, IDocumentVoidVariables>({
    mutationFn: async ({ documentId, body }) =>
      (
        await voidDocument({
          path: { tenantId, documentId },
          body,
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      await invalidate(variables.documentId);
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};

/** `POST …/documents/{id}/void/cancel`: the original goes back to its previous status. */
export const useDocumentCancelVoidMutation = (
  options?: IMutationCallbacks<DocumentView, DocumentView, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const invalidate = useInvalidateDocument();
  return useMutation<DocumentView, XhandlerAxiosError, DocumentView>({
    mutationFn: async (document) =>
      (
        await cancelVoid({
          path: { tenantId, documentId: document.id ?? "" },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, document) => {
      await invalidate(document.id ?? "");
      if (document.voided_by_document_id) {
        await invalidate(document.voided_by_document_id);
      }
      await options?.onSuccess?.(data, document);
    },
    onError: options?.onError,
  });
};

export interface IDocumentEmailVariables {
  documentId: string;
  body: EmailRequest;
}

/** `POST …/documents/{id}/email`: `202`, a job sends it. */
export const useDocumentEmailMutation = (
  options?: IMutationCallbacks<
    Queued,
    IDocumentEmailVariables,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  const invalidate = useInvalidateDocument();
  return useMutation<Queued, XhandlerAxiosError, IDocumentEmailVariables>({
    mutationFn: async ({ documentId, body }) =>
      (
        await emailDocument({
          path: { tenantId, documentId },
          body,
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      // Delivery shows on the document once the job has sent it.
      await invalidate(variables.documentId);
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};

/** `POST …/documents/{id}/access-link`: a new buyer link; earlier ones stay valid. */
export const useDocumentAccessLinkCreateMutation = (
  options?: IMutationCallbacks<AccessLink, DocumentView, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  return useMutation<AccessLink, XhandlerAxiosError, DocumentView>({
    mutationFn: async (document) =>
      (
        await createDocumentAccessLink({
          path: { tenantId, documentId: document.id ?? "" },
          throwOnError: true,
        })
      ).data,
    onSuccess: options?.onSuccess,
    onError: options?.onError,
  });
};

/** `POST …/documents/{id}/access-links/revoke`: ends every buyer link of the document now. */
export const useDocumentAccessLinksRevokeMutation = (
  options?: IMutationCallbacks<void, DocumentView, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  return useMutation<void, XhandlerAxiosError, DocumentView>({
    mutationFn: async (document) => {
      await revokeDocumentAccessLinks({
        path: { tenantId, documentId: document.id ?? "" },
        throwOnError: true,
      });
    },
    onSuccess: options?.onSuccess,
    onError: options?.onError,
  });
};

export interface IDocumentFileVariables {
  document: DocumentView;
  kind: DocumentFileKind;
}

const FILE_EXTENSIONS: Record<DocumentFileKind, string> = {
  xml: "xml",
  cdr: "zip",
  pdf: "pdf",
};

/** `GET …/documents/{id}/files/{kind}` saved as a file (reads are audited by the service). */
export const useDocumentFileDownloadMutation = (
  options?: IMutationCallbacks<
    void,
    IDocumentFileVariables,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  return useMutation<void, XhandlerAxiosError, IDocumentFileVariables>({
    mutationFn: ({ document, kind }) =>
      downloadFile({
        // The spec types the body as JSON (the input/enriched kinds); these kinds are binary.
        request: () =>
          downloadDocumentFile({
            path: { tenantId, documentId: document.id ?? "", kind },
            responseType: "blob",
            throwOnError: true,
          }),
        fallbackName: `${document.serie_numero ?? document.id ?? "documento"}${
          kind === "cdr" ? "-cdr" : ""
        }.${FILE_EXTENSIONS[kind]}`,
      }),
    onSuccess: options?.onSuccess,
    onError: options?.onError,
  });
};
