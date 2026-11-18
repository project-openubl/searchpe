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
  changeCompanyEnvironment,
  type CompanyView,
  type CreateCompany,
  createCompany,
  getCompany,
  getRoute,
  listCompanies,
  type ListCompaniesData,
  type PageCompanyView,
  type RouteChange,
  type RouteView,
  setRoute,
  type Settings,
  updateCompanySettings,
  uploadCompanyLogo,
  verifyCompany,
} from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";

import type { ListParams } from "../utils/listParams";
import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const CompaniesQueryKey = "companies";

export type ICompanyListParams = ListParams<ListCompaniesData>;

export const companyKeys = {
  all: () => [CompaniesQueryKey] as const,
  list: (tenantId: string) => [...companyKeys.all(), tenantId, "list"] as const,
  detail: (tenantId: string, companyId: string) =>
    [...companyKeys.all(), tenantId, "detail", companyId] as const,
  route: (tenantId: string, companyId: string) =>
    [...companyKeys.detail(tenantId, companyId), "route"] as const,

  listQuery: (tenantId: string, params: ICompanyListParams) =>
    [...companyKeys.list(tenantId), params] as const,
};

type CompanyListOptions = Omit<
  UseQueryOptions<PageCompanyView, XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

export const companiesListQueryOptions = ({
  tenantId,
  params,
  options,
}: {
  tenantId: string;
  params: ICompanyListParams;
  options?: CompanyListOptions;
}) =>
  queryOptions<PageCompanyView, XhandlerAxiosError>({
    placeholderData: keepPreviousData,
    ...options,
    queryKey: companyKeys.listQuery(tenantId, params),
    queryFn: async () =>
      (
        await listCompanies({
          path: { tenantId },
          query: params,
          throwOnError: true,
        })
      ).data,
  });

export const useCompaniesListQuery = ({
  params,
  options,
}: {
  params: ICompanyListParams;
  options?: CompanyListOptions;
}) => {
  const tenantId = useSelectedTenantId();
  return useQuery(companiesListQueryOptions({ tenantId, params, options }));
};

export const companyDetailQueryOptions = ({
  tenantId,
  companyId,
}: {
  tenantId: string;
  companyId: string;
}) =>
  queryOptions<CompanyView, XhandlerAxiosError>({
    queryKey: companyKeys.detail(tenantId, companyId),
    queryFn: async () =>
      (
        await getCompany({
          path: { tenantId, companyId },
          throwOnError: true,
        })
      ).data,
  });

export const useSuspenseCompanyDetailQuery = ({
  companyId,
}: {
  companyId: string;
}) => {
  const tenantId = useSelectedTenantId();
  return useSuspenseQuery(companyDetailQueryOptions({ tenantId, companyId }));
};

/** SUNAT environments a company can be in (`EnvironmentChange`); the spec types it as a string. */
export const SUNAT_ENVS = ["fake", "beta", "production"] as const;

export type SunatEnv = (typeof SUNAT_ENVS)[number];

export const isSunatEnv = (value: string): value is SunatEnv =>
  (SUNAT_ENVS as readonly string[]).includes(value);

/** PDF formats of `Settings.pdf_format`. */
export const PDF_FORMATS = ["a4", "ticket80", "ticket58"] as const;

/** Submission routes of `RouteChange.submission_route`. */
export const SUBMISSION_ROUTES = ["sunat", "ose"] as const;

