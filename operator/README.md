# Searchpe operator

The Kubernetes operator of Searchpe and xhandler-service, written in Go with
[controller-runtime](https://github.com/kubernetes-sigs/controller-runtime). OLM installs it from OperatorHub. It
reconciles two kinds:

- `Searchpe` (`searchpe.openubl.io/v1alpha1`): runs the server with a Deployment and exposes it with a Service and an
  Ingress.
- `XHandler` (`xhandler.openubl.io/v1alpha1`): runs [xhandler-service](../xhandler) as
  [`xhandler/deploy/base`](../xhandler/deploy/base) describes it (see below).

| Path                     | What                                                                                   |
|--------------------------|----------------------------------------------------------------------------------------|
| `api/v1alpha1`           | The `Searchpe` API. The CRD in `config/crd` is generated from it                       |
| `api/xhandler/v1alpha1`  | The `XHandler` API, likewise                                                           |
| `internal/controller`    | The controllers: `searchpe_controller.go` and `xhandler_controller.go`                 |
| `cmd`                    | The operator's entry point                                                             |
| `config`                 | The kustomize manifests of the operator; `config/manifests` is the base of its bundle  |

You need Go, make, Docker or Podman (`CONTAINER_TOOL=podman`) and kubectl: the other tools are `go tool` dependencies,
or make downloads them to `bin/`. Run `make` to list the targets.

## XHandler

An `XHandler` (sample: [`config/samples/xhandler_v1alpha1_xhandler.yaml`](config/samples/xhandler_v1alpha1_xhandler.yaml))
gets, with the field manager `xhandler`, the label `app.kubernetes.io/managed-by: xhandler-operator` and owned by it
(deleting it deletes them):

| Resource                                       | Name                       | When                                   |
|------------------------------------------------|----------------------------|----------------------------------------|
| Migration Job (`QUARKUS_INIT_AND_EXIT`, owner credentials) | `<name>-migrate-<hash>` | always                                |
| api Deployment, Service and PodDisruptionBudget | `<name>-api`              | Deployment: once the Job has succeeded |
| worker Deployment and PodDisruptionBudget      | `<name>-worker`            | Deployment: once the Job has succeeded |
| UI Deployment, Service and PodDisruptionBudget | `<name>-ui`                | `ui.enabled` (default)                 |
| Ingress                                        | `<name>-xhandler`          | `ingress.enabled` (default)            |
| Squid ConfigMap, Deployment, Service, PodDisruptionBudget | `<name>-egress-proxy` | `egressProxy.enabled`              |
| NetworkPolicies                                | `<name>-default-deny`, `-ingress`, `-egress`, `-migrate`, `-egress-proxy` | `networkPolicies.enabled` |

The name of an `XHandler` is a DNS-1035 label (it starts with a letter) of at most 40 characters.

- **Migrations first.** The database is migrated by a Job, as the schema owner (`db.ownerUsernameSecret`,
  `db.ownerPasswordSecret`); the app logs in as a non-owner member of `xhandler_app` (`db.usernameSecret`,
  `db.passwordSecret`). The Job's name hashes the image, `db.url` and the owner's secret references (name and key): a
  new image, database or owner runs a new Job. The api and worker Deployments are applied only once the Job has
  succeeded: after a new image, the old pods keep running (expand/contract) until then. A Job's template can't change,
  so it is created once (later `additionalOptions` reach the next Job); a failed Job stays, for its logs, until you
  delete it, and then a new one runs. Once a Job succeeds, those of the previous migrations are deleted. A succeeded Job
  deleted by someone else is not run again: the api and worker Deployments record the hash they run after (annotation
  `xhandler.openubl.io/migration`).
- **Pin the images by digest.** The gate keys on the image reference: with a mutable tag (`:nightly`) and
  `SEARCHPE_IMAGE_PULL_POLICY=Always`, a restarted pod can pull a new build whose migrations never ran. Use
  `image@sha256:…` in production, in the spec and in the operator's `RELATED_IMAGE_*`.
- **Pods.** The api and worker pods run the app with the engine as a native sidecar (Kubernetes 1.29 or newer), with
  150 s to finish in-flight SUNAT calls. The app has a startup probe (`/q/health/started`, up to 150 s) before its
  liveness probe counts. The api, worker, UI and proxy pods spread across nodes and zones (`topology.kubernetes.io/zone`):
  `zoneSpread: Preferred` (default, `ScheduleAnyway`) or `Required` (`DoNotSchedule`: label your nodes on clusters
  without zones, such as kind).
- **Ingress.** With the UI, `/` and the APIs the UI server proxies (`/api/xhandler`, `/api/searchpe`) go to the UI, and
  what other clients call to the api: `/api/v1`, `/api/admin`, `/ose`, SUNAT's SOAP path `/ol-ti-itcpfegem`,
  `/q/openapi` (and `/q/openapi.json`) and `/q/swagger-ui`. Without the UI, only those API paths: never `/` nor the
  rest of `/q` (metrics, health). `ingress.className` sets the IngressClass; the ingress-nginx annotations allow 16 MB
  bodies (the OSE's SOAP intake) and 150 s reads and sends (a synchronous SUNAT send). `tls.secret` terminates TLS at
  the ingress. `publicUrl` defaults to the URL of `hostname`.
- **Egress proxy.** With `egressProxy.enabled`, the engine reaches SUNAT and the third-party OSEs, and the app the
  webhooks, through Squid, which allows only HTTPS to public addresses. Without it the app refuses webhooks, unless
  `XHANDLER_EGRESS_REQUIRE_PROXY=false` in `additionalOptions`. Enabled, the proxy starts before the pods that use it;
  disabled, it stays until the api and worker Deployments no longer refer to it (after a pending migration, if any).
  Its image (`ubuntu/squid`) runs as root by default: the operator runs it as UID 31 (`egressProxy.runAsUser`); on
  OpenShift (`restricted-v2`), set a UID of the namespace's range (annotation `openshift.io/sa.scc.uid-range`). It is
  pulled `IfNotPresent`.
- **Network policies.** They port `xhandler/deploy/base/networkpolicy.yaml`. The api and UI accept port 8080 from the
  pods of `networkPolicies.ingressNamespaces`, which is required when they are enabled: your ingress controller's
  namespace (`ingress-nginx`; `openshift-ingress` on OpenShift, whose routers on the host network also need a policy of
  your own for the host network) and your monitoring's. PostgreSQL is the pods of `networkPolicies.databasePodSelector`,
  or port 5432 anywhere without it. Private-address dependencies (the SUNAT fake of `sunatFakeUrl`, a network HSM, a
  private Keycloak or S3) go in `networkPolicies.additionalEgress`, rules appended to the api and worker egress policy:

  ```yaml
  networkPolicies:
    enabled: true
    ingressNamespaces: [ingress-nginx]
    additionalEgress:
      - to: [{ podSelector: { matchLabels: { app.kubernetes.io/name: sunat-fake } } }]
        ports: [{ port: 8099, protocol: TCP }]
  ```
- **UI.** It gets `XHANDLER_API_URL` (the api Service), `OIDC_SERVER_URL` (`oidc.serverUrl`, required with the UI) and
  `OIDC_CLIENT_ID` (`ui.oidcClientId`, default `xhandler-ui`). That client is public, with PKCE; xhandler-service only
  accepts tokens for its own client (`oidc.clientId`), so give the UI client an audience mapper that adds it (Keycloak:
  client scopes, mapper "Audience", included client audience `oidc.clientId`). With `ui.searchpeRef`, it also gets
  `SEARCHPE_API_URL`, the Service of that Searchpe; while the Searchpe doesn't exist (or after it is deleted) the UI
  runs without it and `Ready` is false with reason `SearchpeNotFound`. A Searchpe with TLS has a certificate the UI
  (Node.js) must trust: put its CA in a ConfigMap and set `ui.caBundleConfigMap` (`name`, `key`), which the UI gets as
  `NODE_EXTRA_CA_CERTS`. Its probes are `GET /healthz`.
- **Configuration.** The spec maps to the app's properties (`QUARKUS_DATASOURCE_*`, `QUARKUS_OIDC_*`,
  `XHANDLER_VAULT_PKCS11_*`, `XHANDLER_STORAGE_S3_*`, `XHANDLER_OSE_*`, `XHANDLER_PUBLIC_URL`) and to the engine's
  (`XHANDLER_SUNAT_FAKE_URL`). `vault.moduleVolume` mounts the HSM vendor's PKCS#11 module at `vault.moduleMountPath`
  (`/opt/hsm`). `additionalOptions` sets any other variable of the app, and of the migration Job (except the variables
  that make it migrate and log in as the owner), replacing the operator's of the same name; `engine.additionalOptions`
  does the same for the engine. `ose.ruc` needs `ose.environment` `beta` or `production`.
- **Telemetry.** `otel.endpoint`, an OTLP over HTTP endpoint (`http://otel-collector:4318`), is the engine's
  `OTEL_EXPORTER_OTLP_ENDPOINT` and the app's `QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT` (with the `http/protobuf`
  protocol). Without it the app's OpenTelemetry SDK is disabled (`QUARKUS_OTEL_SDK_DISABLED`) and the engine exports
  nothing.
