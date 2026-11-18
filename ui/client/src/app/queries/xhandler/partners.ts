import type { UseQueryOptions } from "@tanstack/react-query";
import {
  keepPreviousData,
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  type Branding,
  type CreatePartnerTenant,
  createPartnerTenant,
  listMyPartners,
  listPartnerTenants,
  type ListPartnerTenantsData,
  type PartnerTenant,
  type PartnerView,
  setPartnerBranding,
  type SetPartnerBrandingResponse,
  type TenantUsage,
} from "@app/client/xhandler";

import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";
import { tenantKeys } from "./tenants";

export const PartnersQueryKey = "partners";

/** `month` (`AAAA-MM`); without it the server answers the current month in Lima. */
export type IPartnerTenantsParams = NonNullable<
  ListPartnerTenantsData["query"]
>;

/** The roles of a partner membership (DESIGN.md §1.2): admins manage, viewers look. */
export const PARTNER_ROLES = ["admin", "viewer"] as const;

export type PartnerRole = (typeof PARTNER_ROLES)[number];

export const PARTNER_ROLE_LABELS: Record<PartnerRole, string> = {
  admin: "Administrador",
  viewer: "Lector",
};

export const isPartnerRole = (value: string): value is PartnerRole =>
  (PARTNER_ROLES as readonly string[]).includes(value);

export const partnerKeys = {
  all: () => [PartnersQueryKey] as const,
  mine: () => [...partnerKeys.all(), "mine"] as const,
  tenants: (partnerId: string) =>
    [...partnerKeys.all(), partnerId, "tenants"] as const,

  tenantsQuery: (partnerId: string, params: IPartnerTenantsParams) =>
    [...partnerKeys.tenants(partnerId), params] as const,
};

/**
 * `GET /api/v1/partners`: the partners the user belongs to, with their role. Unpaged by the API
 * (one row per membership), so it is read whole.
 */
export const myPartnersQueryOptions = queryOptions<
  PartnerView[],
  XhandlerAxiosError
>({
  queryKey: partnerKeys.mine(),
  queryFn: async () => (await listMyPartners({ throwOnError: true })).data,
  staleTime: 60 * 1000,
});

/** `options.enabled` lets the shell skip it while xhandler-service is down. */
export const useMyPartnersQuery = ({
  options,
}: {
  options?: Omit<
    UseQueryOptions<PartnerView[], XhandlerAxiosError>,
    "queryKey" | "queryFn"
  >;
} = {}) => useQuery({ ...options, ...myPartnersQueryOptions });

type PartnerTenantsOptions = Omit<
  UseQueryOptions<TenantUsage[], XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

/**
 * `GET /api/v1/partners/{id}/tenants`: the partner's tenants with their documents in a month.
 * Unpaged by the API, so it is read whole and paged on screen. An unknown partner is a 404.
 */
export const partnerTenantsQueryOptions = ({
  partnerId,
  params,
  options,
}: {
  partnerId: string;
  params: IPartnerTenantsParams;
  options?: PartnerTenantsOptions;
}) =>
  queryOptions<TenantUsage[], XhandlerAxiosError>({
    placeholderData: keepPreviousData,
    ...options,
    queryKey: partnerKeys.tenantsQuery(partnerId, params),
    queryFn: async () =>
      (
        await listPartnerTenants({
          path: { partnerId },
          query: params,
          throwOnError: true,
        })
      ).data,
  });

export const usePartnerTenantsQuery = ({
  partnerId,
  params,
  options,
}: {
  partnerId: string;
  params: IPartnerTenantsParams;
  options?: PartnerTenantsOptions;
}) => useQuery(partnerTenantsQueryOptions({ partnerId, params, options }));

/** `POST /api/v1/partners/{id}/tenants` (partner admins): `201` with the new tenant. */
export const useCreatePartnerTenantMutation = (
  partnerId: string,
  options?: IMutationCallbacks<
    PartnerTenant,
    CreatePartnerTenant,
    XhandlerAxiosError
  >,
) => {
  const queryClient = useQueryClient();
  return useMutation<PartnerTenant, XhandlerAxiosError, CreatePartnerTenant>({
    mutationFn: async (body) =>
      (
        await createPartnerTenant({
          path: { partnerId },
          body,
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, body) => {
      await queryClient.invalidateQueries({
        queryKey: partnerKeys.tenants(partnerId),
      });
      // The partner admin acts as admin of the new tenant: its memberships may change.
      void queryClient.invalidateQueries({ queryKey: tenantKeys.mine() });
      await options?.onSuccess?.(data, body);
    },
    onError: options?.onError,
  });
};

/** `PUT /api/v1/partners/{id}/branding` (partner admins): replaces the white-label branding. */
export const useSetPartnerBrandingMutation = (
  partnerId: string,
  options?: IMutationCallbacks<
    SetPartnerBrandingResponse,
    Branding,
    XhandlerAxiosError
  >,
) =>
  useMutation<SetPartnerBrandingResponse, XhandlerAxiosError, Branding>({
    mutationFn: async (body) =>
      (
        await setPartnerBranding({
          path: { partnerId },
          body,
          throwOnError: true,
        })
      ).data,
    // Nothing cached reads the branding (the API has no GET for it).
    onSuccess: async (data, body) => {
      await options?.onSuccess?.(data, body);
    },
    onError: options?.onError,
  });
