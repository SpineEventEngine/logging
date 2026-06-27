---
slug: otel-backend-implementation
branch: otel
owner: claude
status: in-progress
started: 2026-06-27
related-memories: []
---

## Goal

Ship an OpenTelemetry logger backend for Spine Logging that maps `LogData` to OTel
log records (`Logger.emit(...)`) and, on top of the same call path, a log-based
*events* surface. Success = an app can route Spine logs to an OTel pipeline by
adding one backend artifact and pointing it at an `OpenTelemetry` instance; the
record carries severity, message, log-site, throwable, Spine scope/log-site
metadata as attributes, and active-span trace correlation; all proven with tests.

The companion research spec is [`otel-backend-report.md`](otel-backend-report.md)
(referred to below as "the report"). **This plan corrects several factual errors
in the report** (see *Context → Corrections*); where the two disagree, this plan
wins.

## Context

The plan is grounded in three sources, all read on 2026-06-27:

1. **The Spine SPI** (verified `confirmed` against source) — the report describes it
   accurately:
   - [`BackendFactory.kt:81`](logging/src/commonMain/kotlin/io/spine/logging/backend/BackendFactory.kt) — `abstract fun create(loggingClass: String): LoggerBackend`
   - [`LoggerBackend.kt`](logging/src/commonMain/kotlin/io/spine/logging/backend/LoggerBackend.kt) — `loggerName: String?` (65), `isLoggable(Level)` (72), `log(LogData)` (80), `handleError(RuntimeException, LogData)` (118)
   - [`LogData.kt`](logging/src/commonMain/kotlin/io/spine/logging/backend/LogData.kt) — `level`, `timestampNanos: Long`, `loggerName`, `logSite`, `metadata`, `wasForced()`, `literalArgument`
   - [`Level.kt:56`](logging/src/commonMain/kotlin/io/spine/logging/Level.kt) — `data class Level(name, value)`; FATAL=2000, ERROR/SEVERE=1000, WARNING=900, INFO=800, CONFIG=700, DEBUG/FINE=500, FINER/TRACE=400, FINEST=300
   - [`MetadataProcessor.kt`](logging/src/commonMain/kotlin/io/spine/logging/backend/MetadataProcessor.kt) — `forScopeAndLogSite(scope, logged)` (83), `process(handler, ctx)` (133), `getSingleValue(key)` (151), `keySet()` (168)
   - [`MetadataHandler.kt`](logging/src/commonMain/kotlin/io/spine/logging/backend/MetadataHandler.kt) — `handle(key, value, ctx)` (62) and the repeated-key hook **`handleRepeated(key, values: Iterator<T>, ctx)`** (78); build via `MetadataHandler.builder()`
   - [`SimpleMessageFormatter.kt:167`](logging/src/commonMain/kotlin/io/spine/logging/backend/SimpleMessageFormatter.kt) — `getLiteralLogMessage(logData): String` (message text without the `[CONTEXT …]` suffix)
   - [`Platform.kt:182`](logging/src/commonMain/kotlin/io/spine/logging/backend/Platform.kt) — `getInjectedMetadata(): Metadata`
   - [`LogContext.kt:585`](logging/src/commonMain/kotlin/io/spine/logging/LogContext.kt) — `Key.LOG_CAUSE: MetadataKey<Throwable>`

2. **The reference backends to mirror**:
   - [`log4j2-backend`](backends/log4j2-backend) — the mapping template ([`LogEvents.kt`](backends/log4j2-backend/src/main/kotlin/io/spine/logging/backend/log4j2/LogEvents.kt): `MetadataProcessor.forScopeAndLogSite` → format → `getSingleValue(LOG_CAUSE)` → `MetadataHandler.builder()`, with `Tags` special-cased and `ValueQueue` for repeated keys).
   - [`probe-backend`](backends/probe-backend) — the `@AutoService` + KSP registration pattern ([`build.gradle.kts`](backends/probe-backend/build.gradle.kts): `plugins { ksp }`, `implementation(AutoService.annotations)`, `ksp(AutoServiceKsp.processor)`) and the memoizing test backend.
   - [`logging/build.gradle.kts`](logging/build.gradle.kts) — the **`kmp-module`** shape: `kotlin { sourceSets { commonMain {…}; jvmMain { runtimeOnly(project(":jvm-default-platform")) }; jvmTest {…} } }`, JVM target only.

