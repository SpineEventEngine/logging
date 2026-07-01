---
slug: otel-backend-validation
branch: master
owner: claude
status: review-complete
started: 2026-07-01
related-memories: []
---

# Validation of the `otel-backend` and `otel-backend-bootstrap` modules

Independent post-merge validation of the OpenTelemetry backend introduced by
[`otel-backend-implementation.md`](otel-backend-implementation.md) (merged through PR #144,
`ad1beaef`). Findings below are ordered by severity; each was adversarially verified before
inclusion.

## Method

1. **Empirical**: `./gradlew :otel-backend:build :otel-backend-bootstrap:build` (JDK 17) is
   green — detekt, Kover, license checks pass. Tests re-run with `--rerun` (bypassing the
   build cache): **22/22** backend + **9/9** bootstrap tests genuinely pass.
2. **Multi-agent review**: 8 independent reviewers (mapping correctness, OTel spec/semconv
   conformance, concurrency/lifecycle, Kotlin standards, Spine repo rules, docs, tests,
   build/publishing) produced 59 raw findings → 45 after dedup → each verified adversarially
   (3-lens panel for critical/major: code evidence, external spec/library sources, impact).
   **41 confirmed, 4 refuted**, 0 uncertain. Library claims were verified against the pinned
   `opentelemetry-kotlin` **0.4.0** sources via the `api-discovery` skill and against
   opentelemetry.io specs.

## Verdict

The core `LogData → Logger.emit(...)` mapping is sound and well-tested: body without
`[CONTEXT]`, severity thresholds, timestamp passthrough, cause-as-exception, typed/repeated
attributes, tags, trace correlation, and the events surface all verify correctly end-to-end.
The architecture (API-only backend + optional bootstrap owning the SDK) follows the plan and
the OTel guidance.

However, **one critical and four major defects should be fixed before the artifacts are
consumed**: the bootstrap's `close()` provably loses buffered records, the OTLP env-var
handling inverts spec semantics, the log-site attributes use semconv names that the pinned
library itself marks `@Deprecated`, custom `MetadataKey.emit` overrides are bypassed
(redaction leak), and once the SDK is installed there is no level filtering at all.

---

## Critical

### C1. Bootstrap `close()` never flushes — buffered records are lost on every shutdown

`OtelLogging.kt:95-102` — the returned handle only calls `shutdown()`. Verified in the
0.4.0 sources: `BatchTelemetryProcessor.shutdown()` is
`shutdownState.shutdown { scope.cancel(); Success }` — it cancels the flusher coroutine
**without draining the queue**, and `BatchLogRecordProcessorImpl.shutdown()` shuts the
exporter down *before* the processor. The batch flusher ticks every 1000 ms
(`SCHEDULE_DELAY_MS`), so every `close()` silently discards up to the last second of records
(max queue 2048). A short-lived process (CLI tool, batch job) loses essentially everything; a
long-running service always loses its shutdown-sequence logs. The code comment claims
"then flush and shut it down" — it does not flush. (The OTel spec says Shutdown SHOULD
include the effects of ForceFlush; 0.4.0 does not, so the bootstrap must compensate.)

**Fix**: `runBlocking { openTelemetry.forceFlush(); openTelemetry.shutdown() }`
(`forceFlush` is on `TelemetryCloseable` and joins the batch drain within its 5 s timeout);
report a flush `Failure` through `reportShutdown` too. Residual caveat to document: the
0.4.0 OTLP exporter posts asynchronously with a no-op `forceFlush`, so shutdown can still
cancel a just-launched HTTP post — a pinned-library limitation to re-check on version bumps.
Add a test asserting queued records survive `close()` (use a recording/synchronous exporter,
not the OTLP one). Fix the misleading comment either way.

## Major

### M1. `OTEL_EXPORTER_OTLP_LOGS_ENDPOINT` is given base-URL semantics, violating the OTLP spec

`OtelLogging.kt:154-163` — per the OTLP exporter spec, the signal-specific variable "MUST be
used as-is without any modification" (it already contains `/v1/logs`); only the generic
`OTEL_EXPORTER_OTLP_ENDPOINT` is a base URL. Both are resolved identically and fed to
`otlpHttpLogRecordExporter(endpoint)`, which in 0.4.0 unconditionally appends the path
(`OtlpClient.kt:54`: `"$baseUrl/${endpoint.path}"`). A user migrating with the spec-mandated
`OTEL_EXPORTER_OTLP_LOGS_ENDPOINT=http://collector:4318/v1/logs` posts to
`…/v1/logs/v1/logs` → 404 → **all records dropped** (surfaced only by the library's stray
`println`). A trailing slash on the generic endpoint also yields `…//v1/logs`.

