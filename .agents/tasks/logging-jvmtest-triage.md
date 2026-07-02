# Triage and fix the 25 latent `:logging:jvmTest` failures

## Problem

Enabling the JUnit Platform on the KMP `jvmTest` task (see
`kmp-jvmtest-junit-platform.md`, branch `claude/angry-hugle-d62cfe`) surfaced
25 latent test failures in `:logging:jvmTest` (232 run, 207 pass) which
accumulated while the task silently discovered zero tests.

Reproduce with JDK 17:
`./gradlew :logging:jvmTest --rerun` (requires the `kmp-module.gradle.kts`
fix from that branch; a copy is applied in this worktree).

## Triage results

Production fixes (behavior drifted from the Flogger contract during the
Kotlin migration):

1. `MetadataKey.cast` — returned `null` on type mismatch; now throws
   `ClassCastException` (Flogger parity; `null` input still passes through).
2. `checkCannotRepeat` — used `check` (ISE) with an inverted message; now
   `require` (IAE) per the documented contract of `findValue`/`getSingleValue`.
3. `MetadataHandler.Builder.addRepeatedHandler` — called `checkCannotRepeat`,
   the exact opposite of Flogger's `checkArgument(key.canRepeat())`; inverted.
4. `SimpleProcessor` — lost `Collections.unmodifiableList` in translation
   (`e.setValue(e.value as List<*>)` is a no-op cast); repeated-value
   iterators are now wrapped in a read-only `UnmodifiableIterator`.
5. `LogContext.log {}` — evaluated the message lambda *before*
   `AbstractLogger.write`, escaping the recursion guard and error handling;
   evaluation now happens inside `write(data, prepare)`.
   Also, a `null` message now reaches the backend unmodified
   (was string-concatenated into `"null"`).
6. `AbstractLogger` error-report timestamps — used `LocalDateTime.Formats.ISO`
   (no offset, variable millis); now `yyyy-MM-dd'T'HH:mm:ss.SSSZ` equivalent.
7. `AbstractLogger.atConfig()` — mapped to `Level.INFO` (copy-paste);
   now `Level.CONFIG`.
8. `LogPerBucketingStrategy.byClass()/byClassName()` — used
   `key::class`/`qualifiedName!!` (identity not guaranteed; NPE for local and
   anonymous classes); now `key.javaClass`/`key.javaClass.name`.
9. `ScopedLoggingContext.Builder.run(Runnable)` — missing member (silently
   shadowed by stdlib `kotlin.run`, which never installs the context); added.

Test fixes (tests carried stale or Java-specific expectations):

- `AbstractLoggerSpec` — case-insensitive match for the recursion message.
- `LogContextSpec` — `log { null }` expects a `null` literal (rendered as
  `"null"` by `SimpleMessageFormatter`), not `"<null>"`.
- `JvmLoggerSpec` — invocation counters moved out of lazy message lambdas
  (lambdas only run for statements which log); the log-site method name is
  the sanitized synthetic lambda method.
- `LogLevelMapSpec` — `String::class` registers as `kotlin.String`
  (consistent with `LoggingFactory` logger names), not `java.lang.String`.
- `LoggingCompatibilityTest` — Kotlin requires `*` spread to pass an array
  as `vararg`.
- `AnyExtsJvmSpec` — expect Spine's backtick diagnostic style.

## Status

- [x] All 25 failures reproduced and triaged
- [x] Production fixes applied
- [x] Test fixes applied
- [x] `:logging:jvmTest` green (232/232)
- [x] Full `./gradlew build --continue` green (all modules, detekt, Kover)

## Notes

- The `update-copyright` PostToolUse hook strips `The Flogger Authors; `
  from headers; restored in all touched files (keep watching this).
- Delete this file on merge to master.
