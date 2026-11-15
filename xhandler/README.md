# xhandler-service

Multi-tenant SaaS for SUNAT (Peru) electronic documents: create, sign, validate, send,
track, deliver and report them. It works as the issuer's own software, as a PSE, and as
an OSE. Built with Quarkus on top of
[xhandler-rust](https://github.com/project-openubl/xhandler-rust).

- Design: [docs/DESIGN.md](docs/DESIGN.md)
- Regulatory research: [docs/research/](docs/research/README.md)
- Contributor and agent guide: [AGENTS.md](AGENTS.md)

Status: **P0, walking skeleton** (DESIGN.md §21). An invoice is built, signed and sent to
SUNAT beta through the Rust engine, traced end to end.

## Run it locally

Requirements: Java 25, Rust (installs 1.100.0 from `engine/rust-toolchain.toml`), and
Podman or Docker.

```bash
# 1. Engine sidecar (127.0.0.1:8090)
(cd engine && cargo run)

# 2. App in dev mode (http://localhost:8080; Dev Services start PostgreSQL)
./mvnw quarkus:dev

# 3. Build, sign and send the sample invoice to SUNAT beta
#    (change serie_numero for every send: SUNAT rejects repeated numbers)
curl -s -X POST http://localhost:8080/api/v1/dev/documents \
  -H 'content-type: application/json' --data @examples/invoice.json | jq
```

- Swagger UI: http://localhost:8080/q/swagger-ui
- Health: http://localhost:8080/q/health
- Metrics: http://localhost:8080/q/metrics

Traces: `podman compose -f hack/compose.yaml up -d lgtm`, then start the engine with
`OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318` and open Grafana at
http://localhost:3000.

End-to-end check (dependencies, engine, packaged app, SUNAT beta, trace):

```bash
./hack/e2e.sh
```

## License

[Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
