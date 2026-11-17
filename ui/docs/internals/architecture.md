# Architecture

A high-level map of how the UI is structured. For the design decisions behind it see
[docs/adr/](../adr/); for the coding rules see [.rulesync/rules/](../../.rulesync/rules/).

```mermaid
flowchart LR
  Browser["Browser SPA (React, PatternFly)"] -->|/api/xhandler/*| UI["UI server (Express / Vite dev)"]
  Browser -->|/api/searchpe/*| UI
  Browser -->|OIDC code + PKCE| KC["Keycloak (realm xhandler)"]
  UI -->|/api/*| XH["xhandler-service"]
  UI -->|/api/*, /q/health| SP["searchpe (optional)"]
```

## One shell, two backends

A React single-page app drives two independent REST APIs
([ADR 0001](../adr/0001-one-shell-two-backends.md)). The browser calls both on its own origin,
under one prefix each; the UI server (Express in production, Vite in development) proxies
`/api/xhandler/*` and `/api/searchpe/*` to the backends' `/api/*` (searchpe's health check to
`/q/health`). No CORS, and the browser never learns the backend URLs. searchpe is optional:
without `SEARCHPE_API_URL` its proxy, its navigation and its routes don't exist.

`ApiStatusProvider` polls `GET /api/v1/status` (xhandler, public) and searchpe's
`/q/health/ready`; each product's sidebar group shows only while its backend answers. Neither
blocks the shell.

## Authentication

OIDC only, through `react-oidc-context` / `oidc-client-ts` (authorization code with PKCE,
silent renew), with one `UserManager` (`oidc.ts`) shared by `AuthProvider` and the axios
interceptors. `axios-config/apiInit.ts` builds one axios instance per backend; both add the
user's `Bearer` token and, on a `401`, renew the token silently (one renewal shared by
concurrent requests) and retry once. A `401` with no stored user is rejected as is. When renewal
fails, xhandler sends the user to log in again and searchpe just fails the request (its section
reads as unavailable when `whoami` keeps answering `401`); nothing signs the user out but the
user. A third instance, used by public pages, never carries a token
([ADR 0003](../adr/0003-public-and-authenticated-routes.md)).

Routes under `_authenticated/` start the login in `beforeLoad` (never on a preload) and hold
the route on a promise that never settles, so nothing renders or loads while the browser leaves
for Keycloak; routes under `_public/` don't, and render in a bare shell (`PublicLayout`).
Logout returns to the public `/sesion-cerrada`.

## UI server

The Express server (`server/`) proxies each backend, mounting searchpe's proxy only when
`SEARCHPE_API_URL` is set. Before any proxy it answers `400` to API paths with `.`/`..`
segments, backslashes or encoded dots and slashes, and to any path that would land outside the
backend's `/api/` after the rewrite (searchpe's `/q/health/ready` excepted)
(`common/src/proxyPath.ts`; Vite's dev server runs the same check). It sends a CSP with a
per-request nonce for the inline `window._env` script, `Referrer-Policy: no-referrer` and
`nosniff`; the rendered `index.html` is `no-store`, hashed `/assets` are cached for a year. The
browser only gets the allowlisted keys of `CLIENT_ENV_KEYS` (`common/src/environment.ts`).

## Data

- Generated clients (`@app/client/xhandler`, `@app/client/searchpe`) give the request functions
  and types ([api-clients.md](api-clients.md)).
- TanStack Query owns server state: one module per domain in `queries/xhandler/` and
  `queries/searchpe/`, each with a key factory, `*QueryOptions` factories, hooks and mutations
  that own their invalidation.
- List state (filters, sort, page) lives in the URL through `usePersistentState`
  ([ADR 0008](../adr/0008-view-state-persistence.md)).
- Zustand holds the little client state that outlives a page (`stores/useLastTenantStore.ts`:
  the sidebar's last tenant).

## Tenancy

xhandler pages carry the tenant (and company) in the URL
([ADR 0002](../adr/0002-navigation-partner-tenant-company.md)); `useSelectedTenantId()` is the
one hook that reads it, and tenant-scoped query hooks inject it.

## Permissions

`useSearchpePermissions` (from `whoami`) and `useTenantRole` (from the user's tenant
memberships) gate actions: disabled with a tooltip, never hidden
([ADR 0004](../adr/0004-identity-and-permissions.md)).
