import type { UseQueryOptions } from "@tanstack/react-query";
import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  addSenderDomain,
  type DomainView,
  listEmailSuppressions,
  listSenderDomains,
  removeEmailSuppression,
  type Suppression,
  verifySenderDomain,
} from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";

import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const EmailQueryKey = "email";

export const emailKeys = {
  all: () => [EmailQueryKey] as const,
  senderDomains: (tenantId: string) =>
    [...emailKeys.all(), tenantId, "sender-domains"] as const,
  suppressions: (tenantId: string) =>
    [...emailKeys.all(), tenantId, "suppressions"] as const,
};

type SenderDomainListOptions = Omit<
  UseQueryOptions<DomainView[], XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

/** `GET /sender-domains`: unpaged by the API (a tenant has a handful). */
export const senderDomainsListQueryOptions = ({
  tenantId,
  options,
}: {
  tenantId: string;
  options?: SenderDomainListOptions;
}) =>
  queryOptions<DomainView[], XhandlerAxiosError>({
    ...options,
    queryKey: emailKeys.senderDomains(tenantId),
    queryFn: async () =>
      (await listSenderDomains({ path: { tenantId }, throwOnError: true }))
        .data,
  });

export const useSenderDomainsListQuery = ({
  options,
}: {
  options?: SenderDomainListOptions;
}) => {
  const tenantId = useSelectedTenantId();
  return useQuery(senderDomainsListQueryOptions({ tenantId, options }));
};

type SuppressionListOptions = Omit<
  UseQueryOptions<Suppression[], XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

/** `GET /email-suppressions`: addresses that bounced or complained, unpaged by the API. */
export const emailSuppressionsListQueryOptions = ({
  tenantId,
  options,
}: {
  tenantId: string;
  options?: SuppressionListOptions;
}) =>
  queryOptions<Suppression[], XhandlerAxiosError>({
    ...options,
    queryKey: emailKeys.suppressions(tenantId),
    queryFn: async () =>
      (
        await listEmailSuppressions({
          path: { tenantId },
          throwOnError: true,
        })
      ).data,
  });

export const useEmailSuppressionsListQuery = ({
  options,
}: {
  options?: SuppressionListOptions;
}) => {
  const tenantId = useSelectedTenantId();
  return useQuery(emailSuppressionsListQueryOptions({ tenantId, options }));
};

export interface ISenderDomainAddVariables {
  domain: string;
  idempotencyKey: string;
}

/** `POST /sender-domains` (admin): `201` with the TXT record to publish (or the existing one). */
export const useSenderDomainAddMutation = (
  options?: IMutationCallbacks<
    DomainView,
    ISenderDomainAddVariables,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<DomainView, XhandlerAxiosError, ISenderDomainAddVariables>(
    {
      mutationFn: async ({ domain, idempotencyKey }) =>
        (
          await addSenderDomain({
            path: { tenantId },
            body: { domain },
            headers: { "Idempotency-Key": idempotencyKey },
            throwOnError: true,
          })
        ).data,
      onSuccess: async (data, variables) => {
        await queryClient.invalidateQueries({
          queryKey: emailKeys.senderDomains(tenantId),
        });
        await options?.onSuccess?.(data, variables);
      },
      onError: options?.onError,
    },
  );
};

/** `POST /sender-domains/{domain}/verify` (admin): looks the TXT record up now. */
export const useSenderDomainVerifyMutation = (
  options?: IMutationCallbacks<DomainView, DomainView, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<DomainView, XhandlerAxiosError, DomainView>({
    mutationFn: async (domain) =>
      (
        await verifySenderDomain({
          path: { tenantId, domain: domain.domain ?? "" },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, domain) => {
      await queryClient.invalidateQueries({
        queryKey: emailKeys.senderDomains(tenantId),
      });
      await options?.onSuccess?.(data, domain);
    },
    onError: options?.onError,
  });
};

/** `DELETE /email-suppressions/{address}` (admin): mail goes to the address again. */
export const useEmailSuppressionRemoveMutation = (
  options?: IMutationCallbacks<void, Suppression, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<void, XhandlerAxiosError, Suppression>({
    mutationFn: async (suppression) => {
      await removeEmailSuppression({
        path: { tenantId, address: suppression.address ?? "" },
        throwOnError: true,
      });
    },
    onSuccess: async (data, suppression) => {
      await queryClient.invalidateQueries({
        queryKey: emailKeys.suppressions(tenantId),
      });
      await options?.onSuccess?.(data, suppression);
    },
    onError: options?.onError,
  });
};
