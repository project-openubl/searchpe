import { queryOptions, useMutation, useQuery } from "@tanstack/react-query";

import { xhandlerPublicClient } from "@app/axios-config/apiInit";
import {
  downloadPublicDocumentFile,
  getPublicDocument,
  type PublicDocument,
} from "@app/client/xhandler";

import { downloadFile } from "../utils/fileDownload";
import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const PublicDocumentsQueryKey = "public-documents";

export const publicDocumentKeys = {
  all: () => [PublicDocumentsQueryKey] as const,
  detail: (token: string) =>
    [...publicDocumentKeys.all(), "detail", token] as const,
};

/** The files a buyer may download (`PublicDocument.files`); the spec types them as strings. */
export const PUBLIC_DOCUMENT_FILE_KINDS = ["pdf", "xml", "cdr"] as const;

export type PublicDocumentFileKind =
  (typeof PUBLIC_DOCUMENT_FILE_KINDS)[number];

export const isPublicDocumentFileKind = (
  value: string,
): value is PublicDocumentFileKind =>
  (PUBLIC_DOCUMENT_FILE_KINDS as readonly string[]).includes(value);

/**
 * `GET /api/v1/public/documents/{token}` through the token-less client (ADR 0003): the
 * capability link is the only credential.
 */
export const publicDocumentQueryOptions = ({ token }: { token: string }) =>
  queryOptions<PublicDocument, XhandlerAxiosError>({
    queryKey: publicDocumentKeys.detail(token),
    queryFn: async () =>
      (
        await getPublicDocument({
          client: xhandlerPublicClient,
          path: { token },
          throwOnError: true,
        })
      ).data,
    // An unknown, revoked or expired link won't come back on retry.
    retry: false,
  });

export const usePublicDocumentQuery = ({ token }: { token: string }) =>
  useQuery({ ...publicDocumentQueryOptions({ token }), enabled: !!token });

export interface IPublicDocumentFileVariables {
  kind: PublicDocumentFileKind;
  /** Names the saved file when the server sends no `Content-Disposition`. */
  serieNumero: string | undefined;
}

const FILE_EXTENSIONS: Record<PublicDocumentFileKind, string> = {
  pdf: "pdf",
  xml: "xml",
  cdr: "zip",
};

/**
 * `GET /api/v1/public/documents/{token}/{kind}` saved as a file, without a token. The service
 * counts the first download as the document's delivery to the buyer (DESIGN.md §12).
 */
export const usePublicDocumentFileDownloadMutation = (
  token: string,
  options?: IMutationCallbacks<
    void,
    IPublicDocumentFileVariables,
    XhandlerAxiosError
  >,
) =>
  useMutation<void, XhandlerAxiosError, IPublicDocumentFileVariables>({
    mutationFn: ({ kind, serieNumero }) =>
      downloadFile({
        request: () =>
          downloadPublicDocumentFile({
            client: xhandlerPublicClient,
            path: { token, kind },
            responseType: "blob",
            throwOnError: true,
          }),
        fallbackName: `${serieNumero ?? "comprobante"}${
          kind === "cdr" ? "-cdr" : ""
        }.${FILE_EXTENSIONS[kind]}`,
      }),
    onSuccess: options?.onSuccess,
    onError: options?.onError,
  });
