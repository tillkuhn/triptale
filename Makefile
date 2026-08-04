.DEFAULT_GOAL := help

MVN ?= mvnd
MVNARGS ?= -e -ntp -T 1C
JAR := target/triptale.jar
JVMFLAGS := --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow

.PHONY: run run-jar help build compile test package clean format deps

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

clean: ## Remove target/ build output
	$(MVN) $(MVNARGS) clean

deps: ## Print resolved dependency tree
	$(MVN) $(MVNARGS) dependency:tree
