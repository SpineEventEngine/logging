# Project: Spine Logging

## Overview

Spine Logging is the logging library of the Spine Event Engine SDK. It offers a
fluent, Kotlin-first logging API for Kotlin and Java projects, inspired by
[Google Flogger][flogger] and the fluent API introduced in SLF4J 2.0. Application
code logs against one stable façade while log delivery is handled by a pluggable
backend, so every Spine SDK repository shares the same logging API regardless of
the underlying logging framework.

## Architecture

Role in the org: a foundational **library** consumed by the other Spine SDK repos;
it sits near the bottom of the dependency graph.

- The public API (`spine-logging`) is backend-agnostic. Concrete backends are
  separate, swappable modules selected at runtime: JUL is the default, Log4j2 is
  also provided, and an OpenTelemetry backend is being added.
- The code targets the JVM today but is structured for Kotlin Multiplatform: keep
  platform-neutral code in `commonMain` and JVM-specific code in `jvmMain`/`jvmTest`,
  and avoid JVM-only APIs in shared sources unless unavoidable.
- Being a low-level dependency, the library keeps its public surface small and stable
  and avoids pulling in heavy third-party dependencies.

Read [`.agents/guidelines/jvm-project.md`](.agents/guidelines/jvm-project.md) for the
build stack, coding style, tests, and versioning.

[flogger]: https://github.com/google/flogger
