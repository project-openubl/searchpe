#!/usr/bin/env bash
# P0 exit criterion (DESIGN.md §21.1): an invoice is built, signed and accepted
# by SUNAT beta through the engine, and one trace spans app -> engine -> SUNAT.
#
# Needs: cargo, java 25, podman (or docker) compose, curl, jq, network access.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE="${COMPOSE:-podman compose}"
WORK="$(mktemp -d)"
PIDS=()

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
  for _ in $(seq 1 90); do
    if curl -fsS "$1" >/dev/null 2>&1; then return 0; fi
    sleep 1
  done
  echo "timed out waiting for $2" >&2
  return 1
}

echo "==> dependencies (postgres, lgtm)"
$COMPOSE -f "$ROOT/hack/compose.yaml" up -d
wait_for http://localhost:3000/api/health "grafana"

echo "==> engine"
(cd "$ROOT/engine" && cargo build --release --quiet)
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318 \
  "$ROOT/engine/target/release/xhandler-engine" >"$WORK/engine.log" 2>&1 &
PIDS+=($!)
wait_for http://localhost:8090/health "engine"

echo "==> app"
# The skeleton endpoint is a build-time switch: production builds don't include it
(cd "$ROOT" && ./mvnw -B -q package -DskipTests -Dxhandler.dev-endpoints.enabled=true)
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/xhandler \
QUARKUS_DATASOURCE_USERNAME=xhandler \
QUARKUS_DATASOURCE_PASSWORD=xhandler \
  java -jar "$ROOT/app/target/quarkus-app/quarkus-run.jar" >"$WORK/app.log" 2>&1 &
PIDS+=($!)
wait_for http://localhost:8080/q/health/ready "app"

echo "==> send an invoice to SUNAT beta"
NUMERO=$(( $(date +%s) % 99999999 ))
jq --arg sn "F001-$NUMERO" '.spec.serie_numero = $sn' "$ROOT/examples/invoice.json" >"$WORK/invoice.json"
TRACE_ID=$(od -An -tx1 -N16 /dev/urandom | tr -d ' \n')
curl -fsS -X POST http://localhost:8080/api/v1/dev/documents \
  -H 'content-type: application/json' \
  -H "traceparent: 00-$TRACE_ID-$(od -An -tx1 -N8 /dev/urandom | tr -d ' \n')-01" \
  --data @"$WORK/invoice.json" | tee "$WORK/result.json" | jq '{digest_value, status: .sunat.status, code: .sunat.code, description: .sunat.description}'

STATUS=$(jq -r '.sunat.status' "$WORK/result.json")
if [[ "$STATUS" != "accepted" ]]; then
  echo "FAIL: SUNAT status is $STATUS" >&2
  exit 1
fi

echo "==> trace $TRACE_ID"
for _ in $(seq 1 30); do
  if curl -fsS "http://localhost:3200/api/traces/$TRACE_ID" -o "$WORK/trace.json" 2>/dev/null; then
    SERVICES=$(jq -r '[.batches[]?.resource.attributes[]? | select(.key=="service.name") | .value.stringValue] | unique | join(",")' "$WORK/trace.json")
    SUNAT_SPAN=$(jq -r '[.batches[]?.scopeSpans[]?.spans[]?.name] | map(select(. == "sunat.send")) | length' "$WORK/trace.json")
    if [[ "$SERVICES" == *xhandler-engine* && "$SERVICES" == *xhandler-service* && "$SUNAT_SPAN" -ge 1 ]]; then
      echo "services: $SERVICES; sunat.send spans: $SUNAT_SPAN"
      echo "PASS"
      exit 0
    fi
  fi
  sleep 2
done
echo "FAIL: trace $TRACE_ID does not span xhandler-service -> xhandler-engine -> sunat.send" >&2
exit 1
