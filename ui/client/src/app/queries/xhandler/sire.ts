import type { UseQueryOptions } from "@tanstack/react-query";
import {
  keepPreviousData,
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  downloadSireFile,
  getSirePeriod,
  listSirePeriods,
  type ListSirePeriodsData,
  type PageSirePeriodSummary,
  type ReplaceRequest,
  replaceSireProposal,
  requestSireProposal,
  type SirePeriodView,
} from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";

import { downloadFile } from "../utils/fileDownload";
import type { ListParams } from "../utils/listParams";
import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";
import { jobKeys } from "./jobs";

export const SireQueryKey = "sire";

/** SIRE registers (DESIGN.md §11.7): `rvie` sales, `rce` purchases. */
export const SIRE_BOOKS = ["rvie", "rce"] as const;

export type SireBook = (typeof SIRE_BOOKS)[number];

export const SIRE_BOOK_LABELS: Record<SireBook, string> = {
  rvie: "Ventas (RVIE)",
  rce: "Compras (RCE)",
};

export const isSireBook = (value: string): value is SireBook =>
  (SIRE_BOOKS as readonly string[]).includes(value);

/** A `sire_period`'s states (DESIGN.md §11.7); the spec types `status` as a plain string. */
export const SIRE_STATUSES = [
  "requested",
  "proposal_ready",
  "submitting",
  "submitted",
  "accepted",
  "error",
  "credentials",
  "unknown",
] as const;

export type SireStatus = (typeof SIRE_STATUSES)[number];

export const isSireStatus = (value: string): value is SireStatus =>
  (SIRE_STATUSES as readonly string[]).includes(value);

/** States a job or the `sire.poll` schedule still moves on by itself: the page polls them. */
export const isSireStatusInProgress = (status: string | undefined): boolean =>
  status === "requested" || status === "submitting" || status === "submitted";

/** The periods (`AAAAMM`) SIRE takes. */
export const isSirePeriod = (value: string): boolean =>
  /^\d{4}(0[1-9]|1[0-2])$/.test(value);

/** What a replacement can be built against: the server refuses the others with a 409. */
export const isSireReplaceable = (period: SirePeriodView): boolean =>
  period.reconciliation != null &&
  (period.status === "proposal_ready" ||
    period.status === "error" ||
    period.status === "unknown");

/** The stored reconciliation (`RegisterFormats.reconcile`); the spec types it as a JSON node. */
export interface ISireReconciliation {
  sunat_count?: number;
  local_count?: number;
  matches?: boolean;
  missing_in_sunat?: string[];
  missing_locally?: string[];
  differing?: { key?: string; sunat_total?: string; local_total?: string }[];
}

export const sireReconciliation = (
  period: SirePeriodView,
): ISireReconciliation | undefined =>
  period.reconciliation == null
    ? undefined
    : (period.reconciliation as ISireReconciliation);

/** The files SUNAT exchanged for a period (`…/files/{kind}`). */
export type SireFileKind = "proposal" | "replacement";

export interface ISirePeriodPath {
  companyId: string;
  book: SireBook;
  period: string;
}

/** `book` (`rvie`, `rce`) or neither for both. */
export type ISirePeriodListParams = ListParams<ListSirePeriodsData>;

export const sireKeys = {
  all: () => [SireQueryKey] as const,
  company: (tenantId: string, companyId: string) =>
    [...sireKeys.all(), tenantId, companyId] as const,
  period: (tenantId: string, { companyId, book, period }: ISirePeriodPath) =>
    [...sireKeys.company(tenantId, companyId), book, period] as const,
  list: (tenantId: string, companyId: string) =>
    [...sireKeys.company(tenantId, companyId), "list"] as const,

  listQuery: (
    tenantId: string,
    companyId: string,
    params: ISirePeriodListParams,
  ) => [...sireKeys.list(tenantId, companyId), params] as const,
};

type SirePeriodListOptions = Omit<
  UseQueryOptions<PageSirePeriodSummary, XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

/**
 * `GET …/sire`: the company's requested periods in its current environment, latest first,
 * without their reconciliation (read a period for it).
 */
export const sirePeriodsListQueryOptions = ({
  tenantId,
  companyId,
  params,
  options,
}: {
  tenantId: string;
  companyId: string;
  params: ISirePeriodListParams;
  options?: SirePeriodListOptions;
}) =>
  queryOptions<PageSirePeriodSummary, XhandlerAxiosError>({
    placeholderData: keepPreviousData,
    ...options,
    queryKey: sireKeys.listQuery(tenantId, companyId, params),
    queryFn: async () =>
      (
        await listSirePeriods({
          path: { tenantId, companyId },
          query: params,
          throwOnError: true,
        })
      ).data,
  });

