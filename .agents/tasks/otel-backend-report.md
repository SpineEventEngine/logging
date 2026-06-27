# Implementing an OpenTelemetry Logger Backend for Spine Logging (KMP)

**Status:** Research / implementation spec — ready to drive Claude Code sessions
**Target library:** `SpineEventEngine/logging`
**OTel client:** `open-telemetry/opentelemetry-kotlin` (KMP), `io.opentelemetry.kotlin:*` v0.5.0 (
`@ExperimentalApi`)
**Scope:** Logger backend (Spine `LogData` → OTel log records) **plus** a log-based events surface
**SDK lifecycle:** support both *inject an existing instance* and *bootstrap from config*

---

## 0. How to use this document in Claude Code

This is a self-contained spec. A Claude Code session does not have the conversation that produced
it, so everything needed is inline: the exact Spine SPI signatures, the exact `opentelemetry-kotlin`
API surface, the mapping rules, code skeletons, and the open risks. Treat the code blocks as
*starting skeletons to verify against the pinned dependency versions*, not as copy-paste-final —
`opentelemetry-kotlin` is pre-1.0 and `@ExperimentalApi`, so signatures can shift between releases.

Recommended working order is the phased plan in §11. Start with the Phase 0 JVM-via-compat spike
before committing to the native KMP path.

---

## 1. Decision record

| Decision        | Choice                                    | Rationale                                                                                                                                                     |
|-----------------|-------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| OTel client     | `opentelemetry-kotlin` (KMP)              | Multiplatform reach matching Spine's `commonMain` ethos; idiomatic Kotlin API. Trade-off: logs are in *Development* status and the API is `@ExperimentalApi`. |
| Platform target | KMP, JVM-first                            | JVM via the `compat` bridge over `opentelemetry-java` de-risks maturity; native/JS follow.                                                                    |
| Scope           | Backend + log-based events                | `Logger.emit(eventName = …)` makes events the *same* call path as logs — minimal extra surface.                                                               |
| SDK lifecycle   | Both (inject default, optional bootstrap) | Core backend stays `:api`-only; a separate optional module bootstraps a real SDK.                                                                             |
| Span events     | Log-based only (no `Span.AddEvent`)       | Aligns with OTEP 4430 (Span Event API deprecation).                                                                                                           |

---

## 2. Background — the two sides

### 2.1 Spine Logging backend SPI (ground truth from the repo)

The SPI lives in `logging/src/commonMain/kotlin/io/spine/logging/backend/`. It is Flogger-derived
but slimmed down. The four types you implement against:

```kotlin
// BackendFactory.kt — discovered + instantiated by the platform
public abstract class BackendFactory {
    public abstract fun create(loggingClass: String): LoggerBackend
}

// LoggerBackend.kt — the thing you build
public abstract class LoggerBackend {
    public abstract val loggerName: String?
    public abstract fun isLoggable(level: Level): Boolean
    public abstract fun log(data: LogData)
    public abstract fun handleError(error: RuntimeException, badData: LogData)
}

// LogData.kt — the record handed to log()
public interface LogData {
    public val level: Level
    public val timestampNanos: Long
    public val loggerName: String?
    public val logSite: LogSite          // className, methodName, fileName, lineNumber
    public val metadata: Metadata
    public fun wasForced(): Boolean
    public val literalArgument: Any?
}
```

`Level` is a plain data class (not `java.util.logging.Level`), so it is multiplatform-safe:

```kotlin
public data class Level(val name: String, val value: Int)
// OFF=MAX, FATAL=2000, ERROR/SEVERE=1000, WARNING=900, INFO=800,
// CONFIG=700, DEBUG/FINE=500, FINER/TRACE=400, FINEST=300, ALL=MIN
```

`Metadata` is an indexed collection of typed keys; `MetadataKey<T>` carries a `label`, a `canRepeat`
flag, and `cast()`:

```kotlin
public abstract class Metadata {
    public abstract fun size(): Int
    public abstract fun getKey(n: Int): MetadataKey<Any>
    public abstract fun getValue(n: Int): Any
    public abstract fun <T : Any> findValue(key: MetadataKey<T>): T?
}
```

Two helper types you will use rather than hand-rolling iteration:

