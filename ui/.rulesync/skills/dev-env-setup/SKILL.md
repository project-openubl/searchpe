---
name: dev-env-setup
description: Use when a developer asks about onboarding, getting started or setting up the Openubl UI (ui/) development environment. Guides them through the tools, the local stack (PostgreSQL + Keycloak), the backends (xhandler-service, optionally searchpe), the UI's Keycloak client and the first `npm run start:dev`.
targets: ["*"]
---

# Openubl UI developer setup

Walk the developer through setup ONE STEP AT A TIME. Complete each step, show the result, then
move to the next. Run commands from the repository root unless a step says `ui/`.

## Step 1 — Prerequisites

Check `git`, `node` (22+), `npm`, `java` (25), `podman` (or docker) with compose, and Google
Chrome (unit tests use it locally). Don't run privileged installs; point at nvm / the package
manager and wait.

## Step 2 — Dependencies

```bash
cd ui && npm ci   # also generates both API clients
```

## Step 3 — The stack

```bash
podman compose -f hack/compose.yaml up -d postgres keycloak   # lgtm would take port 3000
```

Then add the `xhandler-ui` client to the realm if it isn't there yet: the command is in
`ui/e2e/README.md` (section "The stack").

## Step 4 — The backends

Start xhandler-service on 8080 and, if wanted, searchpe on 8081, with the commands in
`ui/e2e/README.md` (section "The backends"). searchpe needs OIDC and authorization switched on.

## Step 5 — The UI

```bash
cd ui
cp .env.example .env   # drop SEARCHPE_API_URL to run without searchpe
npm run start:dev      # http://localhost:3000, log in as olga/olga
```

## Step 6 — Verify

`npm run lint && npm run format && npm test`. Hooks are opt-in: `npm run hooks:install`.
