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
gets, with the field manager `xhandler` and owned by it (deleting it deletes them):

| Resource                                       | Name                       | When                                   |
|------------------------------------------------|----------------------------|----------------------------------------|
| Migration Job (`QUARKUS_INIT_AND_EXIT`, owner credentials) | `<name>-migrate-<hash of the image>` | always                       |
| api Deployment, Service and PodDisruptionBudget | `<name>-api`              | Deployment: once the Job has succeeded |
| worker Deployment and PodDisruptionBudget      | `<name>-worker`            | Deployment: once the Job has succeeded |
| UI Deployment, Service and PodDisruptionBudget | `<name>-ui`                | `ui.enabled` (default)                 |
| Ingress                                        | `<name>`                   | `ingress.enabled` (default)            |
| Squid ConfigMap, Deployment, Service, PodDisruptionBudget | `<name>-egress-proxy` | `egressProxy.enabled`              |
| NetworkPolicies                                | `<name>-default-deny`, `-ingress`, `-egress`, `-migrate`, `-egress-proxy` | `networkPolicies.enabled` |

- **Migrations first.** The database is migrated by a Job per image, as the schema owner (`db.ownerUsernameSecret`,
  `db.ownerPasswordSecret`); the app logs in as a non-owner member of `xhandler_app` (`db.usernameSecret`,
  `db.passwordSecret`). The api and worker Deployments are applied only once the Job of their image has succeeded:
  after a new image, the old pods keep running (expand/contract) until then. A Job's template can't change, so it is
  created once; a failed Job stays, for its logs, until you delete it, and then a new one runs. Once a Job succeeds,
  those of the previous images are deleted.
- **Pods.** The api and worker pods run the app with the engine as a native sidecar (Kubernetes 1.29 or newer), spread
  across zones (`topology.kubernetes.io/zone`, `DoNotSchedule`: label your nodes on clusters without zones, such as
  kind) and nodes, with 150 s to finish in-flight SUNAT calls.
- **Ingress.** With the UI, `/` goes to the UI and what other clients call (`/api`, `/ose`, SUNAT's SOAP path
  `/ol-ti-itcpfegem`, `/q/openapi`, `/q/swagger-ui`) to the api; without it, everything goes to the api. `tls.secret`
  terminates TLS at the ingress. `publicUrl` defaults to the URL of `hostname`.
- **Egress proxy.** With `egressProxy.enabled`, the engine reaches SUNAT and the third-party OSEs, and the app the
  webhooks, through Squid, which allows only HTTPS to public addresses. Without it the app refuses webhooks, unless
  `XHANDLER_EGRESS_REQUIRE_PROXY=false` in `additionalOptions`.
- **Network policies.** They port `xhandler/deploy/base/networkpolicy.yaml`. The api and UI accept port 8080 from the
  pods of `networkPolicies.ingressNamespaces` (default `envoy-gateway-system` and `monitoring`: add your ingress
  controller's); PostgreSQL is the pods of `networkPolicies.databasePodSelector`, or port 5432 anywhere without it.
  Private-address dependencies (a network HSM, a private Keycloak or S3) need policies of your own.
- **UI.** It gets `XHANDLER_API_URL` (the api Service), `OIDC_SERVER_URL` and `OIDC_CLIENT_ID` (`oidc`), and
  `SEARCHPE_API_URL`, the Service of the Searchpe of `ui.searchpeRef`; the UI waits for that Searchpe to exist.
- **Configuration.** The spec maps to the app's properties (`QUARKUS_DATASOURCE_*`, `QUARKUS_OIDC_*`,
  `XHANDLER_VAULT_PKCS11_*`, `XHANDLER_STORAGE_S3_*`, `XHANDLER_OSE_*`, `XHANDLER_PUBLIC_URL`) and to the engine's
  (`XHANDLER_SUNAT_FAKE_URL`). `vault.moduleVolume` mounts the HSM vendor's PKCS#11 module at `vault.moduleMountPath`
  (`/opt/hsm`). `additionalOptions` sets any other variable of the app, replacing the operator's of the same name.
- **Status.** `Migrated` is true once the Job of the current image has succeeded; `Ready` once the Deployments are
  available, otherwise its reason says why not (`ImageMissing`, `Migrating`, `MigrationFailed`, `SearchpeNotFound`,
  `Progressing`, `Error`).

The images default to the operator's `RELATED_IMAGE_XHANDLER`, `RELATED_IMAGE_XHANDLER_ENGINE`,
`RELATED_IMAGE_XHANDLER_UI` and `RELATED_IMAGE_EGRESS_PROXY` (`XHANDLER_IMAGE`, `XHANDLER_ENGINE_IMAGE`,
`XHANDLER_UI_IMAGE` and `EGRESS_PROXY_IMAGE` of `make deploy`, `make bundle` and `make run`). Unlike
`RELATED_IMAGE_SEARCHPE`, they are optional: an `XHandler` that needs a missing one is not deployed, and its `Ready`
condition says which. `SEARCHPE_IMAGE_PULL_POLICY` applies to every image.

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