- `MetadataProcessor` — merges *scope* metadata (`ScopedLoggingContext`) with *log-site* metadata
  and iterates them: `forScopeAndLogSite(scope, logged)`, then `process(handler, context)`, plus
  `getSingleValue(key)`, `keySet()`.
- `MetadataHandler<C>` — visitor:
  `abstract fun <T : Any> handle(key: MetadataKey<T>, value: T, context: C)` (with separate handling
  hooks for repeated keys).
- `SimpleMessageFormatter` — renders the message. Critically it exposes
  `getLiteralLogMessage(logData): String` (message text **without** the `[CONTEXT …]` suffix) in
  addition to the full `format(logData, metadata)`. The default formatter already ignores
  `LogContext.Key.LOG_CAUSE` when appending context.

The cause/throwable is carried as metadata under `LogContext.Key.LOG_CAUSE`, extracted via
`metadata.getSingleValue(LogContext.Key.LOG_CAUSE)`.

### 2.2 Reference implementation — the Log4j2 backend

The shipped `backends/log4j2-backend` is the template to mirror. Its shape, condensed:

```kotlin
public class Log4j2BackendFactory : BackendFactory() {
    override fun create(loggingClass: String): LoggerBackend {
        val name = loggingClass.replace('$', '.')
        val logger = LogManager.getLogger(name) as Logger
        return Log4j2LoggerBackend(logger)
    }
}

internal class Log4j2LoggerBackend(private val logger: Logger) : LoggerBackend() {
    override val loggerName get() = logger.name
    override fun isLoggable(level: Level) = logger.isEnabled(level.toLog4j())
    override fun log(data: LogData) = logger.get().log(toLog4jLogEvent(logger.name, data))
    override fun handleError(error: RuntimeException, badData: LogData) =
        logger.get().log(toLog4jLogEvent(logger.name, error, badData))
}
```

Registered via
`backends/log4j2-backend/src/main/resources/META-INF/services/io.spine.logging.backend.BackendFactory`
containing one line: the factory FQN. Note this is **JVM `ServiceLoader`** — see §9 for why that
matters for KMP.

Inside `toLog4jLogEvent` the pattern you will replicate is:

1. `MetadataProcessor.forScopeAndLogSite(Platform.getInjectedMetadata(), logData.metadata)`
2. format the message,
3. pull the cause via `getSingleValue(LOG_CAUSE)`,
4. project log-site + metadata into the target representation.

### 2.3 opentelemetry-kotlin Logs/Events API (ground truth from the repo)

Repo: `open-telemetry/opentelemetry-kotlin` (donated by Embrace). Version **0.5.0**. The logs API is
annotated `@ExperimentalApi`.

Module map (relevant subset):

| Artifact                                           | Use                                                                    |
|----------------------------------------------------|------------------------------------------------------------------------|
| `io.opentelemetry.kotlin:api`                      | Instrumentation API — **the only dependency the backend itself needs** |
| `io.opentelemetry.kotlin:noop`                     | `NoopOpenTelemetry` default instance                                   |
| `io.opentelemetry.kotlin:core` + `:implementation` | The native Kotlin SDK (only where you initialize)                      |
| `io.opentelemetry.kotlin:compat`                   | JVM/Android bridge over `opentelemetry-java`                           |
| `io.opentelemetry.kotlin:exporters-otlp`           | OTLP exporters                                                         |
| `io.opentelemetry.kotlin:exporters-in-memory`      | Test exporter                                                          |

The API entry point and logs surface:

```kotlin
public interface OpenTelemetry {
    public val loggerProvider: LoggerProvider
    public val tracerProvider: TracerProvider
    public val context: ContextFactory
    public val span: SpanFactory
    public val baggage: BaggageFactory
    // … meterProvider, propagator, etc.
}

public interface LoggerProvider {
    public fun getLogger(
        name: String, version: String? = null, schemaUrl: String? = null,
        attributes: (AttributesMutator.() -> Unit)? = null,
    ): Logger
}

public interface Logger {
    public fun enabled(
        context: Context? = null,
        severityNumber: SeverityNumber? = null,
        eventName: String? = null,
    ): Boolean

    public fun emit(
        body: Any? = null,
        eventName: String? = null,            // ← log-based EVENTS are just this param
        timestamp: Long? = null,
        observedTimestamp: Long? = null,
        context: Context? = null,
        severityNumber: SeverityNumber? = null,
        severityText: String? = null,
        exception: Throwable? = null,
        attributes: (AttributesMutator.() -> Unit)? = null,
    )
}
```