3. **The proven OTel-Kotlin precedent — the `core-jvm` repo's `server-otel` module**
   (sibling checkout at `/Users/sanders/Projects/Spine/core-jvm`). It already uses
   the native Kotlin SDK successfully and gives us copy-ready artifacts:
   - `core-jvm/buildSrc/src/main/kotlin/io/spine/dependency/lib/OpenTelemetryKotlin.kt` — a `Dependency()` object pinning **0.4.0**, group `io.opentelemetry.kotlin`, exposing `api`, `noop`, `core`, `implementation`, `compat`. **Copy it verbatim into this repo's `buildSrc/.../lib/`.**
   - `core-jvm/server-otel/build.gradle.kts` — production depends on **`api` only**; tests add `core` + `implementation` (the native SDK).
   - `core-jvm/.../given/TestOtel.kt` — the SDK is built with `createOpenTelemetry { tracerProvider { export { processor } } }` and a hand-rolled recording processor. **The logs test path mirrors this with a recording `LogRecordProcessor` — no in-memory-exporter artifact required.**

   The actual **0.4.0 Logs API** was read from the api-discovery cache
   (`io.opentelemetry.kotlin:api:0.4.0`) and matches the report's skeletons:
   `Logger.emit(body, eventName, timestamp: Long?, observedTimestamp, context, severityNumber, severityText, exception, attributes)`,
   `Logger.enabled(context, severityNumber, eventName)`,
   `LoggerProvider.getLogger(name, version, schemaUrl, attributes)`,
   `OpenTelemetry.loggerProvider`, and the `SeverityNumber` enum (1–24; `DEBUG4=8`).

### Corrections to the report (verified 2026-06-27)

| # | Report says | Reality | Consequence |
|---|-------------|---------|-------------|
| C1 | `opentelemetry-kotlin` **0.5.0** | Latest is **0.4.0** (2026-05-20); 0.5.0 does not exist | Pin `0.4.0` (matches `core-jvm`). |
| C2 | Version catalog `gradle/libs.versions.toml` | Repo uses **buildSrc Kotlin objects** | Copy `core-jvm`'s `OpenTelemetryKotlin` object into `buildSrc/.../lib/`, not a TOML entry. |
| C3 | Service file in `jvmMain/resources`, hand-written | Repo standard is **`@AutoService` + KSP** (probe-backend) | Annotate the JVM factory `@AutoService(BackendFactory::class)`; KSP generates the service file. |
| C4 | Test with `exporters-in-memory` (Kotlin); compat bridge for the SDK | Kotlin in-memory exporter artifact unconfirmed; `core-jvm` uses the **native Kotlin SDK** + a hand-rolled recording processor | Use the **native SDK** (`core`+`implementation`); tests capture records with a recording `LogRecordProcessor`. No compat, no in-memory artifact. |
| C5 | A `std` backend exists | Only `log4j2`, `jul`, `probe` exist | Ignore; mirror `log4j2`. |
| C6 | `OtelBackendSettings.use(...)` reuses an existing pattern | No backend has programmatic injection today; JVM precedent is the `spine.logging.backend_factory` system-property override ([`DefaultPlatform.kt`](platforms/jvm-default-platform/src/main/kotlin/io/spine/logging/backend/system/DefaultPlatform.kt)) | `OtelBackendSettings` is a **new** convention; model its `@Volatile`/no-op-default shape on `DefaultPlatform`'s loader and document it. |

