#!/usr/bin/env bash
# The stack the UI's Playwright suite runs against (ui/e2e/README.md), from built artifacts:
#   compose: PostgreSQL and Keycloak (hack/compose.yaml)
#   processes: the SUNAT fake (8099), the engine (8090), xhandler-service (8080), searchpe (8081)
#
#   hack/ui-e2e-stack.sh build   # mvn package (xhandler app, searchpe) and cargo build --release
#   hack/ui-e2e-stack.sh up      # starts everything, waits until healthy
#   hack/ui-e2e-stack.sh down    # stops the processes (and the compose stack with --compose)
#
# Logs and pids go to $STACK_DIR (default /tmp/xhandler-ui-e2e). The database outlives `down`
# unless the compose stack is removed: the suite must pass against it run after run.
# Needs: java 25, cargo, podman (or docker) compose, curl.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE="${COMPOSE:-podman compose}"
STACK_DIR="${STACK_DIR:-/tmp/xhandler-ui-e2e}"
ENGINE_BIN="$ROOT/xhandler/engine/target/release"

wait_for() { # url, name, seconds
  for _ in $(seq 1 "${3:-180}"); do
    if curl -fsS "$1" >/dev/null 2>&1; then return 0; fi
    sleep 1
  done
  echo "timed out waiting for $2 ($1); its log:" >&2
  tail -n 80 "$STACK_DIR/$2.log" >&2 2>/dev/null || true
  return 1
}

start() { # name, command...
  local name=$1
  shift
  "$@" >"$STACK_DIR/$name.log" 2>&1 &
  echo $! >"$STACK_DIR/$name.pid"
}

build() {
  (cd "$ROOT/xhandler" && ./mvnw -B -q package -DskipTests -pl app -am)
  (cd "$ROOT" && ./mvnw -B -q package -DskipTests -pl searchpe)
  (cd "$ROOT/xhandler/engine" && cargo build --release --bins)
}

up() {
  mkdir -p "$STACK_DIR/files"
  $COMPOSE -f "$ROOT/hack/compose.yaml" up -d postgres keycloak
  wait_for http://localhost:8180/realms/xhandler/.well-known/openid-configuration keycloak 300

  start sunat-fake "$ENGINE_BIN/sunat-fake" --addr 127.0.0.1:8099
  start engine "$ENGINE_BIN/xhandler-engine"
  wait_for http://localhost:8090/health engine

  # Production mode with the dev conveniences the UI's specs rely on: the software key vault,
  # file storage, the mailer mock and the CAPTCHA test mode (token "test-pass").
  start xhandler env \
    QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/xhandler \
    QUARKUS_DATASOURCE_USERNAME=xhandler_login \
    QUARKUS_DATASOURCE_PASSWORD=xhandler_login \
    QUARKUS_FLYWAY_USERNAME=xhandler_owner \
    QUARKUS_FLYWAY_PASSWORD=xhandler_owner \
    QUARKUS_OIDC_AUTH_SERVER_URL=http://localhost:8180/realms/xhandler \
    QUARKUS_OIDC_CREDENTIALS_SECRET=secret \
    QUARKUS_OTEL_SDK_DISABLED=true \
    QUARKUS_MAILER_MOCK=true \
    XHANDLER_CAPTCHA_TEST_MODE=true \
    XHANDLER_VAULT_KIND=software \
    XHANDLER_VAULT_SOFTWARE_ALLOW_IN_PRODUCTION=true \
    XHANDLER_VAULT_SOFTWARE_CURRENT_MASTER_KEY=e2e \
    XHANDLER_VAULT_SOFTWARE_MASTER_KEYS_E2E=dWktZTJlLW1hc3Rlci1rZXktMDAwMDAwMDAwMDAwMDA= \
    XHANDLER_STORAGE_KIND=filesystem \
    XHANDLER_STORAGE_DIR="$STACK_DIR/files" \
    XHANDLER_STORAGE_S3_ALLOW_FILESYSTEM_IN_PRODUCTION=true \
    XHANDLER_LIMITS_MAX_UNVERIFIED_COMPANIES=100000 \
    XHANDLER_BILLING_DEFAULT_PLAN=enterprise \
    XHANDLER_EGRESS_REQUIRE_PROXY=false \
    java -jar "$ROOT/xhandler/app/target/quarkus-app/quarkus-run.jar"

  # searchpe with OIDC and authorization on (its defaults turn both off) and the advanced search.
  start searchpe env \
    QUARKUS_HTTP_PORT=8081 \
    QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/searchpe \
    QUARKUS_DATASOURCE_USERNAME=searchpe \
    QUARKUS_DATASOURCE_PASSWORD=searchpe \
    QUARKUS_OIDC_TENANT_ENABLED=true \
    QUARKUS_OIDC_AUTH_SERVER_URL=http://localhost:8180/realms/xhandler \
    QUARKUS_OIDC_CLIENT_ID=searchpe \
    QUARKUS_OIDC_CREDENTIALS_SECRET=secret \
    QUARKUS_HTTP_AUTH_SESSION_ENCRYPTION_KEY=N50xtHmAqRfL0CO7Yej5RvRsvQKeyRcesVDJc8a0Wq0= \
    SEARCHPE_DISABLE_AUTHORIZATION=false \
    SEARCHPE_ALLOW_ADVANCEDSEARCH=true \
    SEARCHPE_WORKSPACE_DIRECTORY="$STACK_DIR/searchpe-workspace" \
    java -jar "$ROOT/searchpe/target/quarkus-app/quarkus-run.jar"

  wait_for http://localhost:8080/q/health/ready xhandler
  wait_for http://localhost:8081/q/health/ready searchpe
  echo "stack up: xhandler :8080, searchpe :8081, engine :8090, SUNAT fake :8099, Keycloak :8180"
}

down() {
  for pid in "$STACK_DIR"/*.pid; do
    [[ -f "$pid" ]] || continue
    kill "$(cat "$pid")" 2>/dev/null || true
    rm -f "$pid"
  done
  if [[ "${1:-}" == "--compose" ]]; then
    $COMPOSE -f "$ROOT/hack/compose.yaml" down
  fi
}

case "${1:-}" in
  build) build ;;
  up) up ;;
  down) down "${2:-}" ;;
  *)
    echo "usage: $0 build | up | down [--compose]" >&2
    exit 2
    ;;
esac
