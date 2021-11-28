import { UseQueryResult, useQuery } from "react-query";

import { CoreClusterResource, CoreClusterResourceKind } from "api-client";
import { Contribuyente, PageRepresentation } from "api/models";

import { useClientInstance } from "shared/hooks";
import { ApiClientError } from "api-client/types";

const resource = new CoreClusterResource(CoreClusterResourceKind.Contribuyente);

export interface IContribuyentesParams {
  filterText?: string;
  offset?: number;
  limit?: number;
  sort_by?: string;
}

export class IContribuyentesParamsBuilder {
  private _params: IContribuyentesParams = {};

  public withFilterText(filterText: string) {
    this._params.filterText = filterText;
    return this;
  }
  public withPagination(pagination: { page: number; perPage: number }) {
    this._params.offset = (pagination.page - 1) * pagination.perPage;
    this._params.limit = pagination.perPage;
    return this;
  }
  public withSorting(sorting?: {
    orderBy: string | undefined;
    orderDirection: "asc" | "desc";
  }) {
    if (sorting) {
      this._params.sort_by = `${sorting.orderBy}:${sorting.orderDirection}`;
    }
    return this;
  }

  public build() {
    return this._params;
  }
}

export const useContribuyentesQuery = (
  params: IContribuyentesParams
): UseQueryResult<PageRepresentation<Contribuyente>, ApiClientError> => {
  const client = useClientInstance();
  const result = useQuery<PageRepresentation<Contribuyente>, ApiClientError>(
    ["contribuyentes", params],
    async (): Promise<PageRepresentation<Contribuyente>> => {
      return (await client.list(resource, params)).data;
    },
    { keepPreviousData: true }
  );
  return result;
};

export const useContribuyenteQuery = (
  numeroDocumento: string | null
): UseQueryResult<Contribuyente, ApiClientError> => {
  const client = useClientInstance();
  const result = useQuery<Contribuyente, ApiClientError>(
    ["contribuyente", numeroDocumento],
    async (): Promise<Contribuyente> => {
      return (await client.get(resource, numeroDocumento || "")).data;
    },
    { enabled: !!numeroDocumento, retry: false }
  );
  return result;
};
