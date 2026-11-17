# searchpe API notes

Behaviour of the searchpe API that shaped the code.

- **Base path** `/api` (`quarkus.rest.path`); the UI calls it as `/api/searchpe`. The health
  check is outside it: `/q/health/ready`, reached as `/api/searchpe/q/health/ready`.
- **JSON is camelCase** (JSON-B), as documented.
- **Auth.** OIDC bearer tokens are accepted only with `quarkus.oidc.tenant-enabled=true`, and
  roles are enforced only with `searchpe.disable.authorization=false` (both off by default).
  With authorization off, `whoami` answers the anonymous identity (no username, no roles) and
  the UI allows every action.
- **Permissions** are realm roles: `search` (contribuyentes), `version:write` (versions),
  `admin:app` (everything). `GET /whoami` lists them.
- **Contribuyentes.** `GET /contribuyentes/{numeroDocumento}` finds by RUC (11 digits) or DNI
  (8), `404` otherwise. `GET /contribuyentes` (`filterText`, `tipoPersona` NATURAL|JURIDICA,
  `offset` ≤ 9000, `limit` ≤ 1000, `sort_by=nombre:asc|desc`) answers `{ meta: { count }, data }`
  and is `400` while `searchpe.allow.advancedSearch=false` (the default): the list page then
  points to the consulta.
- **Versions.** `GET /versions` answers every version, unpaged (the clean-up job keeps few): the
  one documented exception to the list-reads rule. `POST /versions` creates one and schedules
  its import; `PUT /versions/{id}` accepts only `{ status: "CANCELLING" }`; `DELETE` schedules a
  deletion (status `DELETING`). Statuses mirror `Status#isFinished` in
  `queries/searchpe/versions.ts`.
