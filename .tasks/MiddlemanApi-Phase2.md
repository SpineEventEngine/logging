# Phase 2 — Extend LoggingApi to cover MiddlemanApi gaps

Inputs:
- Merge-MiddlemanApi.md
- Middleman-LoggingApi-Phase1.md

Outcome:
- LoggingApi provides a unified fluent surface covering the MiddlemanApi features, with JVM-only parts provided via extensions or expect/actual shims.

## 1) Rate limiting and enablement

- [ ] Verify LoggingApi.onAverageEvery(n: Int) exists and matches semantics
    - [ ] Preconditions: n > 0, n == 1 is a no-op
    - [ ] Ensure chaining preserves API type
    - [ ] Add KDoc with sampling semantics and interaction with other rate limiters
- [ ] Align atMostEvery parameterization
    - [ ] Provide a common API consistent across platforms (Duration/DurationUnit or equivalent)
    - [ ] On JVM, bridge to platform units internally
    - [ ] Add KDoc clarifying granularity and “skipped count” semantics
- [ ] Confirm isEnabled() guard semantics remain unchanged
    - [ ] Ensure no intermediate chain methods affect isEnabled()

## 2) Cause and nullability alignment

- [ ] Update LoggingApi.withCause(cause: Throwable?) to accept nullable
    - [ ] No-op on null
    - [ ] Last call wins if invoked multiple times
    - [ ] Update KDoc and cross-reference existing usage guidance

## 3) Structured metadata support

- [ ] Add LoggingApi.with(key: MetadataKey<T>, value: T?)
    - [ ] Null key must throw
    - [ ] Null value is allowed (document backend behavior); the call is recorded
    - [ ] Ensure fluent chaining returns same API type
- [ ] Add LoggingApi.with(key: MetadataKey<Boolean>) convenience
    - [ ] Implement as with(key, true)
    - [ ] KDoc: intended for backend-recognized flags
- [ ] Ensure compatibility with existing MetadataKey in common sources
    - [ ] No platform-specific dependencies in the common API

## 4) Aggregation: per(...) family

- [ ] Introduce expect/actual for LogPerBucketingStrategy in common
    - [ ] Minimal surface: bucketOf(key: Any?) (or equivalent) and identity semantics
    - [ ] JVM actual: adapt existing behavior; preserve bounded-bucket guarantees
    - [ ] Other platforms: provide usable baseline or bounded no-op
- [ ] LoggingApi.per(key: T?, strategy: LogPerBucketingStrategy<in T>)
    - [ ] No-op on null key
    - [ ] Combine with other per(...) keys (composite aggregation)
    - [ ] KDoc includes memory-leak cautions and bucketing guidance
- [ ] LoggingApi.per(key: Enum<*>?)
    - [ ] Accept nullable; no-op on null
    - [ ] Independent aggregation per enum value
- [ ] Introduce expect/actual for LoggingScopeProvider (or ScopeKey)
    - [ ] Common: marker/functional type, docs describe behavior
    - [ ] JVM actual: bridge to existing scoped logging
    - [ ] Other platforms: safe no-op implementation
- [ ] LoggingApi.per(scopeProvider: LoggingScopeProvider)
    - [ ] No-op if not in scope
    - [ ] Document that scope must be explicitly used (not just being in a context)

## 5) Stack trace capture

- [ ] Introduce expect/actual StackSize in common
    - [ ] Enumerate practical sizes; allow platform to clamp
    - [ ] KDoc: performance and size notes
- [ ] Add LoggingApi.withStackTrace(size: StackSize)
    - [ ] JVM actual: synthetic exception attached as cause; preserves withCause chaining (user cause becomes cause of synthetic)
    - [ ] Other platforms: best-effort or no-op with clear docs

## 6) Log site injection alignment

- [ ] Keep LoggingApi.withInjectedLogSite(common LogSite) as the portable API
    - [ ] Ensure first call wins if invoked multiple times
    - [ ] No-op on null
- [ ] JVM-only overloads remain JVM-specific
    - [ ] Do not expose agent-only overloads in common
    - [ ] KDoc: discourage explicit injection except for helper methods with perf caveats

## 7) Source and binary compatibility considerations

- [ ] Maintain fluent non-wildcard API types to aid optimizer behavior
- [ ] Add null-accepting overloads where needed; avoid breaking existing signatures
- [ ] Ensure NoOp implementations enforce null-key check for metadata (consistent behavior)

## 8) Tests and verification (scoped to Phase 2 changes)

- [ ] Tests: withCause accepts null (no-op) and non-null (sets cause)
- [ ] Tests: with(key, value) behavior
    - [ ] Null key throws; null value recorded
    - [ ] Boolean overload forwards to true
- [ ] Tests: per(...) aggregation
    - [ ] Generic key with bucketing strategy
    - [ ] Enum key; null = no-op
    - [ ] Scope provider aggregation on JVM; no-op on other platforms
- [ ] Tests: withStackTrace size policy and chaining with withCause (JVM)
- [ ] Tests: onAverageEvery sampling behaves statistically over large N
- [ ] Tests: atMostEvery granularity notes hold; first call allowed

## 9) Documentation

- [ ] Update KDoc for new/changed LoggingApi methods
    - [ ] Reference semantics from Phase 1 decisions
- [ ] Document platform-specific behaviors and no-ops
- [ ] Note performance caveats (stack traces, injected log sites, sampling)

## 10) Implementation notes and handoffs

- [ ] Place platform-neutral APIs in common; JVM specifics as expect/actual or extensions
- [ ] Keep agent-only hooks confined to JVM internals
- [ ] Prepare small migration notes for Phase 4 deprecation work
