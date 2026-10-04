# 43 Startup time & memory footprint

Goal: faster startup and a lower memory footprint, especially on smaller devices.

## Measurements (2026-10-04)

Intel Mac (x86_64), Temurin 25.0.4, Spring Boot 4.0.3, JavaFX 23.0.1. Each variant was run
twice and measured once the main window had loaded the last entry. "Spring" is Spring's
`Started application in …`, "process" is the `process running for …` value in the same log line.

| Variant                                    | Spring  | Process   | RSS     |
|--------------------------------------------|---------|-----------|---------|
| Fat jar (`make run-jar`), baseline         | ~1.5 s  | 3.5–4.5 s | ~655 MB |
| Extracted jar                              | ~1.5 s  | ~4.2 s    | ~640 MB |
| **Extracted jar + AOT cache (`run-fast`)** | ~0.55 s | ~2.8 s    | ~630 MB |
| … + `spring.main.lazy-initialization=true` | ~0.48 s | ~2.8 s    | ~636 MB |
| … + `-XX:+UseSerialGC -XX:MaxRAMPercentage=10` | ~0.6 s | ~3.0 s | ~655 MB |

Takeaways:

- About 2.3 s of startup happens **before Spring starts**: JVM boot plus JavaFX toolkit
  init. Spring itself is a small part of the total.
- The JDK AOT cache is the only change that made a clear difference (~1.5 s).
- None of the GC/heap flags reduced RSS. Memory is mostly native (the JavaFX/Prism Metal
  pipeline) plus heap headroom, not Spring.

## Implemented

### 1. JDK 25 AOT cache — `make run-fast`

This uses Project Leyden (JEP 483 AOT class loading & linking, JEP 514 one-step
`-XX:AOTCacheOutput`, JEP 515 method profiles). It needs no code changes beyond the launcher
below.

- The AOT cache can't use the nested Spring Boot fat jar, so the jar is extracted to an exploded
  layout first: `java -Djarmode=tools -jar target/triptale.jar extract --destination target/aot`.
- A **training run** starts the app with `-XX:AOTCacheOutput=…` and
  `-Dtriptale.aot-training=true`. With that property set, `TripTaleApplication.start()` quits
  the app 3 s after the window shows, so the JVM exits normally and writes the cache (~67 MB).
  No user interaction is needed.
- Later runs pass `-XX:AOTCache=…`.

Make targets (they behave the same on macOS, Linux and Windows/Git Bash):

| Target           | What it does                                                            |
|------------------|-------------------------------------------------------------------------|
| `make run-fast`  | Builds the jar, extracts it and trains if needed, then runs with the cache |
| `make aot-train` | Forces a fresh training run                                             |

Staleness is handled automatically:
- a rebuilt `target/triptale.jar` triggers re-extraction and retraining (normal make deps);
- the cache file name includes the exact JDK build (`app-jdk25.0.4+7-LTS.aot`), so a JDK
  upgrade also triggers retraining. If the JVM is ever handed a mismatched cache anyway, it
  logs a warning and starts without it. It never fails.

Known limits:
- JGit jars are **signed**, so their classes are skipped ("Signed JAR") and are not cached.
  Neither are JDK proxies or a few multi-release classes from spring-core. All harmless; the
  training run's log is set to `-Xlog:aot=error` to hide these warnings.
- The training run uses the real data dir. It only does what a normal start does (open the
  last trip, write `.state.yml`).

#### Using it on a machine without Maven (Linux release jar)

The release jar (`triptale-linux.jar`) still runs the classic way (`java -jar …`). To get the
faster startup there, only a JDK 25 is needed:

```bash
FLAGS="--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow"
java -Djarmode=tools -jar triptale-linux.jar extract --destination ~/triptale-aot
java $FLAGS -XX:AOTCacheOutput=$HOME/triptale-aot/app.aot -Dtriptale.aot-training=true \
     -jar ~/triptale-aot/triptale-linux.jar          # once; window closes by itself
java $FLAGS -XX:AOTCache=$HOME/triptale-aot/app.aot -jar ~/triptale-aot/triptale-linux.jar
```

Repeat the first two steps after downloading a new release or upgrading the JDK.

### `Launcher` main class (prerequisite for #1)

The extracted jar runs JavaFX from the classpath. When the main class extends
`javafx.application.Application`, the JDK launcher then aborts with *"JavaFX runtime components
are missing"*. The fat jar never hit this check because Spring Boot's `JarLauncher` is its real
`Main-Class`. `net.timafe.triptale.Launcher` is now a plain main class: it sets the locale and
calls `Application.launch(TripTaleApplication.class, …)`. The pom's `main.class` points to it,
so the fat jar, the extracted jar and `javafx:run` all start the same way.
`TripTaleApplication.main` still exists for IDE run configs and delegates to it.

### 2. Host-only JavaFX natives

`pom.xml` used to hard-code `<javafx.platform>win</javafx.platform>` as the classifier. openjfx's
own parent pom *also* detects the build host's OS, so every jar carried two sets of natives
(macOS build: `-win` + `-mac`, CI: `-win` + `-linux`). The JavaFX dependencies now have no
classifier, and openjfx picks exactly one set for the build host:

- jar size: 37 MB → 28 MB (macOS build);
- **a jar runs on the OS it was built on.** CI's release job builds on `ubuntu-latest`, so
  `triptale-linux.jar` contains linux (x86_64) natives as before. macOS and Windows build
  locally. Simulated with `MAVEN_OPTS="-Dos.name=… -Dos.arch=amd64" mvn dependency:list`:
  Linux → `linux`, Windows → `win`. The release jar has never included aarch64 Linux natives
  (e.g. a Raspberry Pi), and still doesn't.
- `javafx.platform=win` survives as a property inside the `windows-javafx` profile only,
  because that profile's `copy-dependencies` step filters by it.

## Evaluated, not worth it (yet)

- **`spring.main.lazy-initialization`**: saves ~60 ms, because the app has very few beans.
- **GC/heap flags** (`UseSerialGC`, `MaxRAMPercentage`, `TieredStopAtLevel=1`): no RSS
  reduction, and no startup gain worth keeping.
- **Spring AOT (`process-aot`)**: with the AOT cache, Spring's context refresh is already
  ~0.5 s, so it has little left to save, and it constrains conditional beans and profiles.
- **GraalVM native-image**: JavaFX needs GluonFX, and JGit, Jackson and Spring need
  reflection configs. That's a big effort; see `01_native-image-eval.md`.
- **Baeldung's tips** (`-Xverify:none` / `-noverify` etc.): written for Spring Boot 2.x, and
  those flags are deprecated or ignored on JDK 25.

## Possible next steps

- **Start Spring in parallel with the JavaFX toolkit**: kick off the Spring context on a
  background thread in `Launcher.main` and join it in `init()`. This could hide most of the
  remaining ~0.5 s of Spring time behind toolkit startup.
- **Memory**: measure an explicit `-Xmx256m` and use `-XX:NativeMemoryTracking=summary`
  (`jcmd <pid> VM.native_memory`) to see what dominates the ~650 MB RSS before tuning anything.
- **macOS `.app` bundle**: `jpackage` could ship the extracted layout and pass
  `-XX:AOTCache` via `--java-options` (see `05_macos-app-bundle.md`). The cache would have to
  be trained against the bundled runtime.