- **Status.** `Migrated` is true once the Job of the current migration has succeeded; `Ready` once the Deployments are
  available, otherwise its reason says why not (`ImageMissing`, `Migrating`, `MigrationFailed`, `SearchpeNotFound`,
  `Progressing`, `Error`).

The images default to the operator's `RELATED_IMAGE_XHANDLER`, `RELATED_IMAGE_XHANDLER_ENGINE`,
`RELATED_IMAGE_XHANDLER_UI` and `RELATED_IMAGE_EGRESS_PROXY` (`XHANDLER_IMAGE`, `XHANDLER_ENGINE_IMAGE`,
`XHANDLER_UI_IMAGE` and `EGRESS_PROXY_IMAGE` of `make deploy`, `make bundle` and `make run`; the `:nightly` images of
`ghcr.io/project-openubl/searchpe` by default). Unlike `RELATED_IMAGE_SEARCHPE`, they are optional: an `XHandler` that
needs a missing (empty) one is not deployed, and its `Ready` condition (`ImageMissing`) says which.
`SEARCHPE_IMAGE_PULL_POLICY` applies to the Searchpe image and, now, to the xhandler-service, engine and UI images too;
the egress proxy image is pulled `IfNotPresent`.

The operator caches only the ConfigMaps, Jobs and NetworkPolicies with the label
`app.kubernetes.io/managed-by: xhandler-operator`, which it reads for the `XHandler` resources only.