Two API refinements (vs the report's prose):
- `AttributesMutator` has **no `Int`/`Float` setters** — only `setLongAttribute`/`setDoubleAttribute` (+ `*List*`, `setByteArrayAttribute`, `setAnyValueAttribute`). The attribute handler must widen `Int`/`Short`/`Byte` → `Long` and `Float` → `Double`.
- `Logger.emit(timestamp: Long?)` — the param exists, but **the unit (epoch nanos vs millis) is not documented in the API**; it is resolved by the SDK. Confirm against `:implementation` 0.4.0 in Phase 0 before wiring `data.timestampNanos`.

### Decisions (resolved)

| Topic | Decision |
|-------|----------|
| **JVM SDK strategy** | **Native Kotlin SDK** (`core`+`implementation`) from the start — proven in `core-jvm/server-otel`. No compat bridge. |
| **Module platform** | **`kmp-module`** with the JVM target only (mirrors core `logging`). Mapping in `commonMain` (only touches OTel `:api` + Spine commonMain SPI); JVM-only registration in `jvmMain`. Aims at KMP up front to cut future migration, even though only JVM is wired today. |
| **Registration** | **`@AutoService(BackendFactory::class)` + KSP** (`kspJvm`), as probe-backend does. |
| **Events / OTEP 4430** | Events emit through the Logs API (`emit(eventName = …)`); never `Span.AddEvent`. |
| **Body vs attributes** | Body = `getLiteralLogMessage(...)` (no `[CONTEXT]` suffix); metadata → attributes (no double-encoding). |
| **CONFIG severity** | `DEBUG4` (judgment call, documented in KDoc) — open to `INFO` if preferred. |

## Architecture

```
backends/otel-backend/                         ← kmp-module, JVM target only (for now)
  src/commonMain/kotlin/io/spine/logging/backend/otel/
    OtelLoggerBackend.kt         ← LoggerBackend; LogData → Logger.emit(...)   [api only]
    OtelBackendSettings.kt       ← @Volatile holder; default NoopOpenTelemetry; use(otel)
    SeverityMapping.kt           ← Level.toSeverityNumber()
    AttributeMapping.kt          ← MetadataHandler<AttributesMutator> + LogSite → code.* semconv
  src/jvmMain/kotlin/io/spine/logging/backend/otel/
    OtelBackendFactory.kt        ← @AutoService(BackendFactory::class); resolves OpenTelemetry from settings
  src/jvmTest/kotlin/...         ← native SDK + recording LogRecordProcessor + Kotest/JUnit5 specs

backends/otel-backend-bootstrap/   ← OPTIONAL, Phase 2 (jvmMain SDK init, turnkey wiring)
```

- `commonMain` depends on `OpenTelemetryKotlin.api` + `.noop` + `project(":logging")`.
- `jvmMain` adds `AutoService.annotations` (+ `kspJvm(AutoServiceKsp.processor)`), and
  the factory carries `@AutoService` directly — it is a no-arg `class`, so unlike
  probe-backend's `object` it needs no adapter shim.
- `jvmTest` adds `OpenTelemetryKotlin.core` + `.implementation`, `logging-testlib`, and
  `runtimeOnly(project(":jvm-default-platform"))` so `DefaultPlatform` discovers the
  `@AutoService` factory end-to-end.
- **Build wrinkle to validate (Phase 0):** KSP on a `kmp-module` uses `kspJvm(...)`,
  not the plain `ksp(...)` probe-backend uses on its `jvm-module`. If KSP-on-KMP
  misbehaves with the `kmp-module` convention plugin, fall back to a hand-written
  `src/jvmMain/resources/META-INF/services/io.spine.logging.backend.BackendFactory`.

## Open decisions

1. **CONFIG severity** — `DEBUG4` (default) vs `INFO`. Cosmetic; documented either way.
2. **Bootstrap module SDK config surface** (Phase 2) — env-var driven vs explicit DSL.
   Defer until Phase 1 lands.

## Plan

### Phase 0 — spike: native SDK end-to-end on JVM (de-risk) ✅
- [x] Copy `core-jvm`'s `OpenTelemetryKotlin.kt` into
      [`buildSrc/.../lib/OpenTelemetryKotlin.kt`](buildSrc/src/main/kotlin/io/spine/dependency/lib/OpenTelemetryKotlin.kt)
      (0.4.0; api/noop/core/implementation/compat). `AutoService`/`AutoServiceKsp` already exist.
- [x] Register the module: `"otel-backend"` added to `includeBackend(...)` in
      [`settings.gradle.kts`](settings.gradle.kts).
- [x] [`build.gradle.kts`](backends/otel-backend/build.gradle.kts): `plugins { kmp-module; ksp }`;
      `commonMain` → `api(OpenTelemetryKotlin.api)` + `noop` + `:logging`; `jvmMain` →
      `AutoService.annotations` + `kspJvm(AutoServiceKsp.processor)`; `jvmTest` → `core` +
      `implementation` + `logging-testlib` + `runtimeOnly(:jvm-default-platform)`.
      **Two build wrinkles found & fixed:** (1) KSP-on-KMP works via `kspJvm` (no fallback
      needed); (2) `kmp-module` does NOT put the JUnit Platform on `jvmTest` (only
      `jvm-module` configures the `test` task), so added an explicit
      `tasks.named<Test>("jvmTest") { useJUnitPlatform() }`.
- [x] **Logs SDK DSL + timestamp unit confirmed** (upstream `v0.4.0` source):
      `createOpenTelemetry { loggerProvider { export { processor } } }`,
      `LogRecordProcessor.onEmit(ReadWriteLogRecord, Context)`, `ReadableLogRecord`
      getters, and `emit(timestamp)` = **epoch nanoseconds** (passes through unchanged).
- [x] [`RecordingLogRecordProcessor`](backends/otel-backend/src/jvmTest/kotlin/io/spine/logging/backend/otel/given/RecordingLogRecordProcessor.kt)
      (mirrors `core-jvm`'s `RecordingSpanProcessor`) drives the end-to-end test — no
      in-memory-exporter artifact needed.

### Phase 1 — production backend ✅
- [x] [`OtelBackendSettings.kt`](backends/otel-backend/src/commonMain/kotlin/io/spine/logging/backend/otel/OtelBackendSettings.kt):
      `@Volatile` holder, `NoopOpenTelemetry` default, `use()`/`current()`. Documented as new convention.
- [x] [`SeverityMapping.kt`](backends/otel-backend/src/commonMain/kotlin/io/spine/logging/backend/otel/SeverityMapping.kt):
      numeric-threshold `Level.toSeverityNumber()`; CONFIG → `DEBUG4`.
- [x] [`OtelLoggerBackend.kt`](backends/otel-backend/src/commonMain/kotlin/io/spine/logging/backend/otel/OtelLoggerBackend.kt):
      `isLoggable` via `enabled`; `log` maps body (`getLiteralLogMessage`), severity, nanos
      timestamp, cause→exception, attributes; implicit context for correlation; `handleError`.
- [x] [`AttributeMapping.kt`](backends/otel-backend/src/commonMain/kotlin/io/spine/logging/backend/otel/AttributeMapping.kt):
      `MetadataHandler.builder()` with default single + `setDefaultRepeatedHandler`
      (repeated→`*ListAttribute`), `.ignoring(LOG_CAUSE)`, `value is Tags` → `spine.tag.<name>`;
      LogSite → `code.*`; numeric widening (no `Int`/`Float` setters in `AttributesMutator`).
- [x] [`OtelBackendFactory.kt`](backends/otel-backend/src/jvmMain/kotlin/io/spine/logging/backend/otel/OtelBackendFactory.kt):
      `@AutoService(BackendFactory::class)`; resolves the logger from `OtelBackendSettings`.
- [x] [`OtelLoggerBackendSpec`](backends/otel-backend/src/jvmTest/kotlin/io/spine/logging/backend/otel/OtelLoggerBackendSpec.kt) —
      **10 tests, all passing**: body, no `[CONTEXT]`, severity number+text, `code.*`,
      `spine.*` single + repeated→list, cause→`exception.*` (not `spine.cause`), timestamp
      passthrough, and factory resolution via `OtelBackendSettings`.

### Phase 2 — bootstrap module + correlation hardening
- [ ] `backends/otel-backend-bootstrap` (kmp-module, jvmMain SDK init): read config,
      `createOpenTelemetry { loggerProvider { export { … } } }`, `OtelBackendSettings.use(...)`,
      own shutdown.
- [ ] Coroutine trace-correlation test: log inside a span-scoped coroutine; assert
      trace/span ids land on the record; wire the OTel context element if they don't
      survive dispatch (report §5.5 / §10.4); document the requirement.
- [ ] Optional early-record ring buffer in `OtelBackendSettings`, replayed on `use(...)`
      (report §4) — only if bootstrap-order logs matter.

### Phase 3 — log-based events API + domain-event integration
- [ ] Low-level: a `MetadataKey<String>` (`otel.event.name`) the backend pulls out and
      passes as `emit(eventName = …)` (report §6.1).
- [ ] Ergonomic `logEvent(name, severity, build)` guarded by `enabled(eventName = name)`
      (report §6.2).
- [ ] Domain-event → observability-event bridge: proto message type name → `event.name`
      (low cardinality), selected fields + scope → attributes, active span → correlation;
      document the domain-event-vs-observability-event boundary (report §6.3).

### Phase 4 — non-JVM targets (deferred; separate initiative)
- [ ] Only when Spine grows non-JVM backend registration (today only
      `jvm-default-platform` + ServiceLoader exist; `actual fun loadPlatform()` is
      JVM-only). The `commonMain` mapping already built here is the reusable part;
      each target needs its own discovery mechanism + an OTLP exporter (iOS/native OTLP
      is **not** available upstream yet — correction C4).

## Risks & open questions (status after verification)

1. **`opentelemetry-kotlin` maturity** — 0.4.0, `@ExperimentalApi`, logs in *Development*.
   `@OptIn` everywhere; pin exactly; expect breaking changes. *(Open — accepted.)*
2. **`emit(timestamp)` unit** — param is `Long?`, unit undocumented in `:api`. Confirm
   against `:implementation` in Phase 0. *(Open — verify.)*
3. **Logs SDK DSL** — `loggerProvider { export { … } }` inferred by analogy to the
   tracer DSL in `core-jvm`; confirm against `:implementation` 0.4.0 in Phase 0. *(Open — verify.)*
4. **KSP on `kmp-module`** — `kspJvm` path is unexercised in this repo; validate in
   Phase 0, fallback = hand-written service file. *(Open — verify.)*
5. **Coroutine context propagation** — correlation across suspension unproven; Phase 2
   test. *(Open.)*
6. **Test exporter** — resolved: hand-rolled recording `LogRecordProcessor` like
   `core-jvm`, no in-memory artifact. *(Resolved.)*
7. **Non-JVM registration** — genuinely unpaved in Spine; keeps KMP export out of
   near-term scope. *(Resolved → Phase 4.)*

## Testing

- JVM, native Kotlin SDK (`core`+`implementation`) configured via
  `createOpenTelemetry { loggerProvider { export { recordingProcessor } } }`, mirroring
  `core-jvm/server-otel`'s `TestOtel`/`RecordingSpanProcessor`.
- Assert: severity number, body without context suffix, namespaced + widened + repeated→list
  attributes, exception recorded, `event.name` when set, trace/span id under an active
  span, and the coroutine-correlation case.
- `runtimeOnly(:jvm-default-platform)` in `jvmTest` for the `@AutoService` discovery path.

## Log

- 2026-06-27 — Drafted from `otel-backend-report.md` after a 6-agent verification pass
  (SPI signatures, log4j2 template, build conventions, platform selection, task-doc
  format, upstream `opentelemetry-kotlin` status). Recorded corrections C1–C6.
- 2026-06-27 — Scope confirmed by maintainer: JVM-first, Phases 0–3, KMP (Phase 4)
  deferred.
- 2026-06-27 — Two implementation decisions taken: **native Kotlin SDK** from the start
  (proven in `core-jvm/server-otel`) and **`@AutoService` + KSP** registration. Module
  retargeted to **`kmp-module`** (JVM only) with the mapping in `commonMain` to minimise
  future KMP migration. Verified the real 0.4.0 Logs API from the api-discovery cache
  and the `core-jvm` `OpenTelemetryKotlin` dep object / native-SDK test pattern.
- 2026-06-27 — **Phases 0 & 1 implemented and verified.** `./gradlew :otel-backend:build`
  is green (compile + detekt + kover + license); `:otel-backend:jvmTest` runs **10/10
  passing** (had to JDK-17 the build and add `useJUnitPlatform()` to the kmp `jvmTest`).
  KSP emits the `@AutoService` service file naming `OtelBackendFactory`. Timestamp unit
  and logs SDK DSL confirmed against upstream `v0.4.0`. Phases 2–3 remain.
