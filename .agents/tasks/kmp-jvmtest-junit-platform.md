# Enable JUnit Platform for KMP `jvmTest` in the `kmp-module` convention

## Problem

`kmp-module.gradle.kts` never applies `module-testing` (it cannot: that plugin
applies `java-library`, which conflicts with `kotlin("multiplatform")`), so the
KMP `jvmTest` task runs without the JUnit Platform and silently discovers zero
JUnit 5/Kotest tests. Verified: `./gradlew :logging:jvmTest --rerun` passed in
~1 s with no test reports while 13 `*Spec.kt` files exist under
`logging/src/jvmTest/`. `backends/otel-backend/build.gradle.kts` carries a
module-local workaround.

Recorded as the "Collateral finding" in
`.agents/tasks/otel-backend-validation.md` (main checkout).

## Plan

1. In the sibling `config` checkout, extend `kmp-module.gradle.kts`:
   configure `tasks.named<Test>("jvmTest")` with `useJUnitPlatform()` and
   `configureLogging()` — mirroring `module-testing.setupTests()` but without
   `includeEngines("junit-jupiter")`, because `kmp-module` itself adds the
   Kotest runner (engine `kotest`) to `jvmTest` dependencies.
2. Apply the identical change to this repo's `buildSrc` copy (anticipating the
   config float; `./config/pull` will overwrite with the same content once the
   config change lands).
3. Remove the module-local workaround from
   `backends/otel-backend/build.gradle.kts` (the `tasks.named<Test>("jvmTest")`
   block). Keep its `registerTestTasks()` call — the convention does not
   register `fastTest`/`slowTest`.
4. Verify with JDK 17: `:logging:jvmTest --rerun` executes the 13 jvm specs
   (plus commonTest specs and Java tests), and `:backends:otel-backend:jvmTest`
   still executes its tests without the workaround.

## Status

- [x] Config repo edited — committed by the user as `512c1068` on the
      `address-logging-audit-finding` branch of the `config` checkout
- [x] Local `buildSrc` copy updated
- [x] otel-backend workaround removed
- [x] `:logging:jvmTest` executes the 13 jvm specs — 232 tests ran
      (previously zero); all 13 spec classes have result files
- [x] `:otel-backend:jvmTest` still executes its 22 tests (all pass);
      `:logging-testlib:jvmTest` now runs 3 tests (pass);
      `:tests:fixtures` has no test sources (NO-SOURCE)

## Latent-failure triage (resolved)

Enabling the platform surfaced **25 latent test failures** (232 run,
207 pass) — behavior drift accumulated while the task silently ran
nothing. They were triaged in a dedicated session; its fixes were
adopted onto this branch. `:logging:jvmTest` now passes 232/232.

Production bugs found and fixed by the triage (tests were right):

- `AbstractLogger.atConfig()` delegated to `Level.INFO` instead of
  `Level.CONFIG`.
- `MetadataHandler.Builder.addRepeatedHandler` had inverted validation
  (rejected repeatable keys instead of requiring them; drift from the
  `custom-metadata` PR #144).
- `MetadataKey.cast()` returned `null` instead of throwing the
  documented `ClassCastException`; `checkCannotRepeat` threw
  `IllegalStateException` where callers expect `IllegalArgumentException`.
- `SimpleProcessor` "wrapped" repeated-value lists with a no-op cast;
  handlers could mutate them. Replaced with an unmodifiable iterator.
- `ScopedLoggingContext.Builder` lacked a member `run(Runnable)`, so
  Kotlin's stdlib `run` extension executed blocks *without installing
  the context*.
- Lazy log messages were evaluated outside the recursion guard of
  `AbstractLogger.write`, so throwing/reentrant `toString()` escaped
  error handling; timestamps lacked millis and UTC offset.

Test-side fixes (production was right): logger-name expectations
(`kotlin.String` vs `java.lang.String`), synthetic lambda method names,
eager invocation counting for rate-limiter specs, Kotlin spread
operator for `logVarargs`, updated message-text expectations.
