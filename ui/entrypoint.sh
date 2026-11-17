#!/bin/bash

set -e

# SEARCHPE_API_URL is optional: without it the UI hides searchpe.
for name in XHANDLER_API_URL OIDC_SERVER_URL; do
  if [[ -z "${!name}" ]]; then
    echo "You must provide the $name environment variable" 1>&2
    exit 1
  fi
done

if [[ -z "${NODE_EXTRA_CA_CERTS}" ]]; then
  echo "No NODE_EXTRA_CA_CERTS found"
else
  # Copy the Kube API and service CA bundle to NODE_EXTRA_CA_CERTS if they exist

  # Add Kube API CA
  if [ -f "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt" ]; then
    cp /var/run/secrets/kubernetes.io/serviceaccount/ca.crt "${NODE_EXTRA_CA_CERTS}"
  fi

  # Add service serving CA
  if [ -f "/var/run/secrets/kubernetes.io/serviceaccount/service-ca.crt" ]; then
    cat /var/run/secrets/kubernetes.io/serviceaccount/service-ca.crt >>"${NODE_EXTRA_CA_CERTS}"
  fi

  # Add custom ingress CA if it exists
  if [ -f "/etc/pki/ca-trust/extracted/pem/tls-ca-bundle.pem" ]; then
    cat /etc/pki/ca-trust/extracted/pem/tls-ca-bundle.pem >>"${NODE_EXTRA_CA_CERTS}"
  fi
fi

exec node --enable-source-maps server/dist/index.js
