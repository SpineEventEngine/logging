# Plan: Merge MiddlemanApi into LoggingApi

This plan consolidates the JVM-specific fluent logging API into the common LoggingApi while preserving behavior, providing a migration path, and avoiding breaking changes.

## Goals
- [ ] Unify logging API surface across platforms under LoggingApi
- [ ] Preserve existing fluent behavior and rate-limiting semantics
- [ ] Keep binary compatibility for JVM users of MiddlemanApi during a deprecation window
- [ ] Reduce duplication of extensions and documentation
- [ ] Prepare the codebase for the eventual Middleman → JvmLogger consolidation

## High-level relationships (summary)
- [ ] MiddlemanApi is the JVM fluent API returned by Middleman at(level), intended to match and extend LoggingApi semantics.
- [ ] LoggingApi is the common (multiplatform) fluent API used broadly across the codebase.
- [ ] ScopedLoggingContext and “per(…)” aggregation semantics must remain available from the unified API.
- [ ] Middleman-specific extensions and helpers currently live in JVM sources and mirror common API needs.

## Phase 1 — API inventory and surface alignment
- [ ] Inventory all public methods in MiddlemanApi and LoggingApi, focusing on:
    - [ ] Methods present in both (identical signature/semantics).
    - [ ] Methods present only in MiddlemanApi (JVM-only, context-specific, or convenience).
    - [ ] Methods present only in LoggingApi that MiddlemanApi users rely on indirectly.
- [ ] Document meaningful semantic differences (rate limiting, isEnabled, per(…) aggregation, nullability).
- [ ] Decide placement for JVM-only capabilities:
    - [ ] If universally useful and portable, add to LoggingApi.
    - [ ] If truly JVM-only, use expect/actual or move to LoggingApi extensions (JVM source set).
    - [ ] If purely convenience, implement as JVM extensions.

## Phase 2 — Extend LoggingApi to cover MiddlemanApi gaps
- [x] Add missing cross-platform methods from MiddlemanApi to LoggingApi where behavior is platform-neutral (e.g., withCause, rate limiting controls, isEnabled, log variants).
- [ ] For JVM-only features:
    - [ ] Implement per(scopeProvider) as an extension on LoggingApi in JVM sources.
    - [x] Introduce small platform abstractions to preserve semantics on other targets (no-ops where unsupported).
- [ ] Ensure method chaining remains fluent and preserves API type.

## Phase 3 — Migrate Middleman to return LoggingApi
- [ ] Change Middleman.Api to extend LoggingApi (or alias to it), and adapt Middleman.at(level) to return LoggingApi-compatible context objects.
- [ ] Replace MiddlemanApi.NoOp with a LoggingApi-compatible no-op implementation.
- [ ] Ensure rate limiting, per(…) aggregation, and isEnabled checks use shared LoggingApi logic.
- [ ] Keep behavior parity (including forced logging behavior and “skipped” counters where applicable).

## Phase 4 — Compatibility layer and deprecation
- [ ] Introduce typealias MiddlemanApi = LoggingApi in JVM module (if binary compatibility allows) OR provide a deprecated interface MiddlemanApi that extends LoggingApi.
- [ ] Mark MiddlemanApi as @Deprecated with a clear replacement: use LoggingApi.
- [ ] Provide migration KDoc and examples showing unchanged call-sites (fluent chain remains).
- [ ] Keep MiddlemanApi extensions delegating to LoggingApi extensions or re-exporting functionality.

## Phase 5 — Extensions unification
- [ ] Move shared extensions to common sources as LoggingApi extensions when possible.
- [ ] Keep JVM-only extensions under JVM sources and ensure imports remain stable.
- [ ] Remove MiddlemanApi extensions duplication by re-targeting to LoggingApi or re-exporting.

## Phase 6 — Testing and verification
- [ ] Port MiddlemanApi tests to target LoggingApi, including:
    - [ ] isEnabled guard semantics
    - [ ] Rate-limiting behavior (every, onAverageEvery, atMostEvery)
    - [ ] per(…) aggregation across keys, enums, and scope providers
    - [ ] withCause and synthetic stack trace helper chaining
    - [ ] No-op API behavior and short-circuiting
- [ ] Back-compat tests ensuring old code using MiddlemanApi continues to compile/run.
- [ ] Performance checks for fluent no-op paths and rate-limiter hot paths.

## Phase 7 — Documentation and migration
- [ ] Update KDoc for LoggingApi to reflect combined responsibilities and usage patterns.
- [ ] Add migration notes and examples (Before: MiddlemanApi; After: LoggingApi).
- [ ] Update any samples, READMEs, or tutorials referencing MiddlemanApi.
- [ ] Note any platform-specific limitations or no-ops.

## Phase 8 — Cleanup and removal window
- [ ] Changelog entry announcing the merge, deprecation period, and migration steps.
- [ ] After deprecation window:
    - [ ] Remove MiddlemanApi interface and direct references.
    - [ ] Remove redundant MiddlemanApi extensions or keep thin re-exports where necessary.
- [ ] Keep Middleman logger as a JVM entry point for now (separate task: merge with JvmLogger).

## Risk management and roll-back
- [ ] Keep changes behind a minor-version feature flag or branch until stability is confirmed.
- [ ] Validate binary/source compatibility in downstream consumers before release.
- [ ] Provide a quick rollback plan: retain MiddlemanApi with delegation to LoggingApi if unexpected breakage occurs.

## Definition of done
- [ ] LoggingApi fully supersedes MiddlemanApi in functionality.
- [ ] Middleman returns LoggingApi fluent contexts.
- [ ] Deprecations published; migration docs available.
- [ ] Tests green; performance unaffected in hot paths.
- [ ] Duplicated extensions removed or unified; scoped context behavior verified.
