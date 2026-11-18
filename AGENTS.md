# AGENTS.md

Guidance for AI coding agents (and humans) in this monorepo: every xhandler service, built,
tested and deployed together. Read the guide of the directory you change.

## Layout

```
xhandler/   xhandler-service: app (Quarkus), engine (Rust), admin-cli, sdk, deploy, docs/DESIGN.md
            → xhandler/AGENTS.md. DESIGN.md is the source of truth for behavior.
searchpe/   RUC lookup over SUNAT's padrón reducido (Quarkus)
ui/         the one UI of both services (React 19, PatternFly 6, pulp-ui-2 blueprint) → ui/AGENTS.md
operator/   Kubernetes operator (Go, OLM): XHandler and Searchpe kinds → operator/README.md
hack/       local stack (compose.yaml: PostgreSQL, Keycloak :8180, Grafana :3001, SUNAT fake)
            and ui-e2e-stack.sh (every service, for the UI's Playwright suite)
scripts/    manifests of the CI minikube e2e
```

## Commands

```bash
./mvnw verify -pl searchpe                       # searchpe (tests need Docker or Podman)
(cd xhandler && ./mvnw verify)                   # xhandler-service (its own Maven wrapper)
(cd xhandler/engine && cargo fmt --check && cargo clippy --all-targets -- -D warnings && cargo test)
(cd operator && make lint verify-generated test bundle bundle-validate)
(cd ui && npm ci && npm run build && npm run lint && npm run format && npm test)   # build before lint
hack/ui-e2e-stack.sh build && hack/ui-e2e-stack.sh up && (cd ui && npm run test:e2e)
```

Both Quarkus apps default to port 8080; `ui-e2e-stack.sh` runs searchpe on 8081.

## Conventions

- Conventional Commits, scoped when useful (`feat(ui):`, `fix(operator):`, `ci:`).
- User-facing text (UI, API errors, emails, PDFs) is Spanish; code, logs and comments English.
  Acronyms are words (`Ruc`, `Sunat`, `Cdr`).
- Services share nothing but their APIs: each keeps its database, migrations and conventions.
- Never edit an applied migration; add a new one.
- Tests and scripts use SUNAT beta or the fake, never production.
- A behavior change updates `xhandler/docs/DESIGN.md` (or a UI ADR) in the same commit.
- Java in `searchpe/` carries the `tools/copyright.txt` header (`mvn license:format`).

## CI and releases

Every workflow runs on each push and PR; all must stay green on `main`. Required checks: JVM
Tests, Native Tests, Operator Tests, UI Tests, UI E2E Tests (minikube: operator, Searchpe and a
full XHandler), UI Image, UI Playwright, and the xhandler App, Engine, Images and Manifests jobs.

`snapshots.yml` publishes `:nightly` images to `ghcr.io/project-openubl/xhandler-service/`.
Releases run only through release-tools' `release-xhandler-service` workflow, which triggers
`release.yml`.
