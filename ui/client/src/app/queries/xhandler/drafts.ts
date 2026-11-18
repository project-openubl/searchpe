import type { QueryClient, UseQueryOptions } from "@tanstack/react-query";
import {
  keepPreviousData,
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
  useSuspenseQuery,
} from "@tanstack/react-query";

import {
  createDraft,
  deleteDraft,
  type DocumentView,
  type DraftInput,
  type DraftView,
  getDraft,
  issueDraft,
  listDrafts,
  type ListDraftsData,
  type PageDraftView,
  updateDraft,
} from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { getApiErrorKind } from "@app/utils/apiError";
import { isHttpError } from "@app/utils/isHttpError";

import type { ListParams } from "../utils/listParams";
import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";
import { documentKeys } from "./documents";

export const DraftsQueryKey = "drafts";

export type IDraftListParams = ListParams<ListDraftsData>;

/** A draft's statuses (DESIGN.md §11.10); the spec types `status` as a plain string. */
export const DRAFT_STATUSES = ["draft", "issued"] as const;

export type DraftStatus = (typeof DRAFT_STATUSES)[number];

export const isDraftStatus = (value: string): value is DraftStatus =>
  (DRAFT_STATUSES as readonly string[]).includes(value);

/** The `409` kinds of a draft write: issued, or changed since the version the user edited. */
export const DRAFT_CONFLICT_KINDS = ["draft_issued", "draft_changed"] as const;

export type DraftConflictKind = (typeof DRAFT_CONFLICT_KINDS)[number];

/** The conflict a failed draft write reports, if it is one. */
export const getDraftConflictKind = (
  error: unknown,
): DraftConflictKind | undefined => {
  if (!isHttpError({ error, status: 409 })) return undefined;
  const kind = getApiErrorKind(error);
  return (DRAFT_CONFLICT_KINDS as readonly (string | undefined)[]).includes(
    kind,
  )
    ? (kind as DraftConflictKind)
    : undefined;
};

export const draftKeys = {
  all: () => [DraftsQueryKey] as const,
  list: (tenantId: string) => [...draftKeys.all(), tenantId, "list"] as const,
  detail: (tenantId: string, draftId: string) =>
    [...draftKeys.all(), tenantId, "detail", draftId] as const,

  listQuery: (tenantId: string, params: IDraftListParams) =>
    [...draftKeys.list(tenantId), params] as const,
};

type DraftListOptions = Omit<
  UseQueryOptions<PageDraftView, XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

export const draftsListQueryOptions = ({
  tenantId,
  params,
  options,
}: {
  tenantId: string;
  params: IDraftListParams;
  options?: DraftListOptions;
}) =>
  queryOptions<PageDraftView, XhandlerAxiosError>({
    placeholderData: keepPreviousData,
    ...options,
    queryKey: draftKeys.listQuery(tenantId, params),
    queryFn: async () =>
      (
        await listDrafts({
          path: { tenantId },
          query: params,
          throwOnError: true,
        })
      ).data,
  });

export const useDraftsListQuery = ({
  params,
  options,
}: {
  params: IDraftListParams;
  options?: DraftListOptions;
}) => {
  const tenantId = useSelectedTenantId();
  return useQuery(draftsListQueryOptions({ tenantId, params, options }));
};

export const draftDetailQueryOptions = ({
  tenantId,
  draftId,
}: {
  tenantId: string;
  draftId: string;
}) =>
  queryOptions<DraftView, XhandlerAxiosError>({
    queryKey: draftKeys.detail(tenantId, draftId),
    queryFn: async () =>
      (
        await getDraft({
          path: { tenantId, draftId },
          throwOnError: true,
        })
      ).data,
  });

export const useSuspenseDraftDetailQuery = ({
  draftId,
}: {
  draftId: string;
}) => {
  const tenantId = useSelectedTenantId();
  return useSuspenseQuery(draftDetailQueryOptions({ tenantId, draftId }));
};

export interface IDraftCreateVariables {
  companyId: string;
  body: DraftInput;
  /** A fresh UUID per user intent, reused when the same submission is retried. */
  idempotencyKey: string;
}

