# AGENTS.md

Guidance for AI coding agents (and humans) working in the **Openubl UI** (`ui/`): the one web
UI for xhandler-service (SUNAT electronic documents) and searchpe (RUC lookup). It follows the
design, structure and tooling of [pulp-ui-2](https://github.com/pulp/pulp-ui-2) (xhandler
`docs/DESIGN.md` §18).

## Where guidance lives

| Layer                              | Answers                                    | Use it                                                                                    |
| ---------------------------------- | ------------------------------------------ | ----------------------------------------------------------------------------------------- |
| [docs/adr/](docs/adr/)             | **What** the UI does and why (code-free)   | Before changing a screen or flow, match ADRs by `description` and follow their `## Rules` |
| [.rulesync/](.rulesync/)           | **How** code is written: rules and skills  | Loaded automatically by your tool; see [Coding Standards](#coding-standards)              |
| [docs/internals/](docs/internals/) | How the code works (data flow, API quirks) | Orientation; it explains, it doesn't set rules                                            |

A design change needs a new or superseding ADR, and the rules that cover it updated in the same
change. The backends' own guides: `../xhandler/AGENTS.md` (design: `../xhandler/docs/DESIGN.md`,
§12 API, §18 UI) and `../AGENTS.md`.

## Repository Architecture

Four npm workspaces (`@app` alias maps to `client/src/app/`):

```
├── common/                   # shared module (branding, env config, proxy path rewrites)
├── client/                   # React SPA (Vite, TypeScript, PatternFly 6)
│   ├── openapi/              #   xhandler.json, searchpe.json: copies of the Maven builds' specs
│   ├── config/               #   openapi-ts config + in-memory spec patches
│   └── src/
│       ├── main.tsx          # app entry point
│       ├── router.ts         # TanStack Router instance
│       ├── routes/           # file-based routes: _authenticated/ (OIDC) and _public/ (no session)
│       ├── routeTree.gen.ts  # auto-generated route tree (DO NOT EDIT)
│       └── app/
│           ├── pages/        # page components: xhandler/, searchpe/, account/, home/, public/
│           ├── queries/      # TanStack Query hooks: xhandler/<family>.ts, searchpe/<family>.ts
│           ├── components/   # shared UI components
│           ├── hooks/        # useSelectedTenantId, useTenantRole, useSearchpePermissions, …
│           ├── context/      # Auth, ApiStatus (both backends), Notifications, PersistentStateScope
│           ├── layout/       # app shell (masthead, sidebar) and the public shell
│           ├── stores/       # Zustand stores (useLastTenantStore)
│           ├── utils/        # list state, sort, API error text
│           ├── client/       # generated clients: xhandler/, searchpe/ (DO NOT EDIT)
│           ├── axios-config/ # axios instances per backend, Bearer + silent-renew interceptors
│           └── oidc.ts       # OIDC configuration
├── server/                   # Express production server (proxies, env injection, /healthz)
└── e2e/                      # Playwright end-to-end tests
    ├── tests/                # mirrors client/src/app/pages/ (+ auth.setup.ts, navigation.spec.ts)
    └── support/              # fixtures, page objects, helpers (@support/* alias)
```

The browser calls `/api/xhandler/*` and `/api/searchpe/*` on its own origin; the Express server
(production) and Vite (development) proxy them to each backend's `/api/*`. searchpe is
optional: without `SEARCHPE_API_URL` its section doesn't exist.

## Key Commands

```bash
npm ci                     # install (runs `generate`)
npm run start:dev          # common + Vite dev server on :3000 (needs .env, see .env.example)
npm run start              # production build + Express on :8080 (PORT to change)

npm run lint               # the gate: rulesync check, ESLint + tsc per workspace
npm run format             # Prettier check (the gate)
npm run lint:fix && npm run format:fix

npm run lint:fast          # oxlint (fast loop); lint:fast:fix
npm run format:fast        # oxfmt (fast loop); format:fast:fix — always follow with format:fix
npm run oxlint:sync        # regenerate .oxlintrc.json after changing eslint.config.mjs

npm test                   # Vitest Browser Mode (client)
npm run test:e2e           # Playwright against a running stack (e2e/README.md)

npm run generate           # regenerate both API clients from client/openapi/
npm run openapi:refresh    # copy the specs from the Maven builds, then generate
npm run rules              # regenerate .claude/ and .cursor/ from .rulesync/
npm run build              # production builds
npm run hooks:install      # opt-in git hooks (lint-staged, commitlint) for the repository
```

### Linting and formatting for agents

Use the fast oxc tools (`lint:fast`, `format:fast`) while iterating and run the full gate once
at the end: `npm run format:fix && npm run lint && npm run format`. ESLint, tsc and Prettier are
the gate; oxlint misses some ESLint rules and oxfmt differs slightly from Prettier. After
changing `eslint.config.mjs`, run `npm run oxlint:sync`; after changing `.prettierrc.mjs`,
update `.oxfmtrc.json`.

## Conventions

- **Language** ([ADR 0005](docs/adr/0005-spanish-ui.md)): every user-facing text is Spanish
  (es-PE), hard-coded where it's shown; no i18n library. Code, comments and logs are English.
  SUNAT terms as SUNAT writes them (RUC, razón social, serie).
- **Acronyms are words** in identifiers: `Ruc`, `Sunat`, `Cdr`, `Xml`.
- **API errors** are shown as the UI's "No se pudo …" plus the server's `message` and `detail`;
  xhandler's `kind` is for logic only ([ADR 0006](docs/adr/0006-api-error-display.md)).
- **Tenancy**: xhandler pages carry tenant/company ids in the URL; foreign and unknown tenants
  both read as 404 ([ADR 0002](docs/adr/0002-navigation-partner-tenant-company.md)).
- **Public routes** never ask for a login and send no token ([ADR 0003](docs/adr/0003-public-and-authenticated-routes.md)).
- **SUNAT environments**: tests and scripts use beta or the fake only, never production.
- **Commits**: Conventional Commits, with the `ui` scope when it helps (`feat(ui): …`).

## Tech Stack

See [tech-stack.md](./docs/tech-stack.md).

## Coding Standards

Rules and skills are written once in `.rulesync/rules/` and `.rulesync/skills/`;
`npm run rules` ([rulesync](https://github.com/dyoshikawa/rulesync)) generates the `.claude/`
and `.cursor/` copies inside `ui/`. Never edit the generated copies: change the source and run
`npm run rules` (`npm run lint` fails while they are stale). When you rename or remove a rule or
skill, delete its generated copies by hand.

Before finishing a change, check it against the `## Checklist` of every rule matching the
changed files.

## Testing

- **Unit (Vitest Browser Mode)**: colocated `*.test.ts(x)`, `vitest-browser-react`; locally the
  installed Google Chrome, Playwright's Chromium in CI (`CI=1`, `npx playwright install chromium`).
- **E2E (Playwright)**: specs mirror `client/src/app/pages/` down to the surface folder; they need
  the stack described in [e2e/README.md](e2e/README.md).

## Branding

Chosen at build time with `BRANDING=<dir>` (default `./branding`): names, logo, links. See
[branding/README.md](branding/README.md).
