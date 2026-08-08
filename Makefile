.DEFAULT_GOAL := help

MVN ?= mvnd
MVNARGS ?= -e -ntp -T 1C
JAR := target/triptale.jar
JVMFLAGS := --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow

.PHONY: run run-jar help build compile test package clean format deps major minor patch

run: ## Launch the TripTale JavaFX app (via Maven plugin)
	$(MVN) $(MVNARGS) javafx:run

$(JAR): pom.xml src ## Build the fat jar (skips tests, only when sources change)
	$(MVN) $(MVNARGS) -DskipTests package

run-jar: $(JAR) ## Run the pre-built jar directly with java (no Maven required)
	@# Filters a benign, unsuppressable JavaFX startup warning (fat jar loads JavaFX
	@# as an unnamed module — see AGENTS.md/CLAUDE.md if you ever revisit this).
	java $(JVMFLAGS) -Dlogging.level.net.timafe.triptale=INFO -jar $(JAR) 2>&1 | grep --line-buffered -v -e "Unsupported JavaFX configuration" -e "com.sun.javafx.application.PlatformImpl startup"

help: ## Show this help
	@awk 'BEGIN {FS = ":.*##"; printf "TripTale — available targets:\n\n"} \
		/^[a-zA-Z_-]+:.*##/ { printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

build: ## Compile and package (skips tests)
	$(MVN) $(MVNARGS) -DskipTests package

compile: ## Compile sources only
	$(MVN) $(MVNARGS) compile

test: ## Run unit tests
	$(MVN) $(MVNARGS) test

package: ## Build the jar (runs tests)
	$(MVN) $(MVNARGS) package

app: build ## Build a macOS .app bundle via jpackage (target/dist/TripTale.app)
	packaging/macos/build-app.sh

clean: ## Remove target/ build output
	$(MVN) $(MVNARGS) clean

deps: ## Print resolved dependency tree
	$(MVN) $(MVNARGS) dependency:tree

define semtag_check
	@if ! command -v semtag >/dev/null 2>&1; then \
		echo "semtag not found in PATH."; \
		echo "Install it with: brew install semtag  (or see https://github.com/nico2sh/semtag)"; \
		echo "Then run again."; \
		exit 1; \
	fi
endef

major: ## Bump major version tag via semtag and push to remote
	$(call semtag_check)
	@echo "Next version will be: $$(semtag final -s major -o)"
	@printf "Press any key to tag and push, or Ctrl-C to abort... " && read -r _dummy
	semtag final -s major

minor: ## Bump minor version tag via semtag and push to remote
	$(call semtag_check)
	@echo "Next version will be: $$(semtag final -s minor -o)"
	@printf "Press any key to tag and push, or Ctrl-C to abort... " && read -r _dummy
	semtag final -s minor

patch: ## Bump patch version tag via semtag and push to remote
	$(call semtag_check)
	@echo "Next version will be: $$(semtag final -s patch -o)"
	@printf "Press any key to tag and push, or Ctrl-C to abort... " && read -r _dummy
	semtag final -s patch
