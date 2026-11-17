# xhandler-service API notes

Behaviour of the xhandler API that shaped the code. The contract is xhandler `docs/DESIGN.md`
§12; the conventions are in its `AGENTS.md`.

- **Base path** `/api/v1`; the UI calls it as `/api/xhandler/v1`.
- **JSON is snake_case** everywhere, including bodies the UI sends. The generated types are
  snake_case thanks to the spec patch ([api-clients.md](api-clients.md)).
- **Errors** are `{ kind, message, detail }`, `message` in Spanish
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
  workers, queues and component health.
- **Public endpoints** (`/api/v1/public/*`) need no token; the UI calls them without one.
- **Audience.** Access tokens must include `xhandler-service` in `aud`: the UI's Keycloak client
  needs an audience mapper.
