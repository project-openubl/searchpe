import type { UseQueryOptions } from "@tanstack/react-query";
import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  createNubefactToken,
  listAdapterTokens,
  type ListAdapterTokensResponses,
  type NubefactToken,
  revokeAdapterToken,
  type TokenRequest,
} from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";

import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const AdapterTokensQueryKey = "adapter-tokens";

/** A token as `listAdapterTokens` returns it (never the token itself). */
export type AdapterTokenRecord = ListAdapterTokensResponses[200][number];

/** A token row with defaults for the fields the spec leaves optional. */
export interface IAdapterTokenRow {
  id: string;
  kind: string;
  label: string;
  createdBy: string;
  createdAt: string;
  revokedAt: string | undefined;
}

const text = (value: unknown): string =>
  typeof value === "string" ? value : "";

export const toAdapterTokenRow = (
  record: AdapterTokenRecord,
): IAdapterTokenRow => ({
  id: text(record.id),
  kind: text(record.kind),
  label: text(record.label),
  createdBy: text(record.created_by),
  createdAt: text(record.created_at),
  revokedAt: text(record.revoked_at) || undefined,
});

export const adapterTokenKeys = {
  all: () => [AdapterTokensQueryKey] as const,
  list: (tenantId: string, companyId: string) =>
    [...adapterTokenKeys.all(), tenantId, companyId, "list"] as const,
};

type AdapterTokenListOptions = Omit<
  UseQueryOptions<AdapterTokenRecord[], XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

/** `GET …/adapter-tokens`: the company's tokens (never the secret), unpaged by the API. */
export const adapterTokensListQueryOptions = ({
  tenantId,
  companyId,
  options,
}: {
  tenantId: string;
  companyId: string;
  options?: AdapterTokenListOptions;
}) =>
  queryOptions<AdapterTokenRecord[], XhandlerAxiosError>({
    ...options,
    queryKey: adapterTokenKeys.list(tenantId, companyId),
    queryFn: async () =>
      (
        await listAdapterTokens({
          path: { tenantId, companyId },
          throwOnError: true,
        })
      ).data,
  });

export const useAdapterTokensListQuery = ({
  companyId,
  options,
}: {
  companyId: string;
  options?: AdapterTokenListOptions;
}) => {
  const tenantId = useSelectedTenantId();
  return useQuery(
    adapterTokensListQueryOptions({ tenantId, companyId, options }),
  );
};

export interface IAdapterTokenCreateVariables {
  companyId: string;
  body: TokenRequest;
  idempotencyKey: string;
}

/** `POST …/adapter-tokens/nubefact`: `201`, the token is in this answer only. */
export const useNubefactTokenCreateMutation = (
  options?: IMutationCallbacks<
    NubefactToken,
    IAdapterTokenCreateVariables,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<
    NubefactToken,
    XhandlerAxiosError,
    IAdapterTokenCreateVariables
  >({
    // The answer carries the token: drop the mutation from the cache once unobserved.
    gcTime: 0,
    mutationFn: async ({ companyId, body, idempotencyKey }) =>
      (
        await createNubefactToken({
          path: { tenantId, companyId },
          body,
          headers: { "Idempotency-Key": idempotencyKey },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      await queryClient.invalidateQueries({
        queryKey: adapterTokenKeys.list(tenantId, variables.companyId),
      });
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};

export interface IAdapterTokenRevokeVariables {
  companyId: string;
  token: IAdapterTokenRow;
}

/** `DELETE …/adapter-tokens/{id}`: `204`, the adapter stops answering to it. */
export const useAdapterTokenRevokeMutation = (
  options?: IMutationCallbacks<
    void,
    IAdapterTokenRevokeVariables,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<void, XhandlerAxiosError, IAdapterTokenRevokeVariables>({
    mutationFn: async ({ companyId, token }) => {
      await revokeAdapterToken({
        path: { tenantId, companyId, tokenId: token.id },
        throwOnError: true,
      });
    },
    onSuccess: async (data, variables) => {
      await queryClient.invalidateQueries({
        queryKey: adapterTokenKeys.list(tenantId, variables.companyId),
      });
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};