export const useSirePeriodsListQuery = ({
  companyId,
  params,
  options,
}: {
  companyId: string;
  params: ISirePeriodListParams;
  options?: SirePeriodListOptions;
}) => {
  const tenantId = useSelectedTenantId();
  return useQuery(
    sirePeriodsListQueryOptions({ tenantId, companyId, params, options }),
  );
};

type SirePeriodOptions = Omit<
  UseQueryOptions<SirePeriodView, XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

/**
 * `GET …/sire/{book}/{period}`. A period nobody has requested yet is a `404`, which the page
 * shows as "not requested"; `GET …/sire` lists the requested ones.
 */
export const sirePeriodQueryOptions = ({
  tenantId,
  path,
  options,
}: {
  tenantId: string;
  path: ISirePeriodPath;
  options?: SirePeriodOptions;
}) =>
  queryOptions<SirePeriodView, XhandlerAxiosError>({
    ...options,
    queryKey: sireKeys.period(tenantId, path),
    queryFn: async () =>
      (
        await getSirePeriod({
          path: { tenantId, ...path },
          throwOnError: true,
        })
      ).data,
  });

export const useSirePeriodQuery = ({
  path,
  options,
}: {
  path: ISirePeriodPath;
  options?: SirePeriodOptions;
}) => {
  const tenantId = useSelectedTenantId();
  return useQuery({
    ...sirePeriodQueryOptions({ tenantId, path, options }),
    enabled: !!tenantId && isSireBook(path.book) && isSirePeriod(path.period),
  });
};

/** Both writes answer `202` with the period; a job carries on, so the job lists change too. */
const useSireWriteSuccess = () => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return async (data: SirePeriodView, path: ISirePeriodPath) => {
    queryClient.setQueryData(sireKeys.period(tenantId, path), data);
    void queryClient.invalidateQueries({
      queryKey: sireKeys.list(tenantId, path.companyId),
    });
    void queryClient.invalidateQueries({ queryKey: jobKeys.list(tenantId) });
  };
};

/** `POST …/proposal` (operator; admin to start over a reviewed or accepted period). */
export const useRequestSireProposalMutation = (
  options?: IMutationCallbacks<
    SirePeriodView,
    ISirePeriodPath,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  const onWrite = useSireWriteSuccess();
  return useMutation<SirePeriodView, XhandlerAxiosError, ISirePeriodPath>({
    mutationFn: async (path) =>
      (
        await requestSireProposal({
          path: { tenantId, ...path },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, path) => {
      await onWrite(data, path);
      await options?.onSuccess?.(data, path);
    },
    onError: options?.onError,
  });
};

export interface ISireReplaceVariables extends ISirePeriodPath {
  body: ReplaceRequest;
}

/** `POST …/replace` (admin): submits the stored rows as SUNAT's replacement. */
export const useReplaceSireProposalMutation = (
  options?: IMutationCallbacks<
    SirePeriodView,
    ISireReplaceVariables,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  const onWrite = useSireWriteSuccess();
  return useMutation<SirePeriodView, XhandlerAxiosError, ISireReplaceVariables>(
    {
      mutationFn: async ({ body, ...path }) =>
        (
          await replaceSireProposal({
            path: { tenantId, ...path },
            body,
            throwOnError: true,
          })
        ).data,
      onSuccess: async (data, variables) => {
        const { companyId, book, period } = variables;
        await onWrite(data, { companyId, book, period });
        await options?.onSuccess?.(data, variables);
      },
      onError: options?.onError,
    },
  );
};

export interface ISireFileVariables extends ISirePeriodPath {
  kind: SireFileKind;
}

/** `GET …/files/{kind}`: saves SUNAT's ZIP when the user asks for it (each read is audited). */
export const useDownloadSireFileMutation = (
  options?: IMutationCallbacks<void, ISireFileVariables, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  return useMutation<void, XhandlerAxiosError, ISireFileVariables>({
    mutationFn: (variables) =>
      downloadFile({
        request: () =>
          downloadSireFile({
            path: { tenantId, ...variables },
            throwOnError: true,
          }),
        fallbackName: `sire-${variables.book}-${variables.period}-${variables.kind}.zip`,
      }),
    onSuccess: async (data, variables) => {
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};
