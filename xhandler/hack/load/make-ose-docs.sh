#!/usr/bin/env bash
# Pre-signed invoices for the OSE load test: built and signed with the engine's demo key
# (the engine must run on 127.0.0.1:8090: `cd engine && cargo run`).
#   make-ose-docs.sh RUC COUNT OUTPUT_DIR [SERIE]
set -euo pipefail
ruc=${1:?RUC}; count=${2:?COUNT}; out=${3:?OUTPUT_DIR}; serie=${4:-F$(printf '%03d' $((RANDOM % 1000)))}
engine=${ENGINE_URL:-http://127.0.0.1:8090}
mkdir -p "$out"; : > "$out/index.txt"
for n in $(seq 1 "$count"); do
  doc=$(jq -nc --arg s "$serie-$n" --arg ruc "$ruc" '{document: {kind: "Invoice", spec: {serie_numero: $s,
    proveedor: {ruc: $ruc, razon_social: "EMISOR DE CARGA SAC"},
    cliente: {tipo_documento_identidad: "6", numero_documento_identidad: "20111111111", nombre: "CLIENTE SAC"},
    detalles: [{descripcion: "Producto", cantidad: 1, precio: 100}]}}}')
  xml=$(curl -sf "$engine/v1/build" -H 'Content-Type: application/json' -d "$doc" | jq -r .xml)
  signed=$(jq -nc --arg x "$xml" '{xml: $x, demo_key: true}' | curl -sf "$engine/v1/sign" -H 'Content-Type: application/json' -d @- | jq -r .xml)
  name="$ruc-01-$serie-$n"
  printf '%s' "$signed" > "$out/$name.xml"
  (cd "$out" && zip -q "$name.zip" "$name.xml" && rm "$name.xml")
  echo "$name" >> "$out/index.txt"
done
echo "$count documents in $out (serie $serie)"