`SeverityNumber` is the standard 1–24 OTel scale (
`TRACE=1 … DEBUG=5 … INFO=9 … WARN=13 … ERROR=17 … FATAL=21`, each with `2/3/4` sub-levels).
`AttributesMutator` is a typed builder:

```kotlin
public interface AttributesMutator {
    public fun setBooleanAttribute(key: String, value: Boolean)
    public fun setStringAttribute(key: String, value: String)
    public fun setLongAttribute(key: String, value: Long)
    public fun setDoubleAttribute(key: String, value: Double)
    public fun setStringListAttribute(
        key: String,
        value: List<String>
    )   // + Boolean/Long/Double list
    public fun setByteArrayAttribute(key: String, value: ByteArray)
    public fun setAnyValueAttribute(key: String, value: AnyValue)
}
```

**Compat bridge (JVM/Android only, `compat` module):**

```kotlin
// wrap an existing opentelemetry-java instance
val otelKotlin: OpenTelemetry = otelJava.toOtelKotlinApi()
// or create a Kotlin API backed by the Java SDK
val otelKotlin: OpenTelemetry = createCompatOpenTelemetry { /* configure */ }
```

This is the key maturity hedge: on the JVM you get the Kotlin API surface while the **Stable**
`opentelemetry-java` logs SDK does the actual work.

---

## 3. Architecture

```
backends/otel-backend/                 ← KMP module, commonMain depends ONLY on :api (+ :noop)
  commonMain/
    OtelBackendFactory.kt              ← BackendFactory; resolves OpenTelemetry from holder
    OtelLoggerBackend.kt               ← LoggerBackend; maps LogData → Logger.emit(...)
    SeverityMapping.kt                 ← Level → SeverityNumber
    AttributeMapping.kt                ← Metadata/LogSite → AttributesMutator
    OtelBackendSettings.kt             ← holder for the OpenTelemetry instance (inject mode)
    events/                            ← log-based events surface (Phase 3)
  jvmMain/
    resources/META-INF/services/io.spine.logging.backend.BackendFactory

backends/otel-backend-bootstrap/       ← OPTIONAL, separate artifact (bootstrap mode)
  depends on :core/:implementation/:exporters-otlp OR :compat + opentelemetry-java BOM
  builds a real SDK from env/config and calls OtelBackendSettings.use(...)
```

**Why the split:** the OTel docs are explicit that `core`/`compat`/`implementation` should not be a
dependency of any module that isn't initializing the SDK. Keeping the backend `:api`-only means
consumers who already run an OTel SDK just point the backend at it; consumers who want turnkey
wiring add the bootstrap artifact.

---

## 4. SDK lifecycle — supporting both modes

The SPI constraint: `BackendFactory` is no-arg-constructed by the platform (via `ServiceLoader` on
JVM), so the factory **cannot take the `OpenTelemetry` instance through its constructor**. It must
resolve it from a settable holder, defaulting to no-op.

```kotlin
// OtelBackendSettings.kt  (commonMain, :api only)
public object OtelBackendSettings {
    @Volatile
    private var instance: OpenTelemetry = NoopOpenTelemetry

    /** Inject mode: app calls this once at startup. */
    public fun use(openTelemetry: OpenTelemetry) {
        instance = openTelemetry
    }

    internal fun current(): OpenTelemetry = instance
}
```

- **Inject mode (recommended default):** the app sets the instance at bootstrap. On JVM this can be
  a native Kotlin SDK *or* `javaOtel.toOtelKotlinApi()`:
  ```kotlin
  OtelBackendSettings.use(GlobalOpenTelemetry.get().toOtelKotlinApi())   // JVM + compat
  // or
  OtelBackendSettings.use(createOpenTelemetry { loggerProvider { export { … } } })  // native
  ```
- **Bootstrap mode:** the optional `otel-backend-bootstrap` module reads config (e.g.
  `OTEL_EXPORTER_OTLP_ENDPOINT`), builds an SDK, and calls `OtelBackendSettings.use(...)`. It owns
  the SDK lifecycle and shutdown.

