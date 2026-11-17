import { queryOptions, useQuery } from "@tanstack/react-query";

import { xhandlerPublicClient } from "@app/axios-config/apiInit";
import {
  getV1PublicDocumentsByToken,
  type PublicDocument,
} from "@app/client/xhandler";

import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const PublicDocumentsQueryKey = "public-documents";

export const publicDocumentKeys = {
  all: () => [PublicDocumentsQueryKey] as const,
  detail: (token: string) =>
    [...publicDocumentKeys.all(), "detail", token] as const,
};

/**
 * `GET /api/v1/public/documents/{token}` through the token-less client (ADR 0003): the
 * capability link is the only credential.
 */
export const publicDocumentQueryOptions = ({ token }: { token: string }) =>
  queryOptions<PublicDocument, XhandlerAxiosError>({
    queryKey: publicDocumentKeys.detail(token),
    queryFn: async () =>
      (
        await getV1PublicDocumentsByToken({
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
