# Install & run 🛠️

Requires **Java 25**. Building also needs Maven (`make` uses `mvnd` if it's installed, plain `mvn` otherwise).

```bash
make run        # build + launch via Maven — best while developing
make run-fast   # launch with a JDK AOT cache — ~1.5 s faster startup, best for daily use
make build      # build target/triptale.jar without running tests
make test       # run unit tests
make app        # macOS only: build TripTale.app (target/dist/)
make check-update  # compare your version with the latest GitHub release
make help       # list all targets
```

`make run-fast` rebuilds the jar when the sources changed. On its first run (and after every rebuild or JDK upgrade) a window opens and **closes by itself** after a few seconds: that's a training run recording the cache. Let it finish; the real app opens right after. Details and measurements: [docs/43_startup_performance.md](../43_startup_performance.md).

**A jar runs only on the OS it was built on** — it bundles that platform's JavaFX native libraries. On macOS and Windows, build it yourself.

## Linux without Maven

Every release on GitHub has a prebuilt `triptale-linux.jar` (x86_64). You only need a JDK 25:

```bash
java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -jar triptale-linux.jar
```

For the faster AOT startup there, see "Using it on a machine without Maven" in [docs/43_startup_performance.md](../43_startup_performance.md).

## Windows notes

- `make` needs a POSIX shell (e.g. Git Bash) and uses plain `mvn` on Windows. `make run` automatically activates the `windows-javafx` Maven profile, which puts the JavaFX jars on the module path. Without `make`: `mvn -Pwindows-javafx javafx:run`.
- `make run-jar` / `make run-fast` work the same as on macOS and Linux.

Java may print warnings about restricted native access or `sun.misc.Unsafe` while JavaFX loads, and one about an "Unsupported JavaFX configuration". They're harmless; the `make` targets filter the latter.

---

Back to the [guides](README.md) · [project README](../../README.md)
