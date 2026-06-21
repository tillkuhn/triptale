# GraalVM Native Image Evaluation

**Date:** 2026-06-21  
**App:** TripTale (Spring Boot 4 + JavaFX 23 + JGit 7)

---

## Potential Gains

In theory, a native image would provide:

- **Startup time:** Spring Boot + JavaFX currently takes several seconds to initialize; a native image would start in milliseconds.
- **Memory:** RSS could drop from ~150–300 MB to ~50–80 MB.
- **Distribution:** a single self-contained binary, no JDK required.

For a desktop GUI app these are _nice_, but not critical — users launch it once and keep it open. The tradeoff calculus is very different from a CLI tool or microservice where startup and memory are first-class concerns.

---

## Blockers

### 1. JavaFX + FXMLLoader (Critical)

The biggest problem. `FXMLLoader` discovers widget classes from `<?import?>` declarations and binds `@FXML` fields and `onAction` methods **entirely by reflection at runtime**. OpenJFX ships no GraalVM reflection metadata. The only known toolchain for JavaFX native image is [Gluon's `gluonfx-maven-plugin`](https://gluonhq.com/products/gluonfx/), which targets **iOS/Android**, not the desktop. Desktop JavaFX native image is experimental/unsupported.

A workaround exists — build the scene graph programmatically in Java and remove `FXMLLoader` entirely — but that means rewriting `main.fxml` and much of `MainController`.

### 2. JGit + Apache Mina SSHD (High)

JGit uses `ServiceLoader`, reflection-based algorithm discovery, and security provider loading. It ships **no GraalVM metadata**. A large hand-crafted `reflect-config.json` would be needed covering JGit internals, Mina SSHD cipher/MAC/key-exchange providers, and BouncyCastle. This is non-trivial and fragile across JGit version bumps.

### 3. Java 25 on GraalVM (Medium)

GraalVM CE/EE typically lags behind OpenJDK releases. GraalVM for Java 25 may be limited or not yet production-ready. Pinning to Java 21 LTS would likely be required.

---

## What Would Work Well

| Component | Native Image Friendliness |
|---|---|
| Spring Boot 4 AOT | Good — Boot 4 has strong native support |
| `spring.main.web-application-type: none` | Good — no servlet container to deal with |
| Jackson YAML + JSR-310 | Good — ships GraalVM metadata since 2.13 |
| `ProcessBuilder` for `git push/pull` | No issue at all |
| Export templates (classpath resources) | Trivial `resource-config.json` entry |

---

## Verdict

**Not worth pursuing** for this application as-is. The startup-time benefit matters little for a desktop app opened once per session, the memory benefit is modest, and the JavaFX/JGit blockers would require either rewriting the UI layer or maintaining a large, brittle native image configuration that breaks on every dependency update.

If revisiting in the future, the prerequisite changes would be:

1. Replace FXML-based loading with a programmatic scene graph (eliminates `FXMLLoader` reflection).
2. Drop to Java 21 LTS (GraalVM support is stable there).
3. Replace JGit's SSH transport with the OS `git` binary for all operations — push/pull already do this; extending it to all git ops removes the Mina SSHD reflection problem entirely.
