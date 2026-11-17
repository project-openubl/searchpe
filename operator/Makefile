# controller-gen is a tool of go.mod (go tool controller-gen): Dependabot updates it.
# golangci-lint isn't, as its authors advise: CI runs it with golangci-lint-action, keep both
# versions the same.
GOLANGCI_LINT_VERSION ?= v2.15.0

LOCALBIN ?= $(CURDIR)/bin
GOLANGCI_LINT ?= $(LOCALBIN)/golangci-lint-$(GOLANGCI_LINT_VERSION)

.DEFAULT_GOAL := help

.PHONY: help
help: ## Show this help
	@awk 'BEGIN {FS = ":.*##"} /^[a-zA-Z_-]+:.*?##/ { printf "  %-16s %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

.PHONY: generate
generate: ## Generate the DeepCopy methods and the CRD
	go tool controller-gen object:headerFile=hack/boilerplate.go.txt paths=./...
	go tool controller-gen crd paths=./... output:crd:artifacts:config=config/crd/bases

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
test: ## Run the tests
	go test ./... -race -coverprofile cover.out

$(GOLANGCI_LINT):
	mkdir -p $(LOCALBIN)
	GOBIN=$(LOCALBIN) go install github.com/golangci/golangci-lint/v2/cmd/golangci-lint@$(GOLANGCI_LINT_VERSION)
	mv $(LOCALBIN)/golangci-lint $(GOLANGCI_LINT)
