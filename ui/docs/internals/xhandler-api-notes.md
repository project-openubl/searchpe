# xhandler-service API notes

Behaviour of the xhandler API that shaped the code. The contract is xhandler `docs/DESIGN.md`
§12; the conventions are in its `AGENTS.md`.

- **Base path** `/api/v1`; the UI calls it as `/api/xhandler/v1`.
- **JSON is snake_case** everywhere, including bodies the UI sends. The spec documents the
  snake_case names, so the generated types match the wire ([api-clients.md](api-clients.md)).
- **Errors** are `{ kind, message, detail }` (the spec's `ApiError`), `message` in Spanish
  ([ADR 0006](../adr/0006-api-error-display.md)).
- **Tenancy.** Every tenant route is `/tenants/{tenantId}/…`; a foreign and an unknown tenant
  are both `404`. `GET /tenants` lists the user's memberships with their `role`
  (`owner > admin > operator > viewer`, `support` reads only), which the UI uses to gate actions
  (`useTenantRole`, mirroring `Role#allows`).
- **Pages** answer `{ items, limit, offset }` with no total; `limit`/`offset` are query params.
- **Jobs** (`/tenants/{t}/jobs`) are Pulp-like: `state` in `waiting | running | completed |
failed | canceling | canceled`, filter by `state` and `name`; cancel is
  `POST /jobs/{id}/cancel` (operator), answering the job.
- **Status** `GET /api/v1/status` is public: versions (service, engine, xhandler-rust),
  workers, queues, component health and `sunat` (per environment, `available` plus each SUNAT
  service's breaker state and pause).
- **Creates take an `Idempotency-Key`** (documents, drafts, companies, series, members,
  webhooks…): a retry with the same key and body gets the stored `201`; a different body is
  `422 idempotency_key_reused`, one still running `409`. A replayed webhook create has
  `secret: null` (the secret is shown once).
- **Members** (admin): `PUT …/members/{id}` changes the role (never to owner), `DELETE` removes
  one; only an owner touches an owner (`403 owner_required`) and the last owner stays
  (`409 last_owner`).
- **Series**: `PUT …/series/{id}` moves `next_number` forward only (`409 series_number_used`)
  and sets `active`; a deactivated series refuses emits.
- **Documents** filter by buyer: `customer_numero` (exact) and `customer_nombre` (prefix,
  case-insensitive).
- **Public endpoints** (`/api/v1/public/*`) need no token; the UI calls them without one.
- **Buyer portal.** `GET /public/documents/{token}` lists the downloadable `files` (`pdf`, `xml`,
  `cdr`) and the issuer's `libro_reclamaciones_url` when set; `…/{token}/{kind}` downloads one
  (binary: `responseType: "blob"`), and the first download marks the document delivered.
- **Drafts** (manual issuing, DESIGN.md §11.10) are tenant-level (`/drafts/{id}`) but created
  under a company (`/companies/{c}/drafts`). `spec` is xbuilder JSON without `serie_numero`;
  `PUT` replaces it and takes the `version` edited (409 `draft_changed` when stale);
  `POST …/issue?version=` answers emit's `201 DocumentView`, `422` with SUNAT codes leaves the
  draft editable, `409 draft_issued` once issued.
- **Audience.** Access tokens must include `xhandler-service` in `aud`: the UI's Keycloak client
  needs an audience mapper.