/** `POST …/companies/{c}/drafts` (operator): `201` with the draft, no number yet. */
export const useDraftCreateMutation = (
  options?: IMutationCallbacks<
    DraftView,
    IDraftCreateVariables,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<DraftView, XhandlerAxiosError, IDraftCreateVariables>({
    mutationFn: async ({ companyId, body, idempotencyKey }) =>
      (
        await createDraft({
          path: { tenantId, companyId },
          body,
          headers: { "Idempotency-Key": idempotencyKey },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      if (data.id) {
        queryClient.setQueryData(draftKeys.detail(tenantId, data.id), data);
      }
      await queryClient.invalidateQueries({
        queryKey: draftKeys.list(tenantId),
      });
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};

/**
 * After a `409` the cached draft is stale (issued, or a newer version): refetch it and the lists,
 * before the caller's `onError` (and a `mutateAsync` rejection), so the page shows what is stored.
 */
const invalidateOnConflict = async ({
  queryClient,
  tenantId,
  draftId,
  error,
}: {
  queryClient: QueryClient;
  tenantId: string;
  draftId: string;
  error: unknown;
}) => {
  if (!getDraftConflictKind(error)) return;
  await Promise.all([
    queryClient.invalidateQueries({
      queryKey: draftKeys.detail(tenantId, draftId),
    }),
    queryClient.invalidateQueries({ queryKey: draftKeys.list(tenantId) }),
  ]);
};

/** `PUT …/drafts/{id}` (operator): replaces the content; `version` makes it a compare-and-set (409). */
export const useDraftUpdateMutation = (
  draftId: string,
  options?: IMutationCallbacks<DraftView, DraftInput, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<DraftView, XhandlerAxiosError, DraftInput>({
    mutationFn: async (body) =>
      (
        await updateDraft({
          path: { tenantId, draftId },
          body,
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      queryClient.setQueryData(draftKeys.detail(tenantId, draftId), data);
      await queryClient.invalidateQueries({
        queryKey: draftKeys.list(tenantId),
      });
      await options?.onSuccess?.(data, variables);
    },
    onError: async (error, variables) => {
      await invalidateOnConflict({ queryClient, tenantId, draftId, error });
      options?.onError?.(error, variables);
    },
  });
};

/** `DELETE …/drafts/{id}` (operator): `204`; an issued draft can't be deleted (409). */
export const useDraftDeleteMutation = (
  options?: IMutationCallbacks<void, DraftView, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<void, XhandlerAxiosError, DraftView>({
    mutationFn: async (draft) => {
      await deleteDraft({
        path: { tenantId, draftId: draft.id ?? "" },
        throwOnError: true,
      });
    },
    onSuccess: async (data, draft) => {
      if (draft.id) {
        queryClient.removeQueries({
          queryKey: draftKeys.detail(tenantId, draft.id),
        });
      }
      await queryClient.invalidateQueries({
        queryKey: draftKeys.list(tenantId),
      });
      await options?.onSuccess?.(data, draft);
    },
    onError: options?.onError,
  });
};

export interface IDraftIssueVariables {
  draftId: string;
  /** The version the user reviewed: a draft changed since then is a 409, not issued. */
  version: number | undefined;
  /** A fresh UUID per user intent, reused when the same submission is retried. */
  idempotencyKey: string;
}

/**
 * `POST …/drafts/{id}/issue` (operator): emits it (`201`, the numbered document); a `422` leaves
 * the draft editable, a `409` means it was issued or changed meanwhile.
 */
export const useDraftIssueMutation = (
  options?: IMutationCallbacks<
    DocumentView,
    IDraftIssueVariables,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<DocumentView, XhandlerAxiosError, IDraftIssueVariables>({
    mutationFn: async ({ draftId, version, idempotencyKey }) =>
      (
        await issueDraft({
          path: { tenantId, draftId },
          query: { version },
          headers: { "Idempotency-Key": idempotencyKey },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      await queryClient.invalidateQueries({
        queryKey: draftKeys.detail(tenantId, variables.draftId),
      });
      await queryClient.invalidateQueries({
        queryKey: draftKeys.list(tenantId),
      });
      // A new document exists: every document list of the tenant is stale.
      void queryClient.invalidateQueries({
        queryKey: documentKeys.list(tenantId),
      });
      await options?.onSuccess?.(data, variables);
    },
    onError: async (error, variables) => {
      await invalidateOnConflict({
        queryClient,
        tenantId,
        draftId: variables.draftId,
        error,
      });
      options?.onError?.(error, variables);
    },
  });
};
