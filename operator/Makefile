# The recipes pipe commands: a failing command fails the recipe
SHELL = /usr/bin/env bash -o pipefail
.SHELLFLAGS = -ec

# The images of docker-build, deploy and bundle
IMG ?= ghcr.io/project-openubl/xhandler-service/searchpe-operator:nightly
SEARCHPE_IMAGE ?= ghcr.io/project-openubl/xhandler-service/searchpe:nightly
XHANDLER_IMAGE ?= ghcr.io/project-openubl/xhandler-service/xhandler-service:nightly
XHANDLER_ENGINE_IMAGE ?= ghcr.io/project-openubl/xhandler-service/xhandler-engine:nightly
XHANDLER_UI_IMAGE ?= ghcr.io/project-openubl/xhandler-service/xhandler-ui:nightly
EGRESS_PROXY_IMAGE ?= docker.io/ubuntu/squid:6.6-24.04_edge
# The version of the bundle, X.Y.Z (the validators of the catalogs reject 0.0.0)
VERSION ?= 0.0.1
BUNDLE_IMG ?= ghcr.io/project-openubl/xhandler-service/searchpe-operator-bundle:v$(VERSION)
CONTAINER_TOOL ?= docker

# controller-gen, kustomize and yq are tools of go.mod (go tool): Dependabot updates them.
# golangci-lint isn't, as its authors advise: CI runs it with golangci-lint-action, keep both
# versions the same. Neither is operator-sdk, which go install can't build: CI and the release
# install it with .github/actions/setup-operator-sdk, keep both versions the same.
GOLANGCI_LINT_VERSION ?= v2.15.0
OPERATOR_SDK_VERSION ?= v1.42.3
# The Kubernetes version of the API server the tests run against
ENVTEST_K8S_VERSION ?= 1.37.0
# setup-envtest is versioned by the release branches of controller-runtime
ENVTEST_VERSION ?= $(shell go list -m -f '{{.Version}}' sigs.k8s.io/controller-runtime | awk -F'[v.]' '{printf "release-%d.%d", $$2, $$3}')

LOCALBIN ?= $(CURDIR)/bin
GOLANGCI_LINT ?= $(LOCALBIN)/golangci-lint-$(GOLANGCI_LINT_VERSION)
SETUP_ENVTEST ?= $(LOCALBIN)/setup-envtest-$(ENVTEST_VERSION)
OPERATOR_SDK ?= $(LOCALBIN)/operator-sdk-$(OPERATOR_SDK_VERSION)

# manifests: the kustomization of directory $(1), with the image IMG of the operator and the
# images of its resources
define manifests
go tool kustomize build $(1) | IMG=$(IMG) SEARCHPE_IMAGE=$(SEARCHPE_IMAGE) XHANDLER_IMAGE=$(XHANDLER_IMAGE) \
	XHANDLER_ENGINE_IMAGE=$(XHANDLER_ENGINE_IMAGE) XHANDLER_UI_IMAGE=$(XHANDLER_UI_IMAGE) \
	EGRESS_PROXY_IMAGE=$(EGRESS_PROXY_IMAGE) go tool yq '\
	(select(.kind == "Deployment") | .spec.template.spec.containers[0].image) = strenv(IMG) | \
	(select(.kind == "Deployment") | .spec.template.spec.containers[0].env[] | select(.name == "RELATED_IMAGE_SEARCHPE") | .value) = strenv(SEARCHPE_IMAGE) | \
	(select(.kind == "Deployment") | .spec.template.spec.containers[0].env[] | select(.name == "RELATED_IMAGE_XHANDLER") | .value) = strenv(XHANDLER_IMAGE) | \
	(select(.kind == "Deployment") | .spec.template.spec.containers[0].env[] | select(.name == "RELATED_IMAGE_XHANDLER_ENGINE") | .value) = strenv(XHANDLER_ENGINE_IMAGE) | \
	(select(.kind == "Deployment") | .spec.template.spec.containers[0].env[] | select(.name == "RELATED_IMAGE_XHANDLER_UI") | .value) = strenv(XHANDLER_UI_IMAGE) | \
	(select(.kind == "Deployment") | .spec.template.spec.containers[0].env[] | select(.name == "RELATED_IMAGE_EGRESS_PROXY") | .value) = strenv(EGRESS_PROXY_IMAGE) | \
	(select(.kind == "ClusterServiceVersion") | .metadata.annotations.containerImage) = strenv(IMG)'
endef

.DEFAULT_GOAL := help

.PHONY: help
help: ## Show this help
	@awk 'BEGIN {FS = ":.*##"} /^[a-zA-Z_-]+:.*?##/ { printf "  %-16s %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

.PHONY: generate
generate: ## Generate the DeepCopy methods, the CRD and the RBAC role
	go tool controller-gen object:headerFile=hack/boilerplate.go.txt paths=./...
	go tool controller-gen crd paths=./... output:crd:artifacts:config=config/crd/bases
	go tool controller-gen rbac:roleName=searchpe-operator paths=./... output:rbac:artifacts:config=config/rbac

