---
name: add-mutation
description: Use when adding a write (create, update, delete, cancel, an action) against xhandler-service or searchpe in client/src/app/queries — a useMutation hook that owns its invalidation, plus the page wiring for toasts, confirmations and in-form errors.
targets: ["*"]
---

# Add a mutation

Read `.rulesync/rules/toastr.md`, `component-boundaries.md`, `forms.md` and the backend rule
(`xhandler-api.md` / `searchpe-api.md`) first.

## Steps

1. **Find the operation** and its declared responses (`201`, `202` + job, `204`, …); the
   generated function gives the body type.
2. **Hook** in `queries/<backend>/<family>.ts`, next to the reads it invalidates:

```ts
/** `POST …/jobs/{id}/cancel`: answers the job, now `canceling` or `canceled`. */
export const useJobCancelMutation = (
  options?: IMutationCallbacks<JobView, JobView, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<JobView, XhandlerAxiosError, JobView>({
    mutationFn: async (job) =>
      (
        await cancelJob({
          path: { tenantId, jobId: job.id ?? "" },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, job) => {
      await queryClient.invalidateQueries({ queryKey: jobKeys.list(tenantId) });
      await options?.onSuccess?.(data, job);
    },
    onError: options?.onError,
  });
};
```

- Invalidation is inside the hook, computed from the key factory and the variables; no
  `invalidateKey` parameter.
- No toast text in the hook.
- xhandler creates send an `Idempotency-Key` header (`headers: { "Idempotency-Key": key }`)
  with one key per submission, reused on retry.

3. **Page wiring** — hook-level callbacks raise the Spanish toasts; call-level `onSuccess`
   closes the dialog:

```tsx
const cancelJob = useJobCancelMutation({
  onSuccess: (job) => addNotification({ variant: "info", title: `Se solicitó cancelar el trabajo ${job.name}` }),
  onError: (err) =>
    addNotification({ variant: "danger", ...getMutationErrorMessage({ error: err, fallback: "No se pudo cancelar el trabajo" }) }),
});

<ConfirmActionModal
  isOpen={cancelTarget !== null}
  confirmVariant="primary"
  isConfirming={cancelJob.isPending}
  onConfirm={() => cancelTarget && cancelJob.mutate(cancelTarget, { onSuccess: () => setCancelTarget(null) })}
  onCancel={() => setCancelTarget(null)}
  …
/>
```

4. **Gate** the action with `disabledProps` from `useTenantRole()` / `useSearchpePermissions()`.
5. **Forms**: a modal or page form shows the failure in `FormSubmitError` instead of a toast.
6. **Test** the gating and any helper in Vitest; the round-trip in the surface's e2e spec.