**Early-record caveat (mirror the OTel appender behavior):** anything logged before `use(...)` runs
goes to `NoopOpenTelemetry` and is dropped. If bootstrap-order logs matter, add a small bounded ring
buffer in the holder that replays into the real instance on `use(...)`. Treat as a Phase 2
enhancement.

---

## 5. The mapping — `LogData` → `Logger.emit(...)`

This is the core of the backend. The `OtelLoggerBackend`:

```kotlin
internal class OtelLoggerBackend(
    private val logger: Logger,          // io.opentelemetry.kotlin.logging.Logger
    override val loggerName: String?,
) : LoggerBackend() {

    override fun isLoggable(level: Level): Boolean =
        logger.enabled(severityNumber = level.toSeverityNumber())

    override fun log(data: LogData) {
        val metadata = MetadataProcessor.forScopeAndLogSite(
            Platform.getInjectedMetadata(), data.metadata
        )
        val cause = metadata.getSingleValue(LogContext.Key.LOG_CAUSE)
        logger.emit(
            body = SimpleMessageFormatter.getLiteralLogMessage(data),  // text only, no [CONTEXT]
            timestamp = data.timestampNanos,                           // VERIFY unit (see §10)
            severityNumber = data.level.toSeverityNumber(),
            severityText = data.level.name,
            exception = cause,
            context = null,                                            // null ⇒ active context (see §5.5)
            attributes = { applyAttributes(data, metadata) },
        )
    }

    override fun handleError(error: RuntimeException, badData: LogData) {
        logger.emit(
            body = "Spine logging backend error: ${error.message}",
            severityNumber = SeverityNumber.ERROR,
            severityText = "ERROR",
            exception = error,
            attributes = { setStringAttribute("spine.logging.bad_data", badData.toString()) },
        )
    }
}
```

### 5.1 Severity — `Level` → `SeverityNumber`

Use a numeric-threshold mapping rather than name-matching, so custom Spine levels degrade sensibly:

```kotlin
fun Level.toSeverityNumber(): SeverityNumber = when {
    value >= Level.FATAL.value -> SeverityNumber.FATAL    // 2000 → 21
    value >= Level.ERROR.value -> SeverityNumber.ERROR    // 1000 → 17
    value >= Level.WARNING.value -> SeverityNumber.WARN     //  900 → 13
    value >= Level.INFO.value -> SeverityNumber.INFO     //  800 →  9
    value >= Level.CONFIG.value -> SeverityNumber.DEBUG4   //  700 →  8  (judgment call)
    value >= Level.DEBUG.value -> SeverityNumber.DEBUG    //  500 →  5
    value >= Level.FINER.value -> SeverityNumber.TRACE2   //  400 →  2
    else -> SeverityNumber.TRACE    //  300 →  1
}
```

`CONFIG` has no clean OTel equivalent (it sits between INFO and DEBUG in JUL semantics); `DEBUG4`
keeps it just above plain DEBUG. Mapping it to `INFO` is also defensible — pick one and document it.

### 5.2 Body / message

Use `SimpleMessageFormatter.getLiteralLogMessage(data)` to get the rendered message **without** the
appended `[CONTEXT …]` block. Metadata goes to attributes (§5.4), so using the full `format(...)`
here would double-encode it (once as text, once as structured attributes). This is a deliberate
divergence from the Log4j2 backend, which appends context into the text because Log4j2's
structured-data story is weaker.

### 5.3 Timestamp & exception

- `data.timestampNanos` → `emit(timestamp = …)`. **Confirm the unit** `opentelemetry-kotlin`
  expects (epoch nanos vs millis) against the pinned version; OTLP is epoch nanos, but verify the
  Kotlin API contract.
- Cause via `metadata.getSingleValue(LogContext.Key.LOG_CAUSE)` → `emit(exception = …)`. The Kotlin
  SDK records it per the exception semantic conventions.

### 5.4 Metadata + LogSite → attributes

