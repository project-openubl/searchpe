import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  type Authorization,
  type AuthorizationView,
  authorizePse,
  type CompanyView,
  type EmissionMode,
  listPseAuthorizations,
  revokePseAuthorization,
  type RevokePseAuthorizationResponse,
  setEmissionMode,
} from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";

import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";
import { companyKeys } from "./companies";

export const PseQueryKey = "pse";

/** A company's emission modes (`PseResource.EmissionMode`); the spec types it as a string. */
export const EMISSION_MODES = ["software", "pse"] as const;

export type EmissionModeValue = (typeof EMISSION_MODES)[number];

export const isEmissionMode = (value: string): value is EmissionModeValue =>
  (EMISSION_MODES as readonly string[]).includes(value);

export const pseKeys = {
  all: () => [PseQueryKey] as const,
  authorizations: (tenantId: string, companyId: string) =>
    [...pseKeys.all(), tenantId, companyId, "authorizations"] as const,
};

/** The authorization in force: registered and not revoked (approval may still be pending). */
export const activeAuthorization = (
  authorizations: AuthorizationView[] | undefined,
): AuthorizationView | undefined =>
  authorizations?.find((authorization) => !authorization.revoked_at);

/**
 * `GET …/companies/{id}/pse`: the company's PSE authorizations, newest first. Unpaged by the API
 * (a handful per company), so it is read whole.
 */
export const pseAuthorizationsQueryOptions = ({
  tenantId,
  companyId,
}: {
  tenantId: string;
  companyId: string;
}) =>
  queryOptions<AuthorizationView[], XhandlerAxiosError>({
    queryKey: pseKeys.authorizations(tenantId, companyId),
    queryFn: async () =>
      (
        await listPseAuthorizations({
          path: { tenantId, companyId },
          throwOnError: true,
        })
      ).data,
  });

export const usePseAuthorizationsQuery = ({
  companyId,
}: {
  companyId: string;
}) => {
  const tenantId = useSelectedTenantId();
  return useQuery({
    ...pseAuthorizationsQueryOptions({ tenantId, companyId }),
    enabled: !!tenantId && !!companyId,
  });
};

/** Both writes change the company's emission mode or the authorizations it depends on. */
const useInvalidatePse = (companyId: string) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return async () => {
    await queryClient.invalidateQueries({
      queryKey: pseKeys.authorizations(tenantId, companyId),
    });
    await queryClient.invalidateQueries({
      queryKey: companyKeys.detail(tenantId, companyId),
    });
    void queryClient.invalidateQueries({
      queryKey: companyKeys.list(tenantId),
    });
  };
};

/** `POST …/pse/authorization` (admin): registers the SOL authorization with its evidence. */
export const useAuthorizePseMutation = (
  companyId: string,
  options?: IMutationCallbacks<
    AuthorizationView,
    Authorization,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  const invalidate = useInvalidatePse(companyId);
  return useMutation<AuthorizationView, XhandlerAxiosError, Authorization>({
    mutationFn: async (body) =>
      (
        await authorizePse({
          path: { tenantId, companyId },
          body,
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, body) => {
      await invalidate();
      await options?.onSuccess?.(data, body);
    },
    onError: options?.onError,
  });
};

/** `DELETE …/pse/authorization` (admin): PSE stops and the company goes back to software. */
export const useRevokePseMutation = (
  companyId: string,
  options?: IMutationCallbacks<
    RevokePseAuthorizationResponse,
    void,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  const invalidate = useInvalidatePse(companyId);
  return useMutation<RevokePseAuthorizationResponse, XhandlerAxiosError, void>({
    mutationFn: async () =>
      (
        await revokePseAuthorization({
          path: { tenantId, companyId },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      await invalidate();
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};

/** `PUT …/pse/mode` (admin): answers the company with its new mode. */
export const useSetEmissionModeMutation = (
  companyId: string,
  options?: IMutationCallbacks<CompanyView, EmissionMode, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  const invalidate = useInvalidatePse(companyId);
  return useMutation<CompanyView, XhandlerAxiosError, EmissionMode>({
    mutationFn: async (body) =>
      (
        await setEmissionMode({
          path: { tenantId, companyId },
          body,
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, body) => {
      queryClient.setQueryData(companyKeys.detail(tenantId, companyId), data);
      await invalidate();
      await options?.onSuccess?.(data, body);
    },
    onError: options?.onError,
  });
};
