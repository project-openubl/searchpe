import type { UseQueryOptions } from "@tanstack/react-query";
import {
  keepPreviousData,
  queryOptions,
  useQuery,
  useSuspenseQuery,
} from "@tanstack/react-query";

import {
  type ContribuyenteDto,
  getContribuyentes,
  getContribuyentesByNumeroDocumento,
  type GetContribuyentesData,
  type SearchResultDtoContribuyenteDto,
} from "@app/client/searchpe";

import type { ListParams } from "../utils/listParams";
import type { SearchpeAxiosError } from "../utils/openapiErrorTypes";

export const ContribuyentesQueryKey = "contribuyentes";

export type IContribuyenteListParams = ListParams<GetContribuyentesData>;

/** `tipoPersona` filter values (searchpe's `TipoPersona`); the spec types it as a string. */
export const TIPOS_PERSONA = ["NATURAL", "JURIDICA"] as const;

export type TipoPersona = (typeof TIPOS_PERSONA)[number];

export const isTipoPersona = (value: string): value is TipoPersona =>
  (TIPOS_PERSONA as readonly string[]).includes(value);

/** The highest `offset` the list endpoint accepts (`@Max(9_000)`). */
export const CONTRIBUYENTES_MAX_OFFSET = 9000;

/** A RUC has 11 digits and a DNI 8; anything else is never found. */
export const isNumeroDocumento = (value: string): boolean =>
  /^(\d{8}|\d{11})$/.test(value);

export const contribuyenteKeys = {
  all: () => [ContribuyentesQueryKey] as const,
  list: () => [...contribuyenteKeys.all(), "list"] as const,
  detail: (numeroDocumento: string) =>
    [...contribuyenteKeys.all(), "detail", numeroDocumento] as const,

  listQuery: (params: IContribuyenteListParams) =>
    [...contribuyenteKeys.list(), params] as const,
};

type ContribuyenteListOptions = Omit<
  UseQueryOptions<SearchResultDtoContribuyenteDto, SearchpeAxiosError>,
  "queryKey" | "queryFn"
>;

/**
 * `GET /api/contribuyentes` over the active padrón version. The server may disable it
 * (`searchpe.allow.advancedSearch=false`), answering `400`.
 */
export const contribuyentesListQueryOptions = ({
  params,
  options,
}: {
  params: IContribuyenteListParams;
  options?: ContribuyenteListOptions;
}) =>
  queryOptions<SearchResultDtoContribuyenteDto, SearchpeAxiosError>({
    placeholderData: keepPreviousData,
    // A disabled search or a bad filter won't change on retry.
    retry: false,
    ...options,
    queryKey: contribuyenteKeys.listQuery(params),
    queryFn: async () =>
      (await getContribuyentes({ query: params, throwOnError: true })).data,
  });

export const useContribuyentesListQuery = ({
  params,
  options,
}: {
  params: IContribuyenteListParams;
  options?: ContribuyenteListOptions;
}) => useQuery(contribuyentesListQueryOptions({ params, options }));

/** `GET /api/contribuyentes/{numeroDocumento}` (RUC or DNI); `404` when it isn't in the padrón. */
export const contribuyenteDetailQueryOptions = ({
  numeroDocumento,
}: {
  numeroDocumento: string;
}) =>
  queryOptions<ContribuyenteDto, SearchpeAxiosError>({
    queryKey: contribuyenteKeys.detail(numeroDocumento),
    queryFn: async () =>
      (
        await getContribuyentesByNumeroDocumento({
          path: { numeroDocumento },
          throwOnError: true,
        })
      ).data,
    retry: false,
  });

export const useContribuyenteDetailQuery = ({
  numeroDocumento,
}: {
  numeroDocumento: string;
}) =>
  useQuery({
    ...contribuyenteDetailQueryOptions({ numeroDocumento }),
    enabled: isNumeroDocumento(numeroDocumento),
  });

export const useSuspenseContribuyenteDetailQuery = ({
  numeroDocumento,
}: {
  numeroDocumento: string;
}) => useSuspenseQuery(contribuyenteDetailQueryOptions({ numeroDocumento }));