```kotlin
private fun AttributesMutator.applyAttributes(data: LogData, metadata: MetadataProcessor) {
    // log-site → OTel code.* semconv
    val s = data.logSite
    setStringAttribute("code.namespace", s.className)
    setStringAttribute("code.function", s.methodName)
    s.fileName?.let { setStringAttribute("code.filepath", it) }
    if (s.lineNumber >= 0) setLongAttribute("code.lineno", s.lineNumber.toLong())

    // scope + log-site metadata → attributes (LOG_CAUSE already consumed as exception)
    metadata.process(AttributeHandler, this)
}

private object AttributeHandler : MetadataHandler<AttributesMutator>() {
    override fun <T : Any> handle(key: MetadataKey<T>, value: T, ctx: AttributesMutator) {
        if (key == LogContext.Key.LOG_CAUSE) return       // handled as exception
        val name = "spine.${key.label}"                   // namespace to avoid semconv clashes
        when (value) {
            is Boolean -> ctx.setBooleanAttribute(name, value)
            is Int -> ctx.setLongAttribute(name, value.toLong())
            is Long -> ctx.setLongAttribute(name, value)
            is Float -> ctx.setDoubleAttribute(name, value.toDouble())
            is Double -> ctx.setDoubleAttribute(name, value)
            is String -> ctx.setStringAttribute(name, value)
            else -> ctx.setStringAttribute(name, value.toString())
        }
    }
    // Override the repeated-key hook to accumulate into setStringListAttribute / setLongListAttribute, etc.
}
```

Notes:

- **Namespace metadata keys** (`spine.<label>`) so Spine's keys never collide with OTel
  semantic-convention attribute names.
- **Repeated keys** (`MetadataKey.canRepeat == true`): the `MetadataHandler` base provides a
  repeated-value hook; collect those into the `*ListAttribute` setters rather than emitting the
  last-wins single value.
- `ScopedLoggingContext` data flows in automatically because `Platform.getInjectedMetadata()` is
  merged by `MetadataProcessor.forScopeAndLogSite`. This is where Spine's context (bounded-context,
  aggregate id, user) becomes structured OTel attributes — the differentiation that a plain
  Log4j2-appender hop would flatten into text.
- `Tags` (from `ScopedLoggingContext`) are a distinct concept from metadata keys; decide whether to
  surface them as attributes too (recommended: yes, namespaced `spine.tag.<name>`).

### 5.5 Trace correlation

Passing `context = null` lets the Kotlin SDK stamp the active span's trace/span IDs onto the record.
**Verify how the active `Context` is obtained per platform** — on the JVM it's typically
thread-local/`ContextStorage`, but across coroutine suspension you need the OpenTelemetry context
element wired in (see `api-ext` and the broader KMP context-propagation caveat in §10). If Spine
logs from coroutines, correlation silently breaks without it.

---

## 6. Log-based events surface

Because `eventName` is a parameter on `emit`, an event is a log record with a name. Two layers:

### 6.1 Low level — already available

Anyone can emit an event through the backend by attaching the event name as metadata that the
backend maps to `emit(eventName = …)`. Define a Spine metadata key:

```kotlin
val EVENT_NAME: MetadataKey<String> = MetadataKey.single("otel.event.name", String::class)
```

