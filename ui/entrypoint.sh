#!/bin/bash

set -euo pipefail

# SEARCHPE_API_URL is optional: without it the UI hides searchpe.
for name in XHANDLER_API_URL OIDC_SERVER_URL; do
  if [[ -z "${!name:-}" ]]; then
    echo "You must provide the $name environment variable" 1>&2
    exit 1
  fi
done

# Node trusts its bundled roots plus NODE_EXTRA_CA_CERTS. Build that file in a writable place
# (the root filesystem may be read-only; /tmp is expected to be an emptyDir) from the system
# bundle, OpenShift's service-serving CA (backends behind service certificates) and, if set, the
# file mounted at NODE_EXTRA_CA_CERTS. The mounted file is only read, never written.
ca_bundle="${TMPDIR:-/tmp}/ca-bundle.pem"
: >"${ca_bundle}"

append_ca() {
  if [[ -r "$1" ]]; then
    cat "$1" >>"${ca_bundle}"
    echo >>"${ca_bundle}"
  fi
}

append_ca /etc/pki/ca-trust/extracted/pem/tls-ca-bundle.pem
append_ca /var/run/secrets/kubernetes.io/serviceaccount/service-ca.crt
if [[ -n "${NODE_EXTRA_CA_CERTS:-}" ]]; then
  if [[ -r "${NODE_EXTRA_CA_CERTS}" ]]; then
    append_ca "${NODE_EXTRA_CA_CERTS}"
  else
    echo "NODE_EXTRA_CA_CERTS (${NODE_EXTRA_CA_CERTS}) is not readable; ignoring it" 1>&2
  fi
fi

export NODE_EXTRA_CA_CERTS="${ca_bundle}"

exec node --enable-source-maps server/dist/index.js