.PHONY: verify-generated
verify-generated: generate ## Fail if the generated files aren't committed
	@test -z "$$(git status --porcelain -- .)" || { git status --porcelain -- .; git diff -- .; \
		echo "Run 'make generate' and commit the result"; exit 1; }

.PHONY: lint
lint: $(GOLANGCI_LINT) ## Run golangci-lint
	$(GOLANGCI_LINT) run

.PHONY: lint-fix
lint-fix: $(GOLANGCI_LINT) ## Run golangci-lint and fix what it can
	$(GOLANGCI_LINT) run --fix

.PHONY: test
test: $(SETUP_ENVTEST) ## Run the tests, against an API server
	KUBEBUILDER_ASSETS="$$($(SETUP_ENVTEST) use $(ENVTEST_K8S_VERSION) --bin-dir $(LOCALBIN) -p path)" \
		go test ./... -race -coverprofile cover.out

.PHONY: run
run: ## Run the operator against the cluster of the current kubeconfig
	RELATED_IMAGE_SEARCHPE=$${RELATED_IMAGE_SEARCHPE:-$(SEARCHPE_IMAGE)} \
	RELATED_IMAGE_XHANDLER=$${RELATED_IMAGE_XHANDLER:-$(XHANDLER_IMAGE)} \
	RELATED_IMAGE_XHANDLER_ENGINE=$${RELATED_IMAGE_XHANDLER_ENGINE:-$(XHANDLER_ENGINE_IMAGE)} \
	RELATED_IMAGE_XHANDLER_UI=$${RELATED_IMAGE_XHANDLER_UI:-$(XHANDLER_UI_IMAGE)} \
	RELATED_IMAGE_EGRESS_PROXY=$${RELATED_IMAGE_EGRESS_PROXY:-$(EGRESS_PROXY_IMAGE)} \
		go run ./cmd

.PHONY: docker-build
docker-build: ## Build the operator image IMG
	$(CONTAINER_TOOL) build -t $(IMG) .

.PHONY: install
install: ## Install the CRD in the cluster of the current kubeconfig
	go tool kustomize build config/crd | kubectl apply --server-side -f -

.PHONY: uninstall
uninstall: ## Delete the CRD, and the Searchpe resources, from the cluster of the current kubeconfig
	go tool kustomize build config/crd | kubectl delete --ignore-not-found -f -

.PHONY: deploy
deploy: ## Deploy the operator (IMG) in the cluster of the current kubeconfig, namespace searchpe-system
	$(call manifests,config/default) | kubectl apply --server-side -f -

.PHONY: undeploy
undeploy: ## Delete what deploy deployed
	go tool kustomize build config/default | kubectl delete --ignore-not-found -f -

.PHONY: bundle
bundle: generate $(OPERATOR_SDK) ## Generate the OLM bundle of VERSION in bundle/, deploying IMG and the *_IMAGE images
	$(call manifests,config/manifests) | $(OPERATOR_SDK) generate bundle --overwrite --version $(VERSION) \
		--package searchpe-operator --channels alpha --default-channel alpha --output-dir bundle
	$(OPERATOR_SDK) bundle validate bundle

# The validators the community-operators catalogs run (check_osdk_bundle_validate_*), although
# operator-sdk deprecates the operatorhub one
.PHONY: bundle-validate
bundle-validate: $(OPERATOR_SDK) ## Validate the bundle as the OperatorHub catalogs do
	$(OPERATOR_SDK) bundle validate bundle --select-optional name=operatorhub
	$(OPERATOR_SDK) bundle validate bundle --select-optional suite=operatorframework

.PHONY: bundle-build
bundle-build: ## Build the bundle image BUNDLE_IMG
	$(CONTAINER_TOOL) build -f bundle.Dockerfile -t $(BUNDLE_IMG) .

$(GOLANGCI_LINT):
	mkdir -p $(LOCALBIN)
	GOBIN=$(LOCALBIN) go install github.com/golangci/golangci-lint/v2/cmd/golangci-lint@$(GOLANGCI_LINT_VERSION)
	mv $(LOCALBIN)/golangci-lint $(GOLANGCI_LINT)

$(SETUP_ENVTEST):
	mkdir -p $(LOCALBIN)
	GOBIN=$(LOCALBIN) go install sigs.k8s.io/controller-runtime/tools/setup-envtest@$(ENVTEST_VERSION)
	mv $(LOCALBIN)/setup-envtest $(SETUP_ENVTEST)

# Checked against the checksums of the release
$(OPERATOR_SDK):
	mkdir -p $(LOCALBIN)
	cd $(LOCALBIN) && binary=operator-sdk_$$(go env GOOS)_$$(go env GOARCH) && \
		curl -sSfLO https://github.com/operator-framework/operator-sdk/releases/download/$(OPERATOR_SDK_VERSION)/$$binary && \
		curl -sSfL https://github.com/operator-framework/operator-sdk/releases/download/$(OPERATOR_SDK_VERSION)/checksums.txt | \
			grep " $$binary$$" | sha256sum -c - && \
		chmod +x $$binary && mv $$binary $(OPERATOR_SDK)
