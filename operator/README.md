# Searchpe operator

The Kubernetes operator of Searchpe, written in Go with [controller-runtime](https://github.com/kubernetes-sigs/controller-runtime).
For each `Searchpe` resource (`searchpe.openubl.io/v1alpha1`) it runs the server with a Deployment and exposes it with a
Service and an Ingress. OLM installs it from OperatorHub.

| Path                     | What                                                                                   |
|--------------------------|----------------------------------------------------------------------------------------|
| `api/v1alpha1`           | The `Searchpe` API. The CRD in `config/crd` is generated from it                       |
| `internal/controller`    | The controller                                                                         |
| `cmd`                    | The operator's entry point                                                             |
| `config`                 | The kustomize manifests of the operator; `config/manifests` is the base of its bundle  |

You need Go, make, Docker or Podman (`CONTAINER_TOOL=podman`) and kubectl: the other tools are `go tool` dependencies,
or make downloads them to `bin/`. Run `make` to list the targets.

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
make install run   # the CRD, and the operator on your machine: keep it running, go on in another terminal
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
