---
name: ui-conventions
description: "Apply when building or changing UI in pages/ or components/: Spanish copy, the shared table, modal and detail helpers, and where this project departs from PatternFly defaults (no red actions, no empty-state actions, disabled not hidden)."
root: false
targets: ["claudecode", "cursor"]
globs:
  [
    "client/src/app/pages/**",
    "client/src/app/components/**",
    "client/src/app/layout/**",
  ]
---

# UI conventions (`components/`, `pages/`)

This rule covers where the project departs from PatternFly defaults and which shared helpers
to use; generic PatternFly usage (imports, composition, tokens) is out of its scope.

## Language

- Every user-facing string is Spanish (es-PE), hard-coded where it's shown; no i18n library
  ([ADR 0005](../../docs/adr/0005-spanish-ui.md)). Accessible names (`aria-label`) too.
- Dates through dayjs with `RENDER_DATE_FORMAT` / `RENDER_DATETIME_FORMAT` (`@app/constants`);
  numbers with `toLocaleString("es-PE")`.
- PatternFly English defaults are replaced: `ListPagination` (pager titles),
  `ClearFiltersButton` as `DataViewToolbar`'s `customLabelGroupContent`, Spanish placeholders on
  filters.
- A server value with a known meaning gets a Spanish label from a `Record` over its values (so a
  new value fails to compile); an unknown value renders as returned.

## Layout

- Tables: `@patternfly/react-data-view` with `dataViewBodyStates`, `ListPagination`,
  `columnSort`; paging, sorting and filtering are server-side, their state kept with
  `usePersistentState` ([list-state](list-state.md)). `PAGINATION_STATE` (10 per page), never
  above 20.
- Text filters go through `useDebouncedValue`; long cells use `modifier: "truncate"`; data
  column `width`s sum to 100, action columns stay unsized.

```tsx
const debouncedTexto = useDebouncedValue({ value: filters.texto.trim() });
const { data, isLoading, error } = useContribuyentesListQuery({
  params: {
    ...toPageParams(pagination),
    filterText: debouncedTexto || undefined,
  },
});

const columns: DataViewTh[] = [
  { cell: "RUC", props: { width: 20 } },
  {
    cell: "Nombre o razón social",
    props: { sort: sortProps("nombre"), width: 80 },
  },
  { cell: "" }, // actions: unsized
];

const { activeState, bodyStates } = dataViewBodyStates({
  columnCount: columns.length,
  isLoading,
  error,
  isEmpty: rows.length === 0,
  emptyState: (
    <TableEmptyState
      title="No hay contribuyentes"
      isFiltered={isFiltered}
      onClearFilters={clearAllFilters}
    />
  ),
});

return (
  <>
    <DocumentTitle title="Contribuyentes" />
    <PageSection>
      <Content component={ContentVariants.h1}>Contribuyentes</Content>
    </PageSection>
    <PageSection>
      <DataView activeState={activeState}>
        <DataViewToolbar
          clearAllFilters={clearAllFilters}
          customLabelGroupContent={
            <ClearFiltersButton onClick={clearAllFilters} />
          }
          filters={/* DataViewFilters with values={filters} */}
          pagination={
            <ListPagination
              variant={PaginationVariant.top}
              itemCount={data?.meta?.count ?? 0}
              pagination={pagination}
              onPaginationChange={setPagination}
            />
          }
        />
        <DataViewTable
          aria-label="Tabla de contribuyentes"
          columns={columns}
          rows={rows}
          bodyStates={bodyStates}
        />
      </DataView>
    </PageSection>
  </>
);
```

- List and detail pages put the header in its own `<PageSection>` and render
  `<DocumentTitle>`. Tenant pages render `<TenantContextSelector />` above the header.
- Confirmations use `ConfirmActionModal`, never a hand-rolled `Modal`; detail fields a
  `DescriptionList`.
- No inline `style` or custom CSS where a PatternFly component, layout or token exists.

## Departures from PatternFly defaults

- **Never red** on row actions, header kebab items or toolbar buttons: no `isDanger: true`,
  no `variant="danger"`. Danger alerts and toasts for errors are fine.
- `ConfirmActionModal`'s default `confirmVariant="danger"` is only for an irreversible delete;
  every other confirmation (cancel an import or a job) passes `confirmVariant="primary"`.
- **Empty states carry no actions**: title naming what is empty, only **Limpiar filtros** for
  filtered results. `EmptyStateActions` only in `RouteErrorFallback`.
- **Disabled, not hidden** ([ADR 0004](../../docs/adr/0004-identity-and-permissions.md)):
  actions spread `disabledProps(…)` from `useSearchpePermissions()` or `useTenantRole()` (a
  `GatedButton`, or `isAriaDisabled` + `tooltipProps` on a row action) instead of rendering on
  `can(…)`. A page the user can't use at all renders `UnauthorizedState`.

## Status

- Job, version and service states come from the server and are refreshed while they can change
  (`refetchInterval` on the list query); never local "pending" flags alone
  ([ADR 0007](../../docs/adr/0007-job-reporting.md)).
- A product whose backend is down or not deployed has no navigation
  (`useApiStatus()`, [ADR 0001](../../docs/adr/0001-one-shell-two-backends.md)).

## Checklist

Pass/fail rubric for `/eval`. Check changed files under `pages/`, `components/` and `layout/`.

- [ ] Every user-facing string and accessible name is Spanish; dates and numbers use the es-PE
      helpers; PatternFly English defaults are replaced.
- [ ] Tables: react-data-view helpers, server-side paging/sorting/filtering, `perPage` 10
      (≤ 20), debounced text filters, `truncate`, widths summing to 100.
- [ ] Header in its own `<PageSection>` + `<DocumentTitle>`.
- [ ] `ConfirmActionModal` for confirmations; no custom styling where PatternFly has an
      equivalent.
- [ ] No red row actions, menu items or toolbar buttons; danger confirm only for irreversible
      deletes.
- [ ] Empty states have no actions except **Limpiar filtros**.
- [ ] Permission-gated actions are disabled via `disabledProps`, not hidden.
- [ ] States are server-derived and refreshed while they can change.