**Fix**: when the logs-specific variable is set, honor as-is semantics (strip a trailing
`/v1/logs` before handing it to the exporter, since 0.4.0 offers no no-append option); trim
trailing slashes; append-derive only from the generic variable/default. Document the
semantics on the public `fromEnvironment()` KDoc and in `docs/otel-backend.md`; extend
`OtelLoggingSpec` precedence tests with the full-URL form.

### M2. Log-site attributes use deprecated semconv names

`AttributeMapping.kt:55-60` emits `code.namespace`, `code.function`, `code.filepath`,
`code.lineno`. Semconv deprecated these in v1.30.0 and stabilized the replacements in
v1.34.0 (June 2025): `code.function.name` (fully qualified, absorbing `code.namespace`),
`code.file.path`, `code.line.number`. Decisively, the pinned library's own
`io.opentelemetry.kotlin:semconv:0.4.0` marks every emitted name `@Deprecated` and ships the
stable constants. Attribute names are wire/query surface — renaming after release breaks
consumers, and this brand-new backend has no legacy users to stay compatible with.

**Fix (before first release)**: emit `code.function.name = "${className}.${methodName}"`,
`code.file.path`, `code.line.number` — either as literals or via the library's
`CodeAttributes` constants (requires adding the `semconv` artifact to
`OpenTelemetryKotlin`). Update `putLogSite` KDoc, the mapping table in
`docs/otel-backend.md:87`, and the two log-site tests in the same change.

### M3. Custom `MetadataKey.emit`/`emitRepeated` overrides are bypassed

`AttributeMapping.kt:74-86` maps every non-special key via `putValue(key.label, value)`,
never calling `key.safeEmit`/`safeEmitRepeated`. Overriding `emit` is the documented SPI
extension point (`MetadataKey.kt:209-237`) — e.g. redacting a secret or exploding a struct
into `label.suffix` pairs — and just gained explicit coverage (`50e14060`). Text-path
backends honor it via `MetadataKeyValueHandlers`; here the raw value (`value.toString()` for
arbitrary types) goes to a remote collector. For a redacting key this **leaks the unredacted
value off-host**. Nuances from verification: log4j2's structured context-map path takes the
same shortcut (in-repo precedent), and `safeEmit`'s string-oriented handler would lose the
typed-attribute fidelity — a real design tension.

**Fix**: route keys with custom emit logic through `safeEmit` (the SPI's private `isCustom`
flag may need exposing), keeping the typed fast path for factory-created keys. At minimum,
document in `putMetadata` KDoc and `docs/otel-backend.md` that custom emit overrides are not
honored and raw values are exported.

### M4. No level filtering once the SDK is installed — everything is exported

`OtelLoggerBackend.kt:90-91` delegates `isLoggable` to `logger.enabled(severityNumber)`, but
in 0.4.0 `BatchLogRecordProcessorImpl.enabled(...)` is literally `!shutdownState.isShutdown`
— the severity argument is ignored, and the config DSL has no minimum-severity option. So
after `OtelBackendSettings.use(sdk)`, every `FINEST`/`DEBUG` statement in every library is
formatted, queued, serialized, and posted over OTLP. Verification hardened this: Spine's
fluent gate is `isLoggable || isForced`, and a `LogLevelMap` can only *add* logging (only a
mapping to `OFF` suppresses) — there is effectively **no filtering knob anywhere**. Sibling
jul/log4j2 backends inherit the framework's level config (default `INFO`). Neither README
nor guide mentions the export-everything behavior.

**Fix**: add a minimum level to the backend (e.g. an `OtelBackendSettings` threshold checked
in `isLoggable` before `enabled(...)`), defaulting to something sane (`INFO`?). At minimum,
document the behavior prominently. An escape hatch exists today (a user-built severity-
filtering `LogRecordProcessor` decorator in the `export { }` DSL) and could be documented.

## Minor

- **`handleError` diagnostics are useless** (`OtelLoggerBackend.kt:121`): `badData.toString()`
  is an identity hash (`LogContext` never overrides `toString`), and log site/timestamp are
  omitted — compare log4j2's `formatBadLogData`. Format message/metadata/level/timestamp
  explicitly (try/catch-guarded) and reuse `putLogSite(badData)`.
- **`close()` is not idempotent and unconditionally restores noop**
  (`OtelLogging.kt:95-102`): closing a stale handle uninstalls a newer live SDK; a second
  `close()` re-runs `shutdown()`. Requires violating the documented single-install contract,
  hence minor (downgraded from major by the verify panel) — but a conditional, guarded reset
  needs a public CAS-style hook on `OtelBackendSettings` (`current()` is `internal` and the
  bootstrap is a separate module, so it cannot compare-and-set today).
