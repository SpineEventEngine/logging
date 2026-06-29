## OpenTelemetry backend

Routes Spine Logging statements to [OpenTelemetry][otel] as OTLP-ready log records,
using the Kotlin-multiplatform [`opentelemetry-kotlin`][otel-kotlin] API. Each Spine
`LogData` becomes a single `Logger.emit(...)` call, and named log statements become
log-based **events**. The backend depends only on the OpenTelemetry **API**; the
application (or the optional `otel-backend-bootstrap` module) owns the SDK.

> **Status:** experimental. `opentelemetry-kotlin` is pre-1.0 and its logs API is
> annotated `@ExperimentalApi`; this backend pins one exact version and may change with it.

Add the backend to the runtime classpath and inject an `OpenTelemetry` instance once,
at startup, before any logging happens:

```kotlin
runtimeOnly("io.spine:spine-logging-otel-backend:$version")
```

```kotlin
OtelBackendSettings.use(openTelemetry)
```

See the **[OpenTelemetry logging backend guide](../../docs/otel-backend.md)** for SDK
setup (including turnkey OTLP wiring), the `LogData`-to-record mapping, log-based events,
and the domain-event recipe.

[otel]: https://opentelemetry.io/
[otel-kotlin]: https://github.com/open-telemetry/opentelemetry-kotlin