### Upgrade

- Install the new CRDs and RBAC before the new manager image (OLM does it; with kustomize, `make install` and
  `make deploy`): the manager watches the `XHandler` kind and its resources, and fails without them.
- The bundle requires Kubernetes 1.29 (`minKubeVersion`), for the native sidecars of the api and worker pods.

## Develop

```shell
make generate   # after changing the API or the kubebuilder markers
make lint verify-generated test bundle bundle-validate  # what CI runs; the tests run against a Kubernetes API server
```

The API must stay compatible with the resources users have: `api/v1alpha1/crd_test.go` checks the CRD keeps every field
of the CRD of the Java operator, the previous implementation. `internal/controller/upgrade_test.go` checks that taking
over the resources the Java operator created changes nothing, which would restart Searchpe or log its users out.

### Run against a cluster

With [minikube](https://minikube.sigs.k8s.io/), for instance:

```shell
minikube start --addons=ingress
make install run   # the CRDs, and the operator on your machine: keep it running, go on in another terminal
```

Searchpe needs PostgreSQL. With the [Crunchy PostgreSQL operator](https://operatorhub.io/operator/postgresql), as CI does:

```shell
curl -sL https://github.com/operator-framework/operator-lifecycle-manager/releases/download/v0.46.0/install.sh | bash -s v0.46.0
kubectl create -f https://operatorhub.io/install/postgresql.yaml
kubectl create namespace openubl
kubectl apply -n openubl -f ../scripts/postgresql.yaml   # wait for its pods to be ready
kubectl apply -n openubl -f ../scripts/searchpe.yaml
```

To run the operator in the cluster instead, build its image where the cluster can pull it, and deploy it to the
`searchpe-system` namespace:

```shell
make docker-build deploy IMG=<registry>/searchpe-operator:dev
```

### Test the OLM bundle

```shell
make docker-build IMG=<registry>/searchpe-operator:dev
make bundle bundle-validate bundle-build IMG=<registry>/searchpe-operator:dev BUNDLE_IMG=<registry>/searchpe-operator-bundle:dev
docker push <registry>/searchpe-operator:dev && docker push <registry>/searchpe-operator-bundle:dev  # or podman
bin/operator-sdk-v* run bundle <registry>/searchpe-operator-bundle:dev --install-mode OwnNamespace --namespace openubl  # the operator-sdk of make bundle
```

## Release

The release workflow (`.github/workflows/release.yml`) publishes the image and attaches the bundle to the GitHub
release; release-tools opens the OperatorHub pull requests from it.
