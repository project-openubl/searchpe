# Quick start

## Minikube

Start minikube

```shell
minikube start --kubernetes-version
minikube addons enable ingress
```

## PostgreSQL

Follow instructions from https://operatorhub.io/operator/postgresql

```shell
curl -sL https://github.com/operator-framework/operator-lifecycle-manager/releases/download/v0.22.0/install.sh | bash -s v0.22.0
kubectl create -f https://operatorhub.io/install/postgresql.yaml
kubectl get csv -n operators
```

Instantiate database:

```shell
cat << EOF | kubectl apply -f -
apiVersion: postgres-operator.crunchydata.com/v1beta1
kind: PostgresCluster
metadata:
  name: postgresql
spec:
  postgresVersion: 14
  instances:
    - name: pg-1
      replicas: 1
      dataVolumeClaimSpec:
        accessModes:
          - ReadWriteOnce
        resources:
          requests:
            storage: 1Gi
  backups:
    pgbackrest:
      repos:
      - name: repo1
        volume:
          volumeClaimSpec:
            accessModes:
            - ReadWriteOnce
            resources:
              requests:
                storage: 1Gi
  users:
    - name: foo
      databases:
        - searchpedb
      options: "SUPERUSER"
EOF
```

### Init Quarkus

This Operator will be installed in the "operators" namespace and will be usable from all namespaces in the cluster.

Create the CRDs:

```shell
mvn clean package
kubectl apply -f target/kubernetes/searchpes.searchpe.openubl.io-v1.yml
```

Start the project in dev mode:

```shell
mvn compile quarkus:dev
```

Enable debug in your IDE and then instantiate the operator:

```shell
kubectl apply -f src/main/resources/searchpe.yml
```

### Publish Operator

Create operator container:

```shell
mvn clean package \
-Dquarkus.native.container-build=true \
-Dquarkus.container-image.build=true \
-Dquarkus.container-image.push=false \
-Dquarkus.container-image.registry=quay.io \
-Dquarkus.container-image.group=projectopenubl \
-Dquarkus.container-image.name=searchpe-operator \
-Dquarkus.operator-sdk.bundle.package-name=searchpe-operator \
-Dquarkus.operator-sdk.bundle.channels=alpha \
-Dquarkus.application.version=test \
-P native
podman push quay.io/$USER/searchpe-operator:nightly
```

Create bundle:

```shell
BUNDLE_IMAGE=quay.io/$USER/searchpe-operator-bundle:test
podman build -t $BUNDLE_IMAGE -f target/bundle/searchpe-operator/bundle.Dockerfile target/bundle/searchpe-operator
podman push $BUNDLE_IMAGE
```

Create catalog image:

```shell
CATALOG_IMAGE=quay.io/$USER/searchpe-operator-catalog:nightly
opm index add \
    --bundles $BUNDLE_IMAGE \
    --tag $CATALOG_IMAGE \
    --build-tool podman
podman push $CATALOG_IMAGE
```

Create catalog:

```shell
cat <<EOF | kubectl apply -f -
apiVersion: operators.coreos.com/v1alpha1
kind: CatalogSource
metadata:
  name: searchpe-catalog-source
  namespace: openshift-marketplace
spec:
  sourceType: grpc
  image: $CATALOG_IMAGE
EOF
```

Verify:

```shell
kubectl get csv -n operators searchpe-operator
```