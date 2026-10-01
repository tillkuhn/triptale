.DEFAULT_GOAL := help

TOFU ?= tofu
# Written by `tofu init`; re-init when the backend/provider config changes
INIT := .terraform/terraform.tfstate
TFVARS := $(wildcard *.auto.tfvars)

.PHONY: help init fmt validate plan apply output key clean

help: ## Show this help
	@awk 'BEGIN {FS = ":.*##"; printf "TripTale infra (OpenTofu) — available targets:\n\n"} \
		/^[a-zA-Z_-]+:.*##/ { printf "  \033[36m%-10s\033[0m %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

$(INIT): versions.tf $(TFVARS)
	@if [ -z "$(TFVARS)" ]; then \
		echo "No *.auto.tfvars found — cp triptale.auto.tfvars.example triptale.auto.tfvars and fill it in."; \
		exit 1; \
	fi
	$(TOFU) init -input=false
	@touch $@

init: $(INIT) ## Initialize providers and the S3 state backend (only when config changed)

fmt: ## Format all .tf files
	$(TOFU) fmt

validate: $(INIT) ## Check formatting and validate the configuration
	$(TOFU) fmt -check
	$(TOFU) validate

plan: $(INIT) ## Show what apply would change
	$(TOFU) plan

apply: $(INIT) ## Apply changes (shows the plan and asks for confirmation)
	$(TOFU) apply

output: $(INIT) ## Show all outputs (secret masked)
	$(TOFU) output

key: $(INIT) ## Reveal the app's access key ID and secret (for TripTale settings)
	@echo "access_key_id:     $$($(TOFU) output -raw access_key_id)"
	@echo "secret_access_key: $$($(TOFU) output -raw secret_access_key)"

clean: ## Remove the local .terraform/ working dir (state stays in S3)
	rm -rf .terraform
