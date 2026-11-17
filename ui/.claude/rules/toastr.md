---
paths:
  - client/src/app/queries/**
  - client/src/app/pages/**
  - client/src/app/components/**
---
# Notification / toast conventions

**Mutation toasts are defined at the page/feature layer, never hidden in a shared wrapper.**
Mutation hooks own **cache invalidation only**; the page or feature hook that triggers the
mutation owns the message and passes it as **hook-level** `onSuccess`/`onError`
([TkDodo, _Mastering Mutations_](https://tkdodo.eu/blog/mastering-mutations-in-react-query)).
A form's submission error is the one exception: it shows in the form (`FormSubmitError`). A
reusable component never owns the toast or the invalidation
([component-boundaries](component-boundaries.md)).

## Hooks

- Mutation hooks accept `IMutationCallbacks<TData, TVars, TError>`
  (`queries/utils/mutationCallbacks.ts`) and call them from their own `useMutation`
  `onSuccess`/`onError`, **after** invalidation.
- A mutation hook owns its invalidation **internally** (family key factory + variables) and
  **never takes a `QueryKey`/`invalidateKey` parameter**.
- No `useMutationAction` / `runAction` wrapper and no per-feature `useXActions` toast hub.

## Callbacks: hook-level vs call-level

Toasts must fire even if the component unmounts mid-request.

- **Hook-level** callbacks (`useMutation({ onSuccess, onError })`) always run → toasts.
- **Call-level** callbacks (`mutate(vars, { onSuccess })`) are dropped on unmount → only UI
  that's fine to skip (close a dialog, reset state).
- Dialogs **close on success, stay open on error**.
- Use `mutate`, not `mutateAsync`, except to chain requests.

## Jobs

- A write that starts an xhandler job reports it once, when the response arrives
  ([ADR 0007](../../docs/adr/0007-job-reporting.md)); no toast is ever raised by polling a job
  or a padrón version.

## Variables and message

- A mutation's variables are exactly what the request needs: no field only for the toast.
  Delete and cancel take the entity itself (typed with the generated type).
- Messages are Spanish ([ADR 0005](../../docs/adr/0005-spanish-ui.md)), built from `data`, the
  `variables`, or a value fixed for the component's lifetime. **Never close over per-call
  state** (a `cancelTarget` cleared mid-flight reads as `null`).
- Error toasts spread `getMutationErrorMessage({ error, fallback: "No se pudo …" })` into a
  `variant: "danger"` toast; it carries the server's message and detail
  ([ADR 0006](../../docs/adr/0006-api-error-display.md)).

## Checklist

Pass/fail rubric for `/eval`.

- [ ] Mutation hooks own **cache invalidation only** — no toast text inside the hook — and take
      no `QueryKey`/`invalidateKey` parameter.
- [ ] Mutation hooks accept `IMutationCallbacks` and call them **after** the invalidation.
- [ ] Toasts are raised at the page/feature layer via **hook-level** callbacks; dialog close and
      state reset use the **call-level** `onSuccess`, never `onError`.
- [ ] `mutate`, not `mutateAsync`, unless chaining requests.
- [ ] Variables carry only what the request needs; messages are Spanish and built from `data`,
      `variables` or lifetime-fixed values, never per-call state.
- [ ] Error toasts use `getMutationErrorMessage({ error, fallback })` with `variant: "danger"`
      (form submissions excepted).
- [ ] No toast is raised by polling a job or a version.
