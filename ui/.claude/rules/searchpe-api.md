---
paths:
  - client/src/app/queries/searchpe/**
  - client/src/app/hooks/**
  - client/src/app/pages/searchpe/**
---
# searchpe API usage

Before wiring an endpoint, read its operation in `client/openapi/searchpe.json` (or the
generated client `@app/client/searchpe`). Behaviour notes are in
`docs/internals/searchpe-api-notes.md`.

## Shape

- Base path `/api`, called through the generated client (base URL `/api/searchpe`); JSON is
  camelCase (JSON-B) as documented. The health check (`/q/health/ready`) is the one call outside
  the spec.
- searchpe is **optional** ([ADR 0001](../../docs/adr/0001-one-shell-two-backends.md)): code that
  can run without it checks `ENV.SEARCHPE_ENABLED` (queries that the shell runs everywhere set
  `enabled` from it); searchpe routes are 404 without it.
- Errors are `{ message }` or a framework body: use `getMutationErrorMessage`.

## Contribuyentes

- `GET /contribuyentes/{numeroDocumento}` takes a RUC (11 digits) or DNI (8): validate with
  `isNumeroDocumento` before calling; a `404` is "no está en el padrón".
- `GET /contribuyentes` sorts only by `nombre` (`sort_by=nombre:asc|desc` from
  `columnSort`), filters by `filterText` and `tipoPersona` (`NATURAL` | `JURIDICA`), caps
  `offset` at 9000 and answers `meta.count`. It is `400` while the server disables advanced
  search: show that as an info state pointing to the consulta, not as an error.

## Versions

- `GET /versions` is unpaged: read it whole and page on screen (the named exception in
  [list-reads](list-reads.md)); poll it while a version isn't finished.
- Status helpers mirror the server's `Status`: `isVersionFinished`, `isVersionCancellable`,
  `isVersionDeletable`, typed as `Record<Status, …>` so a new status fails to compile.
- Cancel is `PUT /versions/{id}` with `status: "CANCELLING"` (the only update accepted); delete
  schedules a deletion. Both and create invalidate the versions and every contribuyente query.

## Permissions

- `GET /whoami` lists the user's permissions; gate with `useSearchpePermissions()`:
  `search` for contribuyentes, `version:write` for versions, `admin:app` for everything. An
  anonymous whoami means authorization is off: everything is allowed.
- No user management: `/admin/*` and `/current-user/*` are dropped from the generated client
  ([ADR 0004](../../docs/adr/0004-identity-and-permissions.md)).

## Checklist

Pass/fail rubric for `/eval`. Check changed searchpe queries, hooks and pages.

- [ ] Calls go through `@app/client/searchpe`; only the health check is raw.
- [ ] Code reachable without searchpe respects `ENV.SEARCHPE_ENABLED`.
- [ ] Document numbers are validated before a lookup; a disabled search is an info state.
- [ ] Sorting uses only `nombre`; offsets respect the 9000 cap.
- [ ] Version status logic uses the `Record<Status, …>` helpers; writes invalidate versions and
      contribuyentes.
- [ ] Actions are gated with `useSearchpePermissions`; no user-management calls.
