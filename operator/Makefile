# controller-gen is a tool of go.mod (go tool controller-gen): Dependabot updates it.
# golangci-lint isn't, as its authors advise: CI runs it with golangci-lint-action, keep both
# versions the same.
GOLANGCI_LINT_VERSION ?= v2.15.0
# The Kubernetes version of the API server the tests run against
ENVTEST_K8S_VERSION ?= 1.37.0
# setup-envtest is versioned by the release branches of controller-runtime
ENVTEST_VERSION ?= $(shell go list -m -f '{{.Version}}' sigs.k8s.io/controller-runtime | awk -F'[v.]' '{printf "release-%d.%d", $$2, $$3}')

LOCALBIN ?= $(CURDIR)/bin
GOLANGCI_LINT ?= $(LOCALBIN)/golangci-lint-$(GOLANGCI_LINT_VERSION)
SETUP_ENVTEST ?= $(LOCALBIN)/setup-envtest-$(ENVTEST_VERSION)

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
	RELATED_IMAGE_SEARCHPE=$${RELATED_IMAGE_SEARCHPE:-ghcr.io/project-openubl/searchpe/searchpe:nightly} go run ./cmd

$(GOLANGCI_LINT):
	mkdir -p $(LOCALBIN)
	GOBIN=$(LOCALBIN) go install github.com/golangci/golangci-lint/v2/cmd/golangci-lint@$(GOLANGCI_LINT_VERSION)
	mv $(LOCALBIN)/golangci-lint $(GOLANGCI_LINT)

$(SETUP_ENVTEST):
	mkdir -p $(LOCALBIN)
	GOBIN=$(LOCALBIN) go install sigs.k8s.io/controller-runtime/tools/setup-envtest@$(ENVTEST_VERSION)
	mv $(LOCALBIN)/setup-envtest $(SETUP_ENVTEST)
