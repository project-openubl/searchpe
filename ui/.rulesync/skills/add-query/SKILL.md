---
name: add-query
description: Use when adding or changing a TanStack Query read (queryOptions, useQuery, useSuspenseQuery) in client/src/app/queries for xhandler-service or searchpe — a paged list, a detail by id or a status read. Gives the file to put it in, the key/factory/hook templates and the steps to wire it from a route loader.
targets: ["*"]
---

# Add a TanStack query

The constraints are in `.rulesync/rules/queries.md` (read it first), `.rulesync/rules/list-reads.md`
for callers, and `xhandler-api.md` / `searchpe-api.md` for the backend. This skill is the how-to.
The snippets use a made-up xhandler `series` resource; swap in the real generated names.

## Steps

1. **Find the operation** in `client/openapi/<backend>.json` and its generated function and
   types in `src/app/client/<backend>/sdk.gen.ts` / `types.gen.ts` (names come from method +
   path: `listSeries`). Missing? Refresh the spec
   (`npm run openapi:refresh`) or ask for the endpoint; never hand-write the call.
2. **Check it doesn't exist.** A list endpoint has one generic query; pass new `params` or
   `options` instead of adding a hook.
3. **Pick the file:** `queries/<backend>/<family>.ts`, one per domain.
4. **Key:** add to the family's `*Keys` factory (positional args, tenant first for tenant data).
5. **Factory + hook** from the templates, typed `queryOptions<TData, XhandlerAxiosError>` (or
   `SearchpeAxiosError`).
6. **Detail page?** The route loader primes the query from its params; the page reads it with
   `useSuspense*Query` (`.rulesync/rules/routes.md`).
7. **Test** pure helpers next to the module (`<family>.test.ts`).

## Keys

```ts
export const SeriesQueryKey = "series";

export const seriesKeys = {
  all: () => [SeriesQueryKey] as const,
  list: (tenantId: string, companyId: string) =>
    [...seriesKeys.all(), tenantId, companyId, "list"] as const,
  listQuery: (tenantId: string, companyId: string, params: ISeriesListParams) =>
    [...seriesKeys.list(tenantId, companyId), params] as const,
};
```

Invalidate `seriesKeys.list(tenantId, companyId)` after a create.

## List query

```ts
export type ISeriesListParams =
  ListParams<ListSeriesData>;

type SeriesListOptions = Omit<
  UseQueryOptions<PageSeriesView, XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

export const seriesListQueryOptions = ({
  tenantId,
  companyId,
  params,
  options,
}: {
  tenantId: string;
  companyId: string;
  params: ISeriesListParams;
  options?: SeriesListOptions;
}) =>
  queryOptions<PageSeriesView, XhandlerAxiosError>({
    placeholderData: keepPreviousData,
    ...options,
    queryKey: seriesKeys.listQuery(tenantId, companyId, params),
    queryFn: async () =>
      (
        await listSeries({
          path: { tenantId, companyId },
          query: params,
          throwOnError: true,
        })
      ).data,
  });

export const useSeriesListQuery = ({
  companyId,
  params,
  options,
}: {
  companyId: string;
  params: ISeriesListParams;
  options?: SeriesListOptions;
}) => {
  const tenantId = useSelectedTenantId();
  return useQuery(
    seriesListQueryOptions({ tenantId, companyId, params, options }),
  );
};
```

The caller passes `params: { ...toPageParams(pagination), …filters }`.

## Detail query

```ts
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
```

## Public reads

Public pages pass `client: xhandlerPublicClient` to the SDK function (see
`queries/xhandler/public-documents.ts`), so no token is sent.
