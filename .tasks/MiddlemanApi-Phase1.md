# Phase 1 — API inventory and surface alignment (MiddlemanApi vs LoggingApi)

This internal note inventories public APIs to guide Phase 2 and later phases.

Scope of comparison:
- JVM: io.spine.logging.jvm.MiddlemanApi
- Common: io.spine.logging.LoggingApi

Overlap (same or equivalent semantics):
- isEnabled(): Boolean — both
- log(); log(() -> String?) — both
- every(n: Int) — both (n > 0)
- atMostEvery(n, unit) — MiddlemanApi uses java.util.concurrent.TimeUnit (JVM); LoggingApi uses kotlin.time.DurationUnit (common). Semantics equivalent.
- withInjectedLogSite(...) — both have API, but types differ:
  - LoggingApi: withInjectedLogSite(LogSite) — multiplatform
  - MiddlemanApi: withInjectedLogSite(JvmLogSite?) and agent-only overload with internal parameters — JVM-only

MiddlemanApi-only (JVM-specific or convenience):
- onAverageEvery(n: Int) — sampling rate limiter
- per(T?, strategy: LogPerBucketingStrategy<in T>) — JVM-only (type lives in jvmMain)
- per(scopeProvider: LoggingScopeProvider) — JVM-only, integrates with ScopedLoggingContext
- withStackTrace(size: StackSize) — JVM-only
- with(key: MetadataKey<T>, value: T?) and with(key: MetadataKey<Boolean>) — JVM-focused structured metadata API variant (uses jvm MetadataKey class)
- withInjectedLogSite(internalClassName, methodName, encodedLineNumber, sourceFileName) — agent-only JVM hook

LoggingApi-only (common):
- withLoggingDomain(domain: LoggingDomain)

Semantic deltas and nullability:
- withCause: MiddlemanApi accepts Throwable?; LoggingApi originally accepted non-null Throwable. DECISION: make LoggingApi accept nullable Throwable? to align with Middleman semantics (null = no-op).
- per(Enum<*>): MiddlemanApi accepts nullable enum; LoggingApi originally required non-null. DECISION: make LoggingApi accept nullable Enum<*>? (null = no-op) to align.
- Rate limiting: semantics are equivalent for every(...) and atMostEvery(...). onAverageEvery(...) added to LoggingApi in Phase 2 (platform-neutral API, JVM implementation delegates to Middleman).

JVM-only pieces to make Kotlin multiplatform so LoggingApi can expose more MiddlemanApi functions:
- LogPerBucketingStrategy (new common abstraction) — expect/actual.
  - Minimal common API: name/identity and a bucketOf(key: Any?) function or equivalent, without JVM-only dependencies.
  - JVM actual keeps current behavior; other platforms can provide simple strategies or no-op bucketing with bounded maps.
- Scoped logging aggregation API — introduce a common LoggingScopeProvider (or LoggingScopeKey) expect/actual.
  - Common: a marker/functional type to obtain current scope token by key.
  - JVM: actual type bridges to existing io.spine.logging.jvm.LoggingScopeProvider and ScopedLoggingContext. Other platforms: no-op scopes that always return null.
- Stack trace capture policy — StackSize enum and synthetic stack trace type.
  - Move StackSize to common as expect/actual (or mirror enum in common with platform capabilities notes).
  - Provide a common LogSiteStackTrace-like abstraction (name TBD) or adjust API to use StackSize only and let backends decide whether to attach synthetic trace.
- Structured metadata key compatibility in fluent API — enable with(key, value) on LoggingApi using common MetadataKey.
  - Common already has io.spine.logging.MetadataKey; add LoggingApi.with(key: MetadataKey<T>, value: T?) and with(key: MetadataKey<Boolean>).
  - JVM actual implementation continues to use existing plumbing; other platforms can store metadata in a simple per-call map for backends that ignore it.
- Log site injection type alignment.
  - Prefer common LogSite in LoggingApi (already exists). For parity, Middleman-specific JvmLogSite overload remains JVM-only; no multiplatform action needed beyond ensuring backends can accept common LogSite.

Placement decisions for remaining JVM-only capabilities (stay JVM-specific):
- Agent-only withInjectedLogSite overload with internalClassName/methodName/encodedLineNumber/sourceFileName — remain JVM-only.

Notes:
- Fluent chaining type remains non-wildcard via JvmLogger.Api on JVM and LoggingApi<API> in common.
- No behavior changes intended for existing common API aside from null-accepting overloads, which are no-ops on null.

Actionable checklist for Phase 2 based on the above:
- Add to common LoggingApi: onAverageEvery(n) ✓ (Already present)
- Add to common LoggingApi: with(key: MetadataKey<T>, value: T?) and with(key: MetadataKey<Boolean>) — requires common MetadataKey alignment.
- Introduce expect/actual: LogPerBucketingStrategy minimal interface in common; adapt JVM implementation.
- Introduce expect/actual: LoggingScopeProvider (or common scope key), with JVM actual delegating to existing scopes; other platforms as no-op.
- Introduce expect/actual: StackSize; adjust Middleman/JVM to use common type where possible.
- Keep agent-only withInjectedLogSite(...) overload JVM-only.
