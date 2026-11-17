#!/usr/bin/env bash
# End-to-end check against SUNAT beta (DESIGN.md §21.1):
#   P0: a document is built, signed and accepted by SUNAT beta through the
#       engine, and one trace spans app -> engine -> SUNAT.
#   P1: through the real API with a Keycloak token: sign up, create a company,
#       upload its certificate, then sign through the key vault (engine
#       prepare -> vault -> engine finalize).
#   P3: emit through POST /documents (numbered, validated, signed, stored),
#       the reconciler and a worker send it to SUNAT beta, and the document
#       ends accepted; the attempt's trace spans app -> engine -> SUNAT.
#
# Needs: cargo, java 25, podman (or docker) compose, curl, jq, network access.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE="${COMPOSE:-podman compose}"
WORK="$(mktemp -d)"
PIDS=()
RUC=20123456789
PFX="$ROOT/app/src/test/resources/certificates/valid-$RUC.pfx"

cleanup() {
  local status=$?
  if [[ $status -ne 0 ]]; then
    for log in "$WORK"/*.log; do
      [[ -f "$log" ]] || continue
      echo "---- $(basename "$log") (last 50 lines) ----" >&2
      tail -n 50 "$log" >&2
    done
  fi
  for pid in "${PIDS[@]}"; do kill "$pid" 2>/dev/null || true; done
  $COMPOSE -f "$ROOT/hack/compose.yaml" down >/dev/null 2>&1 || true
}
trap cleanup EXIT

wait_for() { # url, name
  for _ in $(seq 1 120); do
    if curl -fsS "$1" >/dev/null 2>&1; then return 0; fi
    sleep 1
  done
  echo "timed out waiting for $2" >&2
  return 1
}

echo "==> dependencies (postgres, keycloak, lgtm)"
$COMPOSE -f "$ROOT/hack/compose.yaml" up -d
wait_for http://localhost:3000/api/health "grafana"
wait_for http://localhost:8180/realms/xhandler/.well-known/openid-configuration "keycloak"

echo "==> engine"
(cd "$ROOT/engine" && cargo build --release --quiet)
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318 \
  "$ROOT/engine/target/release/xhandler-engine" >"$WORK/engine.log" 2>&1 &
PIDS+=($!)
wait_for http://localhost:8090/health "engine"

echo "==> app"
(cd "$ROOT" && ./mvnw -B -q package -DskipTests)
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/xhandler \
QUARKUS_DATASOURCE_USERNAME=xhandler_login \
QUARKUS_DATASOURCE_PASSWORD=xhandler_login \
QUARKUS_FLYWAY_USERNAME=xhandler_owner \
QUARKUS_FLYWAY_PASSWORD=xhandler_owner \
QUARKUS_OIDC_AUTH_SERVER_URL=http://localhost:8180/realms/xhandler \
XHANDLER_VAULT_KIND=software \
XHANDLER_VAULT_SOFTWARE_ALLOW_IN_PRODUCTION=true \
XHANDLER_VAULT_SOFTWARE_CURRENT_MASTER_KEY=e2e \
XHANDLER_VAULT_SOFTWARE_MASTER_KEYS_E2E="$(head -c 32 /dev/urandom | base64)" \
XHANDLER_STORAGE_KIND=filesystem \
XHANDLER_STORAGE_DIR="$WORK/files" \
XHANDLER_STORAGE_S3_ALLOW_FILESYSTEM_IN_PRODUCTION=true \
  java -jar "$ROOT/app/target/quarkus-app/quarkus-run.jar" >"$WORK/app.log" 2>&1 &
PIDS+=($!)
wait_for http://localhost:8080/q/health/ready "app"

echo "==> sign up and set up a company (alice)"
TOKEN=$(curl -fsS http://localhost:8180/realms/xhandler/protocol/openid-connect/token \
  -d grant_type=password -d client_id=xhandler-service -d client_secret=secret \
  -d username=alice -d password=alice | jq -r .access_token)
api() { curl -fsS -H "authorization: Bearer $TOKEN" "$@"; }
TENANT=$(api -X POST http://localhost:8080/api/v1/tenants -H 'content-type: application/json' \
  -d '{"name":"Empresa de Prueba"}' | jq -r .id)
COMPANY=$(api -X POST "http://localhost:8080/api/v1/tenants/$TENANT/companies" -H 'content-type: application/json' \
  -d "{\"ruc\":\"$RUC\",\"razon_social\":\"Empresa de Prueba S.A.C.\"}" | jq -r .id)
api -X PUT "http://localhost:8080/api/v1/tenants/$TENANT/companies/$COMPANY/certificate" \
  -F "file=@$PFX" -F password=password | jq -c '{ruc, ruc_matches}'

echo "==> switch to SUNAT beta, with a series of our own"
# SUNAT beta's demo RUC is shared by everyone: start numbering somewhere unused
api -X POST "http://localhost:8080/api/v1/tenants/$TENANT/companies/$COMPANY/environment" -H 'content-type: application/json' \
  -d '{"sunat_env":"beta"}' | jq -c '{sunat_env}'
SERIE="F$(( RANDOM % 900 + 100 ))"
api -X POST "http://localhost:8080/api/v1/tenants/$TENANT/companies/$COMPANY/series" -H 'content-type: application/json' \
  -d "{\"tipo_comprobante\":\"01\",\"serie\":\"$SERIE\",\"next_number\":$(( $(date +%s) % 99999999 ))}" | jq -c '{serie, next_number}'

echo "==> emit: numbered, validated and signed through the vault; sent by a worker"
jq --arg serie "$SERIE" '{document: (.spec |= (del(.serie_numero) | del(.proveedor))), serie: $serie}' \
  "$ROOT/examples/invoice.json" >"$WORK/emit.json"
api -X POST "http://localhost:8080/api/v1/tenants/$TENANT/companies/$COMPANY/documents" \
  -H 'content-type: application/json' -H "Idempotency-Key: $(uuidgen 2>/dev/null || date +%s%N)" \
  --data @"$WORK/emit.json" | tee "$WORK/emitted.json" | jq -c '{serie_numero, status, digest_value}'
DOCUMENT=$(jq -r .id "$WORK/emitted.json")

STATUS=pending_send
for _ in $(seq 1 90); do
  STATUS=$(api "http://localhost:8080/api/v1/tenants/$TENANT/documents/$DOCUMENT" | tee "$WORK/document.json" | jq -r .status)
  case "$STATUS" in pending_send|in_progress|unknown) sleep 2 ;; *) break ;; esac
done
jq -c '{serie_numero, status, sunat_code, sunat_description}' "$WORK/document.json"
if [[ "$STATUS" != "accepted" && "$STATUS" != "accepted_with_observations" ]]; then
  echo "FAIL: document status is $STATUS" >&2
  exit 1
fi

echo "==> trace of the attempt"
QUERY=$(jq -rn --arg id "$DOCUMENT" '"{ span.xhandler.document_id = \"" + $id + "\" }" | @uri')
for _ in $(seq 1 30); do
  TRACE_ID=$(curl -fsS "http://localhost:3200/api/search?q=$QUERY" 2>/dev/null | jq -r '.traces[0].traceID // empty')
  if [[ -n "$TRACE_ID" ]] && curl -fsS "http://localhost:3200/api/traces/$TRACE_ID" -o "$WORK/trace.json" 2>/dev/null; then
    SERVICES=$(jq -r '[.batches[]?.resource.attributes[]? | select(.key=="service.name") | .value.stringValue] | unique | join(",")' "$WORK/trace.json")
    SUNAT_SPAN=$(jq -r '[.batches[]?.scopeSpans[]?.spans[]?.name] | map(select(. == "sunat.send")) | length' "$WORK/trace.json")
    if [[ "$SERVICES" == *xhandler-engine* && "$SERVICES" == *xhandler-service* && "$SUNAT_SPAN" -ge 1 ]]; then
      echo "trace $TRACE_ID; services: $SERVICES; sunat.send spans: $SUNAT_SPAN"
      echo "PASS"
      exit 0
    fi
  fi
  sleep 2
done
echo "FAIL: no trace of the attempt spans xhandler-service -> xhandler-engine -> sunat.send" >&2
exit 1