…and in the backend, pull it out before the generic attribute loop and pass it as `eventName` (
mirroring how OTel's own Logback appender recognizes an `otel.event.name` key).

### 6.2 Ergonomic — a typed event API (Phase 3)

```kotlin
public fun WithLogging.logEvent(
    name: String,
    severity: Level = Level.INFO,
    build: EventScope.() -> Unit = {},
)
```

where `EventScope` collects typed attributes and ends in a single `emit(eventName = name, …)`. Keep
`enabled(eventName = name)` guarding so disabled events cost nothing.

### 6.3 Domain events ↔ observability events (draw this line explicitly)

For an event-sourced system the word "event" is overloaded. **A Spine domain event** (persisted,
replayable, the source of truth) **is not** an OTel observability event (lossy telemetry). Never use
the OTel pipeline as an event store.

The high-value, safe pattern is to emit an observability event *about* a domain event as it is
handled/applied:

- `event.name` ← the Protobuf message type name (e.g. `acme.orders.OrderPlaced`), which is naturally
  low-cardinality and satisfies the OTel rule that names must not contain dynamic values.
- attributes ← selected proto fields + `ScopedLoggingContext` (aggregate id, bounded context, user).
- correlation ← the active span, automatically.

This turns the existing domain-event flow into well-formed, trace-correlated telemetry with
near-zero ceremony — the differentiation a generic appender can't provide because it has no
knowledge of Spine's typed events.

---

## 7. Registration

**JVM:** add `jvmMain/resources/META-INF/services/io.spine.logging.backend.BackendFactory`
containing:

```
io.spine.logging.backend.otel.OtelBackendFactory
```

Exactly one `BackendFactory` should be on the classpath; if both the OTel and another backend are
present, the platform's selection is undefined/last-wins. Document that consumers pick one backend
artifact.

```kotlin
public class OtelBackendFactory : BackendFactory() {
    override fun create(loggingClass: String): LoggerBackend {
        val name = loggingClass.replace('$', '.')
        val logger = OtelBackendSettings.current().loggerProvider.getLogger(name)
        return OtelLoggerBackend(logger, loggerName = name)
    }
    override fun toString() = this::class.qualifiedName ?: "OtelBackendFactory"
}
```

**Non-JVM:** see §9 — `ServiceLoader` does not exist; this is an open path in Spine itself.

---

## 8. Build setup (Gradle, KMP)

Version catalog:

```toml
[versions]
otelKotlin = "0.5.0"          # PIN exactly — pre-1.0, @ExperimentalApi

[libraries]
otel-kotlin-api = { module = "io.opentelemetry.kotlin:api", version.ref = "otelKotlin" }
otel-kotlin-noop = { module = "io.opentelemetry.kotlin:noop", version.ref = "otelKotlin" }
otel-kotlin-core = { module = "io.opentelemetry.kotlin:core", version.ref = "otelKotlin" }
otel-kotlin-impl = { module = "io.opentelemetry.kotlin:implementation", version.ref = "otelKotlin" }
otel-kotlin-compat = { module = "io.opentelemetry.kotlin:compat", version.ref = "otelKotlin" }
otel-kotlin-otlp = { module = "io.opentelemetry.kotlin:exporters-otlp", version.ref = "otelKotlin" }
otel-kotlin-inmem = { module = "io.opentelemetry.kotlin:exporters-in-memory", version.ref = "otelKotlin" }
```

Backend module (`:api` + `:noop` only):

```kotlin
kotlin {
    jvm()
    // androidTarget(); iosX64(); iosArm64(); iosSimulatorArm64(); js(); … per target-coverage check (§10)
    sourceSets {
        commonMain.dependencies {
            api(projects.logging)                 // Spine backend SPI
            implementation(libs.otel.kotlin.api)
            implementation(libs.otel.kotlin.noop)
        }
        commonTest.dependencies { implementation(libs.otel.kotlin.inmem) }
    }
}
```

Bootstrap module (JVM, where the SDK is initialized) adds `core`+`implementation`+`exporters-otlp`,
or `compat` + the `opentelemetry-java` BOM if you go the compat route.

---

## 9. Critical finding — KMP registration is unpaved in Spine

The backend SPI (`BackendFactory`, `LoggerBackend`, `LogData`, `Metadata`) is in `commonMain`, **but
every shipped backend (jul, log4j2) is JVM-only and is discovered via JVM `ServiceLoader`** (
`META-INF/services`). The default JVM platform is `platforms/jvm-default-platform` (
`DefaultPlatform`).

Implications:

- On **JVM**, registration is solved — copy the log4j2 pattern.
- On **non-JVM targets** there is no `ServiceLoader`. How Spine selects a `BackendFactory` on
  native/JS is **not demonstrated by any existing backend**. Before promising true multiplatform,
  audit Spine's per-platform `Platform`/backend-selection wiring (look at how `Platform.getBackend`
  /the platform default is resolved off-JVM) and expect to contribute that path. This is the single
  biggest unknown in the KMP plan and the reason for the JVM-first phasing.

---

## 10. Risks & open questions

1. **opentelemetry-kotlin maturity.** v0.5.0, `@ExperimentalApi`, logs in *Development*. Pin the
   exact version; expect breaking changes; gate the backend behind its own opt-in artifact.
2. **KMP backend registration (see §9).** Non-JVM selection path unproven in Spine.
3. **Multiplatform exporter coverage.** Confirm `io.opentelemetry.kotlin:exporters-otlp` actually
   supports your intended targets. Community KMP efforts had to hand-roll an iOS OTLP/HTTP
   exporter (Ktor) because native OTLP coverage lagged — verify before promising iOS/native export.
