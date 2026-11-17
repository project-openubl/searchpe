---
name: stores
description: "Apply when adding or changing a Zustand store in client/src/app/stores/: stores hold only client state that must outlive a component (never server data, route params, view state or form state), persist only serializable data, and are read through selectors."
root: false
targets: ["claudecode", "cursor"]
globs: ["client/src/app/stores/**"]
---

# Client stores (Zustand)

A store is for **client state that must outlive the component that creates it**: work the user
started that a page leaving can't drop, a choice the shell needs everywhere (the last tenant
opened), or state read outside React. Everything else has an owner already:

| State                                   | Owner                                              |
| --------------------------------------- | -------------------------------------------------- |
| Anything the server returns             | TanStack Query ([queries](queries.md))             |
| Tenant, company and other ids           | The URL (route params)                             |
| A list's filters, sort, page            | `usePersistentState` ([list-state](list-state.md)) |
| Form values and validation              | react-hook-form ([forms](forms.md))                |
| A modal's open flag, a page's selection | `useState` in that component                       |

## Shape

- One store per file, `stores/use<Name>Store.ts`, created with `create<IState>()(…)`. Actions
  live in the store next to the state they change.
- Components read with a selector per value (`useLastTenantStore((state) => state.lastTenantId)`), never the
  whole store; a selector that builds a new object or array wraps it in `useShallow`.
- Code outside React (auth providers, axios interceptors, route guards) uses
  `useXStore.getState()`.
- A store never fetches, toasts or invalidates queries. The component that reacts to it does.

## Persistence

- `persist` with an `openubl-ui/<name>` key and a `version`; `partialize` to what must survive a
  reload. Persisted state is JSON only: no callbacks, React nodes, `Map`s or class instances.
  Describe follow-up work as data (`kind` plus hrefs) and map it to behaviour where it runs.
- A persisted store that several tabs write re-reads on the `storage` event
  (`persist.rehydrate()`), so tabs don't overwrite each other.
- State tied to the signed-in user is reset on logout.

## Checklist

Pass/fail rubric for `/eval`. Check changed files in `stores/` and their readers.

- [ ] The store holds client state that outlives a component; no server data, route params,
      view state or form state.
- [ ] Readers use a selector per value (`useShallow` for derived objects or arrays); code
      outside React uses `getState()`.
- [ ] The store has no fetching, toasts or query invalidation.
- [ ] Persisted state is serializable, keyed `openubl-ui/<name>` with a `version`, narrowed by
      `partialize`, synced across tabs, and user state resets on logout.
