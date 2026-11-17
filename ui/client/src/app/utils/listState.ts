import { stripSearchParams } from "@tanstack/react-router";
import { z } from "zod";

import { parseFields } from "@app/hooks/persistentStateCodec";
import {
  definePersistentState,
  type IPersistentStateDefinition,
  type PersistentStateSchema,
} from "@app/hooks/usePersistentState";

/** Page sizes every list offers (`ListPagination`). */
export const PER_PAGE_VALUES = [10, 20] as const;

export type PerPage = (typeof PER_PAGE_VALUES)[number];

export const isPerPage = (value: number): value is PerPage =>
  (PER_PAGE_VALUES as readonly number[]).includes(value);

/** A zod schema accepting the strings a type guard accepts, typed as what it narrows to (an API enum). */
export const schemaFromGuard = <T extends string>(
  guard: (value: string) => value is T,
) => z.custom<T>((value) => typeof value === "string" && guard(value));

/** A list's page and page size; keep it with `usePersistentState({ ...PAGINATION_STATE, storage })`. */
export const PAGINATION_STATE = definePersistentState({
  key: "pagination",
  schema: z.object({
    page: z.coerce.number().int().min(1),
    perPage: z.coerce.number().pipe(z.literal(PER_PAGE_VALUES)),
  }),
  defaultValue: { page: 1, perPage: 10 },
});

export type ListPaginationState = z.output<typeof PAGINATION_STATE.schema>;

/**
 * The params a page of a list asks for: `limit`/`offset` from its pagination state (list-reads
 * rule).
 */
export const toPageParams = (
  pagination: ListPaginationState,
): { limit: number; offset: number } => ({
  limit: pagination.perPage,
  offset: (pagination.page - 1) * pagination.perPage,
});

/**
 * xhandler-service pages answer no total (`{ items, limit, offset }`): count what was seen,
 * plus one more page when this one came back full, so the pager offers "next" only then.
 */
export const estimateItemCount = ({
  pagination,
  pageSize,
}: {
  pagination: ListPaginationState;
  pageSize: number;
}): number =>
  (pagination.page - 1) * pagination.perPage +
  pageSize +
  (pageSize === pagination.perPage ? 1 : 0);

/** A list's sort over its sortable column keys (the endpoint's `ordering` enum); call it at module scope. */
export const defineSortState = <K extends string>({
  columnKeys,
  defaultValue,
}: {
  columnKeys: readonly [K, ...K[]];
  // NoInfer: the keys come from columnKeys only, so a default on another column is an error.
  defaultValue: { sortBy: NoInfer<K>; direction: "asc" | "desc" };
}) =>
  definePersistentState({
    key: "sort",
    schema: z.object({
      sortBy: z.enum(columnKeys),
      direction: z.enum(["asc", "desc"]),
    }),
    defaultValue,
  });

type AnyDefinition = IPersistentStateDefinition<PersistentStateSchema>;

/** A list's whole state, one definition per piece: `{ pagination, sort, filters }`. */
export type ListState = Record<string, AnyDefinition>;

/** Checks a list's state where it's written and keeps each piece's exact type. */
export const defineListState = <L extends ListState>(list: L): L => list;

/** The state of a list that only pages (no sort or filters): its route passes it to `listSearch`. */
export const PAGINATION_LIST_STATE = defineListState({
  pagination: PAGINATION_STATE,
});

type UnionToIntersection<U> = (
  U extends unknown ? (arg: U) => void : never
) extends (arg: infer I) => void
  ? I
  : never;

/** The query params of a list: every field of every piece, all optional. */
export type ListSearch<L extends ListState> = Partial<
  UnionToIntersection<z.output<L[keyof L]["schema"]>>
>;

/**
 * Route options declaring a list's state, so links into it are type-checked and defaults stay out
 * of the URL: `const { validateSearch, search } = listSearch(REMOTE_LIST_STATE)`, then pass both.
 */
export const listSearch = <L extends ListState>(list: L) => {
  const pieces = Object.values(list);
  const merge = (values: object[]) =>
    Object.assign({}, ...values) as ListSearch<L>;
  return {
    // Only declares the params: the page still reads them with usePersistentState.
    validateSearch: (search: ListSearch<L>) =>
      merge(pieces.map((piece) => parseFields({ fields: search, ...piece }))),
    search: {
      middlewares: [
        stripSearchParams<ListSearch<L>>(
          merge(pieces.map((piece) => piece.defaultValue)),
        ),
      ],
    },
  };
};
