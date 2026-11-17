# AGENTS.md

Guidance for AI coding agents (and humans) working in **xhandler-service**, the `xhandler/`
directory of this repository (see the root [AGENTS.md](../AGENTS.md) for the other services).
Paths and commands below are relative to `xhandler/`.

## What

Multi-tenant SaaS for SUNAT (Peru) electronic documents. A Quarkus app (`app/`) does
everything except the SUNAT documents themselves. Those are handled by a Rust sidecar
(`engine/`) built on [xhandler-rust](https://github.com/project-openubl/xhandler-rust).

**The design is the source of truth: [docs/DESIGN.md](docs/DESIGN.md).** Read the
relevant section before changing behavior. A design change updates DESIGN.md in the same
commit. Regulatory background is in [docs/research/](docs/research/README.md).

## Layout

```
app/              Quarkus application (Java 25, Maven)
admin-cli/        xhandler-admin: Picocli client of the admin API
engine/           Rust sidecar: build, sign, send (axum; xhandler-rust pinned by rev)
deploy/base/      Kustomize base: one pod = app + engine sidecar
hack/             e2e.sh and PostgreSQL roles (the stack itself is the root ../hack/compose.yaml)
examples/         Sample documents (xbuilder JSON)
docs/             DESIGN.md and research
```

## Commands

```bash
./mvnw quarkus:dev                     # app in dev mode (Dev Services start PostgreSQL)
./mvnw verify                          # app tests (needs Docker or Podman)
(cd engine && cargo run)               # engine on 127.0.0.1:8090
(cd engine && cargo test)              # engine tests
(cd engine && cargo test -- --ignored) # also sends to SUNAT beta (network)
(cd engine && cargo fmt --check && cargo clippy --all-targets -- -D warnings)
./hack/e2e.sh                          # full flow against SUNAT beta, checks the trace
```

Rust 1.100.0 is pinned in `engine/rust-toolchain.toml`. Java 25. The engine builds against
libxml2 (`libxml2-dev`, `libclang-dev`, `pkg-config`).

```bash
(cd engine && cargo run --bin sunat-fake)  # the SUNAT fake on 127.0.0.1:8099 (environment "fake")
java -jar admin-cli/target/xhandler-admin.jar --help   # xhandler-admin (after ./mvnw package)
```

## Conventions

- **Language**:
  - SUNAT and domain concepts in Spanish (`Detalle`, `tipoComprobante`, `serieNumero`).
  - Infrastructure in English (`Job`, `Worker`, `…Service`, `…Repository`, `…Exception`).
  - **User-facing text in Spanish**: UI, API error messages, emails, PDFs. Code, logs and comments in English.
- **Acronyms are words**: `Igv`, `Sunat`, `Ruc`, `Cdr`, `Xml` (in Java and Rust).
- **Java**:
  - Google Java Style.
  - Records for data passed between components.
  - CDI beans are stateless (`@ApplicationScoped` with no mutable fields).
  - Entities and sessions never cross threads (DESIGN.md §10.8).
- **API errors** for clients are `{ kind, message (Spanish), detail }`; internal details are logged, not returned.
- **JSON** is snake_case everywhere (`quarkus.jackson.property-naming-strategy=SNAKE_CASE`), like xhandler-rust's models.
- **Engine**:
  - stateless; no global mutable state;
  - errors are `{ "kind", "message" }` with `kind` in `invalid_document | invalid_certificate | wrong_password | invalid_request | other` (422, except `other`: 500);
  - send outcomes: `unknown` means SUNAT may have the document (ask before resending), `not_delivered` means it never reached SUNAT;
  - timeouts nest: app → engine read timeout > engine → SUNAT timeout.
- **Tenancy (DESIGN.md §6)**:
  - every `@Transactional` method touching tenant data calls `TenantAccess.enter(tenantId, minimumRole)` first;
  - PostgreSQL row-level security does the isolation: the app runs as `xhandler_app` (never as the owner or a superuser; `DatabaseRoleCheck`), and a query without an open tenant sees no rows;
  - cross-tenant lookups are `SECURITY DEFINER` SQL functions in the migrations, never ad-hoc queries;
  - never edit an applied migration (`V1`, `V2`, …): add a new one. "Applied" includes your own
    local test database (Dev Services reuse it), so once a test run has migrated it, add a new
    migration instead of editing;
  - foreign and unknown tenants are both 404.
- **Secrets and keys**:
  - private keys only through `KeyVault` (an HSM in production; `SoftwareKeyVault` for dev and tests only);
  - secrets only through `CredentialService`/`SecretBox`;
  - never log or return them; reads of secrets are audited (`Auditor`).
- **Tests** use real Keycloak tokens (`Api.as("alice")`) and Dev Services PostgreSQL; they must pass against a database that outlives the run (random RUCs, no fixed ids).
- **Concurrency rules** (DESIGN.md §10.8) apply to any change touching jobs, documents or numbering.
- **SUNAT environments**:
  - tests and e2e use **SUNAT beta** only;
  - never send to production from tests or scripts.
- **Commits**:
  - Conventional Commits (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `ci:`, `chore:`).
  - Commits go straight to `main` (no PRs in this repo). Each phase (DESIGN.md §21) is followed by a senior-engineer review.
