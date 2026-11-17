#!/usr/bin/env bash
# Generates the TypeScript and Java SDKs from the OpenAPI document (DESIGN.md §26.2).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SPEC="${SPEC:-$ROOT/app/target/openapi/openapi.json}"
[[ -f "$SPEC" ]] || { echo "missing $SPEC: run ./mvnw -pl app package -DskipTests first" >&2; exit 1; }

rm -rf "$ROOT/sdk/typescript" "$ROOT/sdk/java"
npx --yes -p typescript@5.9 -p @hey-api/openapi-ts@0.99.0 openapi-ts -i "$SPEC" -o "$ROOT/sdk/typescript/src" -c @hey-api/client-fetch
cat > "$ROOT/sdk/typescript/package.json" <<JSON
{ "name": "@xhandler/sdk", "version": "0.1.0", "license": "Apache-2.0", "type": "module", "main": "src/index.ts" }
JSON

docker run --rm -v "$ROOT:/work" -u "$(id -u):$(id -g)" openapitools/openapi-generator-cli:v7.27.0 generate \
  -i "/work/${SPEC#"$ROOT/"}" -g java -o /work/sdk/java \
  --additional-properties=library=native,groupId=io.github.project-openubl,artifactId=xhandler-sdk,apiPackage=io.github.projectopenubl.xhandler.sdk.api,modelPackage=io.github.projectopenubl.xhandler.sdk.model,invokerPackage=io.github.projectopenubl.xhandler.sdk
echo "SDKs in sdk/typescript and sdk/java"
