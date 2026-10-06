.DEFAULT_GOAL := help

ifeq ($(OS),Windows_NT)
MVN ?= mvn
else
MVN ?= $(if $(shell command -v mvnd 2>/dev/null),mvnd,mvn)
endif
MVNARGS ?= -e -ntp -T 1C
JAR := target/triptale.jar
JVMFLAGS := --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow
# Filters a benign, unsuppressable JavaFX startup warning (JavaFX is loaded from the
# classpath as an unnamed module — see AGENTS.md/CLAUDE.md if you ever revisit this).
FXFILTER := grep --line-buffered -v -e "Unsupported JavaFX configuration" -e "com.sun.javafx.application.PlatformImpl startup"

# AOT cache (JDK 25, JEP 483/514/515): the jar is extracted to an exploded layout (a nested
# fat jar can't be cached) and one training run records loaded/linked classes + profiles.
# The cache file name embeds the exact JDK build, so a JDK upgrade retrains automatically;
# a rebuilt jar retrains via the normal make dependency. See docs/43_startup_performance.md.
AOT_DIR := target/aot
AOT_JAR := $(AOT_DIR)/triptale.jar
JDK_BUILD := $(shell java -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.runtime.version/{print $$2}')
AOT_CACHE := $(AOT_DIR)/app-jdk$(JDK_BUILD).aot

.PHONY: run run-jar run-fast aot-train help build compile test package clean format deps major minor patch

# -Dmvnd.rawStreams: mvnd otherwise prefixes every app log line with "[INFO] [stdout] ".
# Plain mvn just sees an unused system property.
ifeq ($(OS),Windows_NT)
RUN_CMD := $(MVN) $(MVNARGS) -Dmvnd.rawStreams=true -Pwindows-javafx javafx:run
else
RUN_CMD := $(MVN) $(MVNARGS) -Dmvnd.rawStreams=true javafx:run
endif

run: ## Launch the TripTale JavaFX app (via Maven plugin)
	$(RUN_CMD)

# Pure-Make recursive file listing (no external `find`): on Windows, Git's PATH puts
# System32's find.exe (the DOS text-search command) ahead of GNU find, so `find src -type f`
# fails with "FIND: Parameterformat falsch". $(wildcard) is a Make built-in, immune to that.
# Don't match directories with a trailing-slash glob (`dir/*/`): GNU Make 3.81 (macOS
# /usr/bin/make) also returns plain files for it, which recursed forever and segfaulted make.
# Directories end up in the list too; harmless as prerequisites.
rwildcard = $(foreach d,$(wildcard $(1:=/*)),$(call rwildcard,$d,$2) $(filter $(subst *,%,$2),$d))
SOURCES := $(call rwildcard,src,*) pom.xml

$(JAR): $(SOURCES) ## Build the fat jar (skips tests, only when sources change)
	$(MVN) $(MVNARGS) -DskipTests package

run-jar: $(JAR) ## Run the pre-built jar directly with java (no Maven required)
	java $(JVMFLAGS) -Dlogging.level.net.timafe.triptale=INFO -jar $(JAR) 2>&1 | $(FXFILTER)

$(AOT_JAR): $(JAR)
	rm -rf $(AOT_DIR)
	java -Djarmode=tools -jar $(JAR) extract --destination $(AOT_DIR)

$(AOT_CACHE): $(AOT_JAR)
	@echo "AOT training run: the window opens and closes by itself after a few seconds..."
	rm -f $(AOT_DIR)/*.aot
	java $(JVMFLAGS) -XX:AOTCacheOutput=$@ -Xlog:aot=error -Dtriptale.aot-training=true -jar $(AOT_JAR) 2>&1 | $(FXFILTER)
	@test -f $@ || { echo "error: training run did not produce $@" >&2; exit 1; }

aot-train: ## Force a fresh AOT training run (normally automatic, see run-fast)
	rm -f $(AOT_DIR)/*.aot
	$(MAKE) $(AOT_CACHE)

run-fast: $(AOT_CACHE) ## Run with the JDK AOT cache (~1.5 s faster start; builds/trains on demand)
	java $(JVMFLAGS) -XX:AOTCache=$(AOT_CACHE) -Dlogging.level.net.timafe.triptale=INFO -jar $(AOT_JAR) 2>&1 | $(FXFILTER)

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
