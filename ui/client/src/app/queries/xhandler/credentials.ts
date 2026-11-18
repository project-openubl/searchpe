import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  type CredentialView,
  type GreCredentials,
  listCredentials,
  type LookupCredentials,
  setGreCredentials,
  setLookupCredentials,
  setSireCredentials,
  setSmtpCredentials,
  setSolCredentials,
  type SireCredentials,
  type SmtpCredentials,
  type SolCredentials,
} from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";

import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const CredentialsQueryKey = "credentials";

/** Credential kinds a company stores (DESIGN.md §6); each has its own `PUT …/credentials/{kind}`. */
export const CREDENTIAL_KINDS = [
  "sol",
  "gre",
  "sire",
  "smtp",
  "lookup",
] as const;

export type CredentialKind = (typeof CREDENTIAL_KINDS)[number];

export const isCredentialKind = (value: string): value is CredentialKind =>
  (CREDENTIAL_KINDS as readonly string[]).includes(value);

/** The body each kind's endpoint takes. */
export interface ICredentialBodies {
  sol: SolCredentials;
  gre: GreCredentials;
  sire: SireCredentials;
  smtp: SmtpCredentials;
  lookup: LookupCredentials;
}

/** One write: the kind and its body, so a single mutation serves every kind. */
export type CredentialWrite = {
  [K in CredentialKind]: { kind: K; body: ICredentialBodies[K] };
}[CredentialKind];

export const credentialKeys = {
  all: () => [CredentialsQueryKey] as const,
  list: (tenantId: string, companyId: string) =>
    [...credentialKeys.all(), tenantId, companyId, "list"] as const,
};

export const credentialsListQueryOptions = ({
  tenantId,
  companyId,
}: {
  tenantId: string;
  companyId: string;
}) =>
  queryOptions<CredentialView[], XhandlerAxiosError>({
    queryKey: credentialKeys.list(tenantId, companyId),
    queryFn: async () =>
      (
        await listCredentials({
          path: { tenantId, companyId },
          throwOnError: true,
        })
      ).data,
  });

/**
 * `GET …/credentials`: which kinds are set, with a masked label and the update time. Secrets are
 * write-only: the API never returns them. Unpaged (one row per kind).
 */
export const useCredentialsListQuery = ({
  companyId,
}: {
  companyId: string;
}) => {
  const tenantId = useSelectedTenantId();
  return useQuery({
    ...credentialsListQueryOptions({ tenantId, companyId }),
    enabled: !!tenantId && !!companyId,
  });
};

const writeCredential = async ({
  tenantId,
  companyId,
  write,
}: {
  tenantId: string;
  companyId: string;
  write: CredentialWrite;
}): Promise<CredentialView> => {
  const path = { tenantId, companyId };
  switch (write.kind) {
    case "sol":
      return (
        await setSolCredentials({ path, body: write.body, throwOnError: true })
      ).data;
    case "gre":
      return (
        await setGreCredentials({ path, body: write.body, throwOnError: true })
      ).data;
    case "sire":
      return (
        await setSireCredentials({ path, body: write.body, throwOnError: true })
      ).data;
    case "smtp":
      return (
        await setSmtpCredentials({ path, body: write.body, throwOnError: true })
      ).data;
    case "lookup":
      return (
        await setLookupCredentials({
          path,
          body: write.body,
          throwOnError: true,
        })
      ).data;
  }
};

/** `PUT …/credentials/{kind}` (admin): replaces the kind's credential. */
export const useCredentialSetMutation = (
  companyId: string,
  options?: IMutationCallbacks<
    CredentialView,
    CredentialWrite,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<CredentialView, XhandlerAxiosError, CredentialWrite>({
    // The variables carry the secret: drop the mutation from the cache once unobserved.
    gcTime: 0,
    mutationFn: (write) => writeCredential({ tenantId, companyId, write }),
    onSuccess: async (data, variables) => {
      await queryClient.invalidateQueries({
        queryKey: credentialKeys.list(tenantId, companyId),
      });
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};
