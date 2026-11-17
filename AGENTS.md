# AGENTS.md

Guidance for AI coding agents (and humans) working in this repository: every service of the
xhandler platform, built and deployed together.

## Layout

```
searchpe/     searchpe: RUC lookup over SUNAT's padrón reducido (Quarkus, Java 25)
xhandler/     xhandler-service: SUNAT electronic documents — app (Quarkus), engine (Rust), admin-cli,
              sdk, deploy, docs/DESIGN.md. Its own guide: xhandler/AGENTS.md
operator/     Kubernetes operator (Go, controller-runtime, OLM bundle) for every service
hack/         the local stack: PostgreSQL (one database per service), Keycloak, Grafana LGTM, SUNAT fake
scripts/      manifests used by the CI end-to-end test (OLM catalog, PostgreSQL, sample resources)
pom.xml       Maven aggregator: searchpe (inherits it) and xhandler (its own parent)
```

Read the guide of the directory you change: `xhandler/AGENTS.md` (the design is
`xhandler/docs/DESIGN.md`), `operator/README.md`.

## Commands

```bash
./mvnw verify                                   # every Java service (tests need Docker or Podman)
./mvnw verify -pl searchpe                      # only searchpe
(cd xhandler && ./mvnw verify)                  # only xhandler-service
(cd xhandler/engine && cargo fmt --check && cargo clippy --all-targets -- -D warnings && cargo test)
(cd operator && make lint verify-generated test)
podman compose -f hack/compose.yaml up -d       # the local stack (Keycloak on 8180)
```

Both Quarkus apps default to port 8080: run one of them with `-Dquarkus.http.port=8081` when
running both. Against the local stack, searchpe uses its database and the shared realm:

```bash
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/searchpe \
QUARKUS_DATASOURCE_USERNAME=searchpe QUARKUS_DATASOURCE_PASSWORD=searchpe \
QUARKUS_OIDC_AUTH_SERVER_URL=http://localhost:8180/realms/xhandler \
QUARKUS_OIDC_CLIENT_ID=searchpe QUARKUS_OIDC_CREDENTIALS_SECRET=secret ./mvnw quarkus:dev -pl searchpe
```

Run Maven for xhandler from `xhandler/` (`(cd xhandler && ./mvnw ...)`): it has its own wrapper.

## Conventions

- Conventional Commits with a scope when it helps (`feat(operator):`, `fix(searchpe):`, `ci:`).
- Changes land on `main` directly or through pull requests; either way the required checks of
  `.github/workflows/` must pass.
- Java in `searchpe/` carries the license header of `tools/copyright.txt` (`mvn license:format`);
  `xhandler/` follows its own guide.
- Each service keeps its own database, migrations and conventions; nothing crosses service
  directories except through their APIs.
- CI: `.github/workflows/ci.yml` (searchpe and the operator) and `xhandler*.yml` (xhandler-service),
  filtered by path. Every workflow must stay green on main.
