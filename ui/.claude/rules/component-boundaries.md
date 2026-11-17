---
paths:
  - client/src/app/components/**
  - client/src/app/pages/**
---
# Component boundaries: the caller owns side effects

**A reusable component is controlled and side-effect-free. The caller that knows the resource
owns the mutation, the cache invalidation and the toasts, and decides what happens on success
and on error.** A component reused across resources/callers cannot know which cache to refresh
or what a success message should say — so it must not try. It takes data and callbacks, and
reports outcomes upward.

## No query keys in component props

- A component never receives a `QueryKey` / `invalidateKey` (or any cache-key) prop, and never
  calls `queryClient.invalidateQueries`. Cache invalidation lives in the mutation hook the
  caller invokes ([queries](queries.md), [toastr](toastr.md)).
- Wanting to pass an `invalidateKey` down is the signal the component is doing the caller's job.
  Make it controlled instead: expose a callback (`onSave`, `onConfirm`, `onChange`) and let the
  caller run the mutation, which owns invalidation.

## No notifications in a reusable component

- A reusable component never calls `useNotifications` / `addNotification` / `notify*`. It
  surfaces outcomes through its callbacks; the page or feature layer raises the toast via
  hook-level `onSuccess`/`onError` ([toastr](toastr.md)).
- The caller — not the component — decides both the success path (invalidate, toast, close) and
  the error path.

## The controlled shape

- A shared editor/modal takes its data (`value` / `labels` / `entity`), caller-owned status
  (`isSaving` / `isPending`), and `on*` callbacks — never a mutation hook, a query hook, or a
  query key.
- `onSave` / `onConfirm` may return the request's `Promise`/result so the component can make a
  pure-UI reaction (close on success, stay open on failure) without owning the side effect.
- Example (confirmations): `ConfirmActionModal` takes `title`, `body`, `isConfirming` and
  `onConfirm` / `onCancel`; each caller (`VersionList`, `JobList`) wires its own mutation
  (`useVersionCancelMutation`, `useJobCancelMutation`, each owning its invalidation), raises its
  toasts and closes the modal from the call-level `onSuccess`.

## The boundary (litmus)

- A **reusable component** — anything in `@app/components`, a page's `components/` used by more
  than one caller, or a component generic over multiple resources — is controlled and owns no
  data side effects.
- A component dedicated to **one specific action/resource** is the **feature owner** (it is the
  caller): it wires its own mutation + toasts exactly as [toastr](toastr.md) prescribes — e.g.
  a page that runs one action on its own rows.
- The query key is the test: if the component would need a query key passed in to do its job, it
  is not the owner — make it controlled.

## Checklist

Pass/fail rubric for `/eval`. Check changed reusable components under `components/` and shared
page `components/`.

- [ ] No component takes a `QueryKey` / `invalidateKey` (or other cache-key) prop, and none
      calls `invalidateQueries`.
- [ ] No reusable/generic component calls `useNotifications` / `addNotification` / `notify*`;
      outcomes surface through callbacks.
- [ ] Reusable editors/modals are controlled: data + caller-owned `isSaving` / `isPending` +
      `on*` callbacks; no mutation hook or query hook inside.
- [ ] The caller (page, or the single-surface feature owner) owns invalidation, toasts and the
      success/error decisions; `onSave` / `onConfirm` return a Promise/result when the component
      needs a pure-UI reaction.
- [ ] A single-action feature owner that raises its own toast does so per [toastr](toastr.md)
      and takes no query key.
