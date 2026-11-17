---
name: add-list-state
description: Use when a list page gets filters, sort or pagination kept in the URL (or another storage) — define the list's state once, read each piece with usePersistentState, and declare it on the route with listSearch.
---
# Add list state

The constraints are in `.rulesync/rules/list-state.md`; `docs/adr/0008-view-state-persistence.md`
is the decision.

## Steps

1. **Define** the state next to the page, `<list>State.ts`:

```ts
export const JOB_LIST_STATE = defineListState({
  pagination: PAGINATION_STATE,
  filters: definePersistentState({
    key: "filters",
    schema: z.object({
      estado: z.array(schemaFromGuard(isJobState)),
      nombre: z.string(),
    }),
    defaultValue: { estado: [], nombre: "" },
  }),
});
```

Sort only by fields the endpoint sorts by (`defineSortState({ columnKeys: ["nombre"], … })`).
A pagination-only list uses `PAGINATION_LIST_STATE` and needs no file.

2. **Route**: `const { validateSearch, search } = listSearch(JOB_LIST_STATE);` and pass both.
3. **Page**: one `usePersistentState({ ...JOB_LIST_STATE.<piece>, storage: "url" })` per piece;
   every filter, sort or page-size change also sets page 1; the toolbar reads `values={filters}`
   and clears with the definition's `defaultValue`.
4. **Params**: `{ ...toPageParams(pagination), state: filters.estado[0], name: debounced || undefined }`.
5. **Type check**: extend nothing; `utils/listState.typecheck.ts` covers `listSearch`.