4. **Coroutine context propagation.** Trace correlation depends on the active `Context` being
   present at `emit`. Across coroutine suspension/dispatch this is lost unless the OTel context
   element is wired (thread-local approaches don't survive dispatcher hops). Validate explicitly.
5. **Timestamp unit.** Confirm `emit(timestamp = …)` expects epoch nanos.
6. **`AttributesMutator`/`Logger` signature drift.** Re-check against the pinned version before
   finalizing the skeletons.
7. **CONFIG severity mapping** is a documented judgment call (DEBUG4 vs INFO).
8. **Double-encoding metadata.** Enforced by using `getLiteralLogMessage` + attributes; add a test
   asserting the body contains no `[CONTEXT …]`.
9. **Performance.** Build attributes only when `isLoggable`/`enabled`; the SPI already guards via
   `isLoggable`, but the events API must guard with `enabled(eventName = …)` too.

---

## 11. Phased plan

- **Phase 0 — JVM-via-compat spike.** Backend on JVM only, `OpenTelemetry` provided as
  `javaOtel.toOtelKotlinApi()`. Validate the full path (Spine log → OTLP → backend of choice)against
  the mature Java SDK. Lowest risk; proves the mapping before touching KMP.
- **Phase 1 — KMP commonMain backend (`:api`).** Move the mapping into `commonMain`, JVM target
  registered via `ServiceLoader`, SDK injected by the app. Severity, body, metadata→attributes,
  cause, correlation all covered with tests on the in-memory exporter.
- **Phase 2 — bootstrap module + native/JS.** Add `otel-backend-bootstrap`, tackle §9 (non-JVM
  registration) and §10.3 (exporter coverage). Add the early-record buffer if needed.
- **Phase 3 — ergonomic events API + domain-event integration.** `logEvent { … }`, proto-type →
  `event.name`, the domain-event-vs-observability-event boundary (§6.3).

---

## 12. Testing

- Use `io.opentelemetry.kotlin:exporters-in-memory` to capture emitted records and assert: severity
  number, body text (no context suffix), attribute set (namespaced, repeated→list), exception
  recorded, event name when set, and trace/span id when a span is active.
- Mirror the existing `probe-backend` testing approach for backend-selection tests.
- Add a coroutine correlation test (log inside a span-scoped coroutine, assert ids propagate).

---

## 13. OTEP 4430 alignment

This design emits events through the **Logs API** (`emit(eventName = …)`), which is exactly the
direction OTEP 4430 (Span Event API deprecation, March 2026) endorses; it never calls
`Span.AddEvent`/`Span.RecordException`. `opentelemetry-kotlin` ships a `SpanEventCreator` in its
tracing package — the optional compatibility shim that can surface log-based events back onto spans
for backends that still expect them. You do not need it for this backend, but it's the escape hatch
if a downstream trace view requires events-on-spans.

---

## 14. References

- opentelemetry-kotlin (repo): https://github.com/open-telemetry/opentelemetry-kotlin
- opentelemetry-kotlin getting started (coordinates, compat
  mode): https://opentelemetry.io/docs/languages/kotlin/getting-started/
- Embrace donation / KMP
  rationale: https://opentelemetry.io/blog/2025/kotlin-multiplatform-opentelemetry/
- Logs API spec (bridge vs ergonomic
  API): https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/logs/api.md
- Logs data model (event = log with
  `event.name`): https://opentelemetry.io/docs/specs/otel/logs/data-model/
- Event semantic conventions: https://opentelemetry.io/docs/specs/semconv/general/events/
- Span Event API deprecation (blog): https://opentelemetry.io/blog/2026/deprecating-span-events/
- OTEP 4430 (deprecation
  plan): https://github.com/open-telemetry/opentelemetry-specification/blob/main/oteps/4430-span-event-api-deprecation-plan.md
- Spine Logging (repo): https://github.com/SpineEventEngine/logging

---

*Generated as an implementation research spec. Verify all `opentelemetry-kotlin` signatures against
the pinned v0.5.0 artifacts before finalizing code — the API is `@ExperimentalApi` and subject to
change.*
