import type { UseQueryOptions } from "@tanstack/react-query";
import {
  keepPreviousData,
  queryOptions,
  useMutation,
  useQuery,
  useSuspenseQuery,
} from "@tanstack/react-query";

import {
  type ConformityView,
  exportConformity,
  getDocumentConformity,
  listConformity,
  type ListConformityData,
} from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";

import { downloadFile } from "../utils/fileDownload";
import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const ConformityQueryKey = "conformity";

/** `state` filter of the list and the export. */
export type IConformityListParams = NonNullable<ListConformityData["query"]>;

/** A factura negociable's conformity states (DESIGN.md §11.8); the spec types it as a string. */
export const CONFORMITY_STATES = [
  "pending",
  "agreed",
  "objected",
  "silent_agreement",
] as const;

export type ConformityState = (typeof CONFORMITY_STATES)[number];

export const isConformityState = (value: string): value is ConformityState =>
  (CONFORMITY_STATES as readonly string[]).includes(value);

export const conformityKeys = {
  all: () => [ConformityQueryKey] as const,
  company: (tenantId: string, companyId: string) =>
    [...conformityKeys.all(), tenantId, "company", companyId] as const,
  document: (tenantId: string, documentId: string) =>
    [...conformityKeys.all(), tenantId, "document", documentId] as const,

  companyQuery: (
    tenantId: string,
    companyId: string,
    params: IConformityListParams,
  ) => [...conformityKeys.company(tenantId, companyId), params] as const,
};

type ConformityListOptions = Omit<
  UseQueryOptions<ConformityView[], XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

/**
 * `GET …/companies/{id}/conformity`: the company's facturas negociables, newest acceptance first.
 * Unpaged by the API (it caps the answer at 1000 rows), so it is read whole and paged on screen.
 */
export const conformityListQueryOptions = ({
  tenantId,
  companyId,
  params,
  options,
}: {
  tenantId: string;
  companyId: string;
  params: IConformityListParams;
  options?: ConformityListOptions;
}) =>
  queryOptions<ConformityView[], XhandlerAxiosError>({
    placeholderData: keepPreviousData,
    ...options,
    queryKey: conformityKeys.companyQuery(tenantId, companyId, params),
    queryFn: async () =>
      (
        await listConformity({
          path: { tenantId, companyId },
          query: params,
          throwOnError: true,
        })
      ).data,
  });

export const useConformityListQuery = ({
  companyId,
  params,
  options,
}: {
  companyId: string;
  params: IConformityListParams;
  options?: ConformityListOptions;
}) => {
  const tenantId = useSelectedTenantId();
  return useQuery(
    conformityListQueryOptions({ tenantId, companyId, params, options }),
  );
};

/** `GET …/documents/{id}/conformity`: one factura's conformity, with the suggested NC 13. */
export const documentConformityQueryOptions = ({
  tenantId,
  documentId,
}: {
  tenantId: string;
  documentId: string;
}) =>
  queryOptions<ConformityView, XhandlerAxiosError>({
    queryKey: conformityKeys.document(tenantId, documentId),
    queryFn: async () =>
      (
        await getDocumentConformity({
          path: { tenantId, documentId },
          throwOnError: true,
        })
      ).data,
  });

export const useSuspenseDocumentConformityQuery = ({
  documentId,
}: {
  documentId: string;
}) => {
  const tenantId = useSelectedTenantId();
  return useSuspenseQuery(
    documentConformityQueryOptions({ tenantId, documentId }),
  );
};

export interface IConformityExportVariables {
  companyId: string;
  params: IConformityListParams;
}

/** `GET …/conformity/export`: saves the CSV for factoring when the user asks for it (audited). */
export const useExportConformityMutation = (
  options?: IMutationCallbacks<
    void,
    IConformityExportVariables,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  return useMutation<void, XhandlerAxiosError, IConformityExportVariables>({
    mutationFn: ({ companyId, params }) =>
      downloadFile({
        request: () =>
          exportConformity({
            path: { tenantId, companyId },
            query: params,
            // As bytes: decoding it as text would drop the BOM Excel needs for UTF-8.
            responseType: "blob",
            throwOnError: true,
          }),
        fallbackName: `facturas-negociables-${companyId}.csv`,
      }),
    onSuccess: async (data, variables) => {
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};
