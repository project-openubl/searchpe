---
name: list-state
description: "Apply when a list's filters, sort or pagination are kept or changed, or a route reads search params: each piece is one usePersistentState call whose storage (url, session, local, state) can change without touching anything else, and reused lists are namespaced by the screen."
root: false
targets: ["claudecode", "cursor"]
globs:
  [
    "client/src/app/pages/**",
    "client/src/app/components/**",
    "client/src/app/hooks/**",
    "client/src/routes/**",
  ]
---

# List state (filters, sort, page)

[ADR 0008](../../docs/adr/0008-view-state-persistence.md) decides where a list's state is kept.
In code that is **only state management**: `usePersistentState` (`@app/hooks/usePersistentState`)
replaces `useState`; PatternFly props, debouncing and API params stay in the page.

## Define once, choose where at the call

```ts
// contribuyente-list/contribuyenteListState.ts: what the list keeps, one definition per piece
export const CONTRIBUYENTE_LIST_STATE = defineListState({
  pagination: PAGINATION_STATE,
  sort: defineSortState({
    columnKeys: SORTABLE_COLUMN_KEYS,
    defaultValue: { sortBy: "nombre", direction: "asc" },
  }),
  filters: definePersistentState({
    key: "filters",
    schema: z.object({
      texto: z.string(),
      tipoPersona: z.array(schemaFromGuard(isTipoPersona)),
    }),
    defaultValue: { texto: "", tipoPersona: [] },
  }),
});

// ContribuyenteList.tsx: where each piece is kept
const [pagination, setPagination] = usePersistentState({
  ...CONTRIBUYENTE_LIST_STATE.pagination,
  storage: "url", // "url" | "session" | "local" | "state"
});
```

- Pagination, sort and filters are one `usePersistentState` call each. It returns
  `[value, setValue]` with a stable setter; changing `storage` changes no other line.
- `storage` follows ADR 0008's default: `"url"` for a page's or tab's main list; `"state"` for a
  list that isn't a screen's main list or can be shown several times at once (modals, expanded
  rows, drawers, dashboard cards); `"session"` / `"local"` only where the screen should remember.
- A list's definitions are one `defineListState({ … })` object, `<LIST>_STATE`, in
  `<list>State.ts` next to the page; never a type annotation, which would widen each piece. Its
  pieces: `definePersistentState`, or `PAGINATION_STATE`, `defineSortState` and `schemaFromGuard` from
  `@app/utils/listState`. A schema or default built in the component makes the setter unstable.
  A pagination-only list uses `PAGINATION_LIST_STATE` (route and page), with no state file.
- A `"state"` piece whose default comes from props stays a `useState`: nothing persists, and a
  default built per render would unsettle the setter.
- DataView's own state hooks (`useDataViewPagination`, `useDataViewFilters`, `useDataViewSort`)
  are lint-blocked: they keep a second copy of the state.
- The hook already writes with `replace` (no Back step), leaves defaults out of the address, and
  scopes session/local keys by route and tenant. Don't redo any of it: no own `navigate` for list
  state, no tenant in `key`.

## Schemas (zod)

These schemas are zod; yup is for forms only ([forms](forms.md)).

- A flat `z.object`, one schema per field; field names become the query params, so use short
  readable Spanish words (`texto`, `estado`, `tipoPersona`). Values arrive as strings: `z.coerce.number()`,
  `z.stringbool()`. A list field has a list default (`[]`). No `.default()` / `.catch()`: a missing
  or invalid field already falls back to `defaultValue`.
- A filter over an API enum is a picker (`DataViewCheckboxFilter`), never free text. Its values
  are an `as const` list next to the query (`TIPOS_PERSONA`, `JOB_STATES`) with a guard
  (`isTipoPersona`), used as `z.array(schemaFromGuard(guard))`; the params take it without a
  cast.

## The page

- A filter, sort or page-size change also sets page 1, in the same handler.
- The toolbar reads the state itself (`values={filters}`), so restored filters show as chips;
  clearing sets the definition's `defaultValue` (`setFilters(CONTRIBUYENTE_LIST_STATE.filters.defaultValue)`).
- `columnSort` (`@app/utils/sortHelpers`) gives searchpe's `sortByParam` and `<Th>` sort props;
  sortable columns are only the fields the endpoint sorts by. `ListPagination` takes
  `pagination` and `onPaginationChange={setPagination}`.

## The list's route

A page's main list in the URL declares its state on its route, so links into it are
type-checked and leave defaults out:

```ts
// routes/_authenticated/padron/contribuyentes/index.ts
const { validateSearch, search } = listSearch(CONTRIBUYENTE_LIST_STATE);
createFileRoute("/_authenticated/padron/contribuyentes/")({ validateSearch, search, component: ContribuyenteList });

// any page: checked against the list's schemas
<Link to="/padron/contribuyentes" search={{ tipoPersona: ["JURIDICA"] }} />
```

- The page and the route import the same `<LIST>_STATE`, so they can't drift.
- The route only declares: the list reads its state through `usePersistentState`, and
  other lists' params pass through. A prefixed list (inside a `PersistentStateScopeProvider`) isn't
  declared, since its prefix depends on the screen.
- No per-list type test: `utils/listState.typecheck.ts` guards `listSearch` through one list.

## Reused lists and the URL

- A reused list never picks its own prefix: the screen showing it wraps it in
  `<PersistentStateScopeProvider prefix="…">` (`@app/context/PersistentStateScope/PersistentStateScopeProvider`),
  turning `page` into `prefix.page`. The dev error "Two persistent states on one screen use …"
  means a scope is missing.
- No `window.location` reads or hand-built query strings; a link to a filtered list passes
  `search` with the list's field names.
- Changing a route's own search param in place uses `search: (prev) => ({ ...prev, … })`; the
  object form replaces the whole query and wipes the list's state.
- Query values are plain strings (`parseSearch` / `stringifySearch`, `@app/utils/searchParams`):
  `validateSearch` gets `string | string[]`, never numbers or JSON.

## Checklist

Pass/fail rubric for `/eval`. Check changed list pages, list components, hooks and routes.

- [ ] Each changed list keeps pagination, sort and filters in `usePersistentState`, one call per
      piece (a props-derived `"state"` piece may stay `useState`).
- [ ] `storage` is `"url"` for a page's or tab's main list, `"state"` in modals, expanded rows,
      drawers and cards, session or local only where the screen should remember.
- [ ] Schemas are flat zod objects that convert strings, with list defaults for list fields and
      no `.default()` / `.catch()`; enum filters are pickers typed with `schemaFromGuard`, with no
      cast in the params.
- [ ] Filter, sort and page-size changes also set page 1.
- [ ] The toolbar's values are the state; clearing sets the definition's `defaultValue`.
- [ ] No own history writes, default values or tenant in keys for list state: the hook does them.
- [ ] A reused list is namespaced by its screen with `PersistentStateScopeProvider`.
- [ ] A list's definitions are one `defineListState` `<LIST>_STATE` in `<list>State.ts` (no schema or default
      built in the component); a page's main URL list passes `listSearch(<LIST>_STATE)` to its
      route.
- [ ] No `window.location` reads or hand-built query strings; in-place search changes use the
      `prev =>` form; `validateSearch` expects strings.
