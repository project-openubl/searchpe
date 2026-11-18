# xhandler-service

Multi-tenant SaaS for SUNAT (Peru) electronic documents: create, sign, validate, send,
track, deliver and report them. It works as the issuer's own software, as a PSE, and as
an OSE. Built with Quarkus on top of
[xhandler-rust](https://github.com/project-openubl/xhandler-rust).

- Design: [docs/DESIGN.md](docs/DESIGN.md)
- Regulatory research: [docs/research/](docs/research/README.md)
- Contributor and agent guide: [AGENTS.md](AGENTS.md)

## Run it locally

Requirements: Java 25, Rust (installs 1.100.0 from `engine/rust-toolchain.toml`), and
Podman or Docker.

```bash
# 1. Engine sidecar (127.0.0.1:8090)
(cd engine && cargo run)

# 2. App in dev mode (http://localhost:8080; Dev Services start PostgreSQL)
./mvnw quarkus:dev

# 3. The SUNAT fake (every new company starts there) on 127.0.0.1:8099
(cd engine && cargo run --bin sunat-fake)
```

Then sign up with a Keycloak token (dev realm users: alice/alice, bob/bob; platform admin
olga/olga) or use the UI (`../ui`), create a company and emit (see `hack/e2e.sh` for the whole flow, including the
switch to SUNAT beta). In dev mode Keycloak runs from Dev Services on a random port (see the
Dev UI); with `podman compose -f ../hack/compose.yaml up -d` it is on 8180, as below:

```bash
TOKEN=$(curl -s http://localhost:8180/realms/xhandler/protocol/openid-connect/token \
  -d grant_type=password -d client_id=xhandler-service -d client_secret=secret \
  -d username=alice -d password=alice | jq -r .access_token)
# POST /api/v1/tenants → POST /api/v1/tenants/{t}/companies → POST …/companies/{c}/documents
```

- Swagger UI: http://localhost:8080/q/swagger-ui
- Health: http://localhost:8080/q/health
- Metrics: http://localhost:8080/q/metrics

Traces: `podman compose -f ../hack/compose.yaml up -d lgtm`, then start the engine with
`OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318` and open Grafana at
http://localhost:3001.

Platform administration: `java -jar admin-cli/target/xhandler-admin.jar --help`.

End-to-end check (dependencies, engine, packaged app, SUNAT beta, trace):

```bash
./hack/e2e.sh
```

## License

[Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
