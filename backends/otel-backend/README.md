## OpenTelemetry backend

This module routes Spine Logging statements to [OpenTelemetry][otel] as OTLP-ready
log records, using the Kotlin-multiplatform [`opentelemetry-kotlin`][otel-kotlin]
API. It maps each Spine `LogData` onto a single `Logger.emit(...)` call and, on the
same path, supports log-based **events**.

The backend itself depends only on the OpenTelemetry **API** (`io.opentelemetry.kotlin:api`).
The application — or a dedicated bootstrap — owns the SDK and decides where records go.

> **Status:** experimental. `opentelemetry-kotlin` is pre-1.0 and its logs API is
> annotated `@ExperimentalApi`; this backend pins one exact version and may need to
> change with it.

### Providing the `OpenTelemetry` instance

The backend factory is constructed by the platform via `ServiceLoader`, so it cannot
receive the `OpenTelemetry` instance through a constructor. Instead, the application
installs it once, at startup, **before any logging happens**:

```kotlin
import io.opentelemetry.kotlin.createOpenTelemetry
import io.spine.logging.backend.otel.OtelBackendSettings

OtelBackendSettings.use(
    createOpenTelemetry {
        loggerProvider {
            export { /* a LogRecordProcessor, e.g. a batch processor over an OTLP exporter */ }
        }
    }
)
```

Until `use(...)` is called, records go to `NoopOpenTelemetry` and are dropped. The
instance is owned by the caller, including its shutdown.

This module exposes the factory as a Java service, so adding it to the runtime
classpath makes `DefaultPlatform` pick it up automatically. Keep exactly **one**
backend on the classpath; if several are present, select this one explicitly with
`-Dspine.logging.backend_factory=io.spine.logging.backend.otel.OtelBackendFactory`.

### What is mapped

| Spine `LogData` | OpenTelemetry log record |
|---|---|
| rendered message (without the `[CONTEXT …]` suffix) | `body` |
| `level` | `severityNumber` (numeric threshold) and `severityText` (the level name) |
| `timestampNanos` | `timestamp` (epoch nanoseconds) |
| `LogContext.Key.LOG_CAUSE` | `exception` (recorded as `exception.*` attributes) |
| `logSite` | `code.namespace`, `code.function`, `code.filepath`, `code.lineno` |
| scope + log-site metadata | attributes, namespaced `spine.<label>` (repeated → list) |
| `Tags` from `ScopedLoggingContext` | `spine.tag.<name>` attributes |
| the active span | trace/span ids (the record is emitted with the implicit context) |

Metadata is emitted as **structured attributes**, not folded into the message text,
so a downstream collector sees Spine's bounded-context, aggregate id, user, etc. as
queryable fields.

`CONFIG` has no exact OpenTelemetry counterpart and is mapped to `DEBUG4`.

### Log-based events

Because `eventName` is just a parameter on `emit`, an event is a log record with a
name. Emit one with `logEvent`:

```kotlin
import io.spine.logging.backend.otel.logEvent

class Checkout : WithLogging {
    fun complete(orderId: String) {
        logEvent("acme.orders.OrderCompleted")
        // Attributes are added the usual way, via the fluent API and metadata keys:
        // logger.atInfo().with(ORDER_ID, orderId).with(EVENT_NAME, name).log { … }
    }
}
```

Under the hood `logEvent` attaches the [`EVENT_NAME`][event-name] metadata key
(Spine label `otelEventName`); the backend forwards it as `emit(eventName = …)` and
does not duplicate it as an attribute. With a non-OpenTelemetry backend the key
degrades to ordinary metadata.

Event names must be **low-cardinality** (no ids or other dynamic values) per the
OpenTelemetry [event semantic conventions][otel-events]; dynamic data goes into
attributes.

### Domain events vs. observability events

In an event-sourced Spine application the word *event* is overloaded, and the two
meanings must not be conflated:

- A **Spine domain event** is persisted, replayable, and the source of truth.
- An **OpenTelemetry event** is lossy telemetry.

**Never use the OpenTelemetry pipeline as an event store.** The safe, high-value
pattern is to emit an observability event *about* a domain event as it is handled —
turning the existing domain-event flow into trace-correlated telemetry:

- `event.name` ← the Protobuf message **type** name (e.g. `acme.orders.OrderPlaced`),
  which is naturally low-cardinality;
- attributes ← selected proto fields plus `ScopedLoggingContext` (aggregate id,
  bounded context, user);
- correlation ← the active span, automatically.

This bridge belongs in the **consumer** application, not in this module: it needs the
Spine domain/proto types, which live in the service's own code (and depends on
`io.spine:spine-server`), not in the logging library. A sketch in a consumer:

```kotlin
// In a consumer service, when a domain event is handled/applied:
fun onDomainEvent(event: EventMessage) {
    logger.atInfo()
        .with(EVENT_NAME, event.typeName())   // proto type name → event.name
        .with(AGGREGATE_ID, /* … */)           // selected fields → attributes
        .log { "Domain event handled." }
}
```

### Aligned with OTEP 4430

Events are emitted through the **Logs API** (`emit(eventName = …)`), never
`Span.AddEvent` — the direction endorsed by [OTEP 4430][otep-4430] (Span Event API
deprecation).

[event-name]: src/commonMain/kotlin/io/spine/logging/backend/otel/OtelEvents.kt
[otel]: https://opentelemetry.io/
[otel-kotlin]: https://github.com/open-telemetry/opentelemetry-kotlin
[otel-events]: https://opentelemetry.io/docs/specs/semconv/general/events/
[otep-4430]: https://github.com/open-telemetry/opentelemetry-specification/blob/main/oteps/4430-span-event-api-deprecation-plan.md