- **`close()` blocks and that is undocumented** (`OtelLogging.kt:99`): `runBlocking` was
  adjudicated acceptable in the prior review (recorded in the implementation task doc), but
  nothing warns that `close()` blocks the caller (~3 s bound: `withTimeout(3000)` wraps the
  whole SDK shutdown) and must not be called from a coroutine. Document it; consider a
  suspend-first `shutdown()` on a small handle type, keeping `AutoCloseable` as the bridge.
- **Stray stdout noise from the turnkey pipeline** (`OtelLogging.kt:89`): 0.4.0's
  `OtlpHttpLogRecordExporter` contains a leftover `println("OTLP exported log: …")` per
  non-empty batch — pollutes stdout collectors and leaks error bodies. Document as a known
  0.4.0 limitation; consider wiring the exporter without the convenience builder.
- **Per-call logger resolution allocates** (`OtelLoggerBackend.kt:88`): 0.4.0's
  `getLogger` rebuilds an `InstrumentationScopeInfoImpl` key (+ fresh `mutableMapOf`) on
  every call; the backend resolves twice per emitted statement. The KDoc claim "the lookup
  is cheap" is wrong for the SDK provider. Cache `Pair<OpenTelemetry, Logger>` per backend,
  re-resolving on identity change of `current()` — preserves the install/replace semantics.
- **Same-label distinct keys overwrite each other's attribute** (`AttributeMapping.kt:76`):
  last-wins, lossier than the text backends which render both. Document, or merge collisions
  into a list.
- **`ByteArray` (and all arrays) degrade to identity strings** (`AttributeMapping.kt:97`):
  `setByteArrayAttribute` exists in 0.4.0; at least render arrays via `contentToString()`.
- **`DEBUG4` KDoc is inverted** (`SeverityMapping.kt:44`): DEBUG4=8 is the *most severe*
  debug sub-level (adjacent to INFO), not "the most detailed". The mapping is right; the
  rationale text will mislead anyone mapping custom levels.
- **`auto-service-annotations` leaks into the published POM as `runtime`**
  (`otel-backend/build.gradle.kts:69`): verified in the published `.m2` POM. Use
  `compileOnly(AutoService.annotations)`; KSP path is unaffected.
- **README pairs `runtimeOnly` with a snippet that cannot compile under it**
  (`backends/otel-backend/README.md:16`): `OtelBackendSettings.use(...)` needs
  `implementation`. The guide carries the caveat; the README omits it.
- **Bootstrap artifact coordinates appear nowhere** (`docs/otel-backend.md:62`): the
  "Turnkey OTLP wiring" section never says
  `implementation("io.spine:spine-logging-otel-backend-bootstrap:$version")`.
- **Doc/line-length nits**: mapping-table rows are 121–132 chars (limit 100) — strip the
  decorative padding (`docs/otel-backend.md:81-90`); `OtelLoggerBackendSpec.kt:306` is 101
  chars (jvmTest is outside detekt's source set, so the build does not catch it); the
  `OtelBackendSettings` KDoc parity note ("no other shipped backend…") will rot — keep only
  the durable lazy-resolution contract.

### Test-coverage gaps (all minor)

- `isLoggable` false-path under the default noop instance — the drop-before-install
  contract, the module's most consequential behavior — has no direct assertion (the
  true-path is covered indirectly by the `logEvent` end-to-end tests; Kover shows the method
  itself covered).
- Severity aliases (`SEVERE→ERROR`, `FINE→DEBUG`, notably `TRACE→TRACE2` — a surprising
  name mismatch) and between-threshold custom levels are unasserted; convert the mapping
  test to `@ParameterizedTest` so failures name the level.
- Bootstrap `close()` contract is only tested as "does not throw": nothing asserts noop
  restoration, `reportShutdown` wiring from the real close path, or double-close.
- `handleError` body text and the `?: error` null-message branch are unasserted.
- Numeric widening (single `Byte`/`Short`/`Float`), repeated numeric lists, and the
  mixed-numeric `[1, 2.5]` → *string* list fallback (lossy where a double list exists —
  reconsider before the surface hardens) are untested.
- Tags: non-string tag values, mixed-type multi-value tags, and the `TAG_PREFIX` collision
  rationale (metadata `user` + tag `user` coexisting) are unpinned.
- Two `OtelLoggingSpec` tests read the real environment and fail (or build an exporter
  against a live URL) on machines with `OTEL_EXPORTER_OTLP_*` set — guard with JUnit
  assumptions or drop in favor of the injectable-`getenv` overload.
- Factory tests sit inside `OtelLoggerBackendSpec` (siblings keep a dedicated factory
  spec), and `ServiceLoader` discovery of the `@AutoService` registration has no direct
  test.

## Suggestions

- **Tag attribute type flaps by cardinality** (`AttributeMapping.kt:119-131`): `tag.ids` is
  a string on one record, a list on the next — columnar backends index by type. Pick one
  stable shape or document the instability.