/** Writes answering the company: refresh its detail and every list page. */
const useCompanyWrite = <TVars>({
  request,
  options,
}: {
  request: (args: {
    tenantId: string;
    variables: TVars;
  }) => Promise<CompanyView>;
  options?: IMutationCallbacks<CompanyView, TVars, XhandlerAxiosError>;
}) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<CompanyView, XhandlerAxiosError, TVars>({
    mutationFn: (variables) => request({ tenantId, variables }),
    onSuccess: async (data, variables) => {
      if (data.id) {
        queryClient.setQueryData(companyKeys.detail(tenantId, data.id), data);
      }
      await queryClient.invalidateQueries({
        queryKey: companyKeys.list(tenantId),
      });
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};

/** `POST …/companies` (admin): answers `201` with the new company. */
export const useCompanyCreateMutation = (
  options?: IMutationCallbacks<CompanyView, CreateCompany, XhandlerAxiosError>,
) =>
  useCompanyWrite<CreateCompany>({
    request: async ({ tenantId, variables }) =>
      (
        await createCompany({
          path: { tenantId },
          body: variables,
          throwOnError: true,
        })
      ).data,
    options,
  });

/** `PUT …/settings` (admin): replaces the company's editable settings. */
export const useCompanySettingsMutation = (
  companyId: string,
  options?: IMutationCallbacks<CompanyView, Settings, XhandlerAxiosError>,
) =>
  useCompanyWrite<Settings>({
    request: async ({ tenantId, variables }) =>
      (
        await updateCompanySettings({
          path: { tenantId, companyId },
          body: variables,
          throwOnError: true,
        })
      ).data,
    options,
  });

/** `PUT …/logo` (admin): the raw PNG or JPEG, its media type as the file's. */
export const useCompanyLogoMutation = (
  companyId: string,
  options?: IMutationCallbacks<CompanyView, File, XhandlerAxiosError>,
) =>
  useCompanyWrite<File>({
    request: async ({ tenantId, variables }) =>
      (
        await uploadCompanyLogo({
          path: { tenantId, companyId },
          body: variables,
          headers: { "Content-Type": variables.type || "image/png" },
          throwOnError: true,
        })
      ).data,
    options,
  });

/** `POST …/environment` (admin): production needs a verified RUC, a certificate and SOL (409). */
export const useCompanyEnvironmentMutation = (
  companyId: string,
  options?: IMutationCallbacks<CompanyView, SunatEnv, XhandlerAxiosError>,
) =>
  useCompanyWrite<SunatEnv>({
    request: async ({ tenantId, variables }) =>
      (
        await changeCompanyEnvironment({
          path: { tenantId, companyId },
          body: { sunat_env: variables },
          throwOnError: true,
        })
      ).data,
    options,
  });

/** `POST …/verify` (admin): proves the RUC with the company's SOL user (DESIGN.md §6). */
export const useCompanyVerifyMutation = (
  companyId: string,
  options?: IMutationCallbacks<CompanyView, void, XhandlerAxiosError>,
) =>
  useCompanyWrite<void>({
    request: async ({ tenantId }) =>
      (
        await verifyCompany({
          path: { tenantId, companyId },
          throwOnError: true,
        })
      ).data,
    options,
  });

export const companyRouteQueryOptions = ({
  tenantId,
  companyId,
}: {
  tenantId: string;
  companyId: string;
}) =>
  queryOptions<RouteView, XhandlerAxiosError>({
    queryKey: companyKeys.route(tenantId, companyId),
    queryFn: async () =>
      (
        await getRoute({
          path: { tenantId, companyId },
          throwOnError: true,
        })
      ).data,
  });

/** `GET …/route`: the submission route and whether SUNAT lists the company as OSE-obligated. */
export const useCompanyRouteQuery = ({ companyId }: { companyId: string }) => {
  const tenantId = useSelectedTenantId();
  return useQuery({
    ...companyRouteQueryOptions({ tenantId, companyId }),
    enabled: !!tenantId && !!companyId,
  });
};

/** `PUT …/route` (admin): SUNAT or a third-party OSE with its credentials. */
export const useCompanyRouteMutation = (
  companyId: string,
  options?: IMutationCallbacks<RouteView, RouteChange, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<RouteView, XhandlerAxiosError, RouteChange>({
    mutationFn: async (body) =>
      (
        await setRoute({
          path: { tenantId, companyId },
          body,
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      queryClient.setQueryData(companyKeys.route(tenantId, companyId), data);
      // The company's `submission_route` changes with it.
      await queryClient.invalidateQueries({
        queryKey: companyKeys.detail(tenantId, companyId),
        exact: true,
      });
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};
