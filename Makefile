.DEFAULT_GOAL := help

MVN ?= mvn

.PHONY: run help build compile test package clean format deps

run: ## Launch the TripTale JavaFX app
	$(MVN) javafx:run

help: ## Show this help
	@awk 'BEGIN {FS = ":.*##"; printf "TripTale — available targets:\n\n"} \
		/^[a-zA-Z_-]+:.*##/ { printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

build: ## Compile and package (skips tests)
	$(MVN) -DskipTests package

compile: ## Compile sources only
	$(MVN) compile

test: ## Run unit tests
	$(MVN) test

package: ## Build the jar (runs tests)
	$(MVN) package

clean: ## Remove target/ build output
	$(MVN) clean

deps: ## Print resolved dependency tree
	$(MVN) dependency:tree
