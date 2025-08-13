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

Placement decisions for JVM-only capabilities:
- per(T?, LogPerBucketingStrategy): remain JVM-only extension(s) in a future phase (require jvm type).
- per(scopeProvider: LoggingScopeProvider): implement as JVM extension on JvmLogger.Api in a future phase (needs access to underlying Middleman or alternative plumbing).
- withStackTrace(StackSize): JVM-only extension in a future phase.
- Metadata with(...) methods: consider multiplatform story later; keep as JVM-only for now.
- Agent-only withInjectedLogSite overload: JVM-only.

Notes:
- Fluent chaining type remains non-wildcard via JvmLogger.Api on JVM and LoggingApi<API> in common.
- No behavior changes intended for existing common API aside from null-accepting overloads, which are no-ops on null.
