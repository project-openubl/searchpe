import type { UseQueryOptions } from "@tanstack/react-query";
import {
  keepPreviousData,
  queryOptions,
  useQuery,
  useSuspenseQuery,
} from "@tanstack/react-query";

import {
  type CompanyView,
  getV1TenantsByTenantIdCompanies,
  getV1TenantsByTenantIdCompaniesByCompanyId,
  type GetV1TenantsByTenantIdCompaniesData,
  type PageCompanyView,
} from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";

import type { ListParams } from "../utils/listParams";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const CompaniesQueryKey = "companies";

export type ICompanyListParams =
  ListParams<GetV1TenantsByTenantIdCompaniesData>;

export const companyKeys = {
  all: () => [CompaniesQueryKey] as const,
  list: (tenantId: string) => [...companyKeys.all(), tenantId, "list"] as const,
  detail: (tenantId: string, companyId: string) =>
    [...companyKeys.all(), tenantId, "detail", companyId] as const,

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
        await getV1TenantsByTenantIdCompanies({
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
        await getV1TenantsByTenantIdCompaniesByCompanyId({
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