- **Severity mapping diverges from data-model Appendix B / the OTel Java agent** (JUL
  example: FINER→DEBUG, FINE→DEBUG2, CONFIG→DEBUG3, vs. this backend's TRACE2/DEBUG/DEBUG4):
  non-normative, but mixed pipelines (agent + this backend) filter inconsistently. Align or
  document the divergence.
- **Event-name casing**: recommending proto type names (`acme.orders.OrderPlaced`) deviates
  from the semconv lowercase/snake_case SHOULD while citing the conventions; either
  recommend a lowercase derivation or note the deliberate deviation.
- **`fromEnvironment()` honors only the endpoint variables**: `OTEL_EXPORTER_OTLP_HEADERS`
  (needed for any authenticated endpoint), `_PROTOCOL`, `_TIMEOUT` are ignored; 0.4.0's
  `otlpHttpLogRecordExporter(baseUrl, httpClient)` overload makes headers implementable.
  Support headers or state the limitation.
- **`EVENT_NAME` is a generic top-level public identifier**: Spine's own keys are grouped
  (`LogContext.Key`); consider `OTEL_EVENT_NAME` or a grouping object before release.
- **`logEvent` reachability and home**: only a `WithLogging` extension, and it lives in the
  backend module — calling it forces `implementation` on the backend, dragging the
  experimental OTel API onto the app's compile classpath though `logEvent` touches no OTel
  type. Add a `Logger.logEvent` extension; consider a backend-neutral home for the events
  surface.
- **`@Volatile` in `commonMain`** (`OtelBackendSettings.kt:54`): resolves to
  `kotlin.jvm.Volatile`; import `kotlin.concurrent.Volatile` so the guarantee survives
  adding non-JVM targets (a new target would fail to compile today — loud, so low risk).
- **`OpenTelemetryKotlin` duplicates artifact names** between properties and `modules` —
  deviates from the version-less-property convention of sibling dependency objects.
- **Bootstrap uses `io.spine.annotation.VisibleForTesting` via a 3-hop transitive chain**;
  declare `Base.annotations` directly.
- **`RecordingLogRecordProcessor` stores live mutable records**; snapshot asserted fields at
  emit time.
- Optional bounded stress test for concurrent `use()` swaps while logging (the `@Volatile`
  single-read-per-emit pattern is the actual safety argument — currently correct).
- README top heading is `##` (no H1) — shared by all backend READMEs; fix repo-wide or
  codify the convention.
- Minor grammar: number agreement in the domain-events parenthetical
  (`docs/otel-backend.md:143`).

## Examined and dismissed (refuted by verification)

- *"`@file:OptIn` launders the experimental opt-in out of `use(OpenTelemetry)`"* — false:
  empirically compiled a consumer against the published JAR; Kotlin ≥1.9 propagates the
  opt-in through signature-exposed types, so consumers do get the warning.
- *"Unresolvable KDoc link breaks `dokkaGenerate`"* — `dokkaGeneratePublicationHtml --rerun`
  passes with `failOnWarning` active. (The `[OpenTelemetry]` link in `OtelBackendFactory`
  does render unresolved in the HTML — cosmetic only, worth a `[]()`-style fix sometime.)
- *"Late-install pickup is untested"* — mutation testing showed eager logger capture makes
  `use the lazily supplied message as the event body` fail: the JVM-global backend cache
  means the second `logEvent` test exercises exactly the late-install path.
- *"`OtelLoggingSpec` leaks a live SDK without `@AfterEach`"* — `close()` restores noop as
  its first statement, before anything that can throw.

## Collateral finding (outside this module — action needed in `config` repo)

The module-local `useJUnitPlatform()` workaround (`otel-backend/build.gradle.kts:99-105`)
compensates for a real gap in the shared `kmp-module` convention, which never applies
`module-testing`. Verified first-hand: **the core `logging` module's `jvmTest` silently
discovers zero tests** — `./gradlew :logging:jvmTest --rerun` "passes" in 1 s with no test
reports while 13 `*Spec.kt` files exist under `logging/src/jvmTest`. The fix belongs in
`kmp-module.gradle.kts` in the `config` repository; it would also let the local workaround
here be deleted.

## Suggested fix order

1. **C1** (flush on close) + **M1** (env-var semantics) — bootstrap data-loss fixes, small
   diffs, plus tests.
2. **M2** (semconv names) — must land before the artifact is consumed; wire-format surface.
3. **M4** (level filtering) and **M3** (custom-key emit) — need a small design decision
   each (threshold API shape; typed-vs-safeEmit routing).
4. `config` repo: `kmp-module` test wiring (restores the core `logging` jvmTest suite).
5. Minor code/doc fixes and the test-coverage batch — mechanical, can ride along.
