# Support custom Log4j2 configurations in the Log4j2 backend

## Problem

`toLog4jLogEvent(String, LogData)` in
`backends/log4j2-backend/.../LogEvents.kt` throws `IllegalStateException`
whenever the current Log4j2 configuration is not `DefaultConfiguration` —
i.e., whenever an application provides its own `log4j2.xml` (or any other
config source). This makes the backend unusable in any configured
application. Reported from `delivery-server`, which configures Log4j2 via
`log4j2.xml` packaged in its fat JAR.

## Analysis

- The Java original in this repo (`Log4j2LogEventUtil.java`, removed in
  `53559319` when converted to Kotlin) already threw in this branch, so the
  gap predates the Kotlin conversion.
- Upstream Google Flogger *does* implement this branch
  (`log4j2/.../Log4j2LogEventUtil.java`):
  `BaseMessageFormatter.appendFormattedMessage(logData, new StringBuilder())`
  — the plain log message without the `[CONTEXT ...]` metadata suffix.
  Rationale: with a user-provided configuration, metadata is carried by the
  log event's context-data map (populated by `createContextMap()`), which a
  custom layout renders via `%X`/`%K`. Appending it to `%msg` as well would
  duplicate it. The suffix is only needed under `DefaultConfiguration`,
  whose hard-wired console layout ignores context data.
- Spine's `LogData` carries no template arguments (`literalArgument` only;
  the Kotlin lambda API evaluates the message eagerly), so Flogger's
  `BaseMessageFormatter.appendFormattedMessage(...)` reduces to
  `SimpleMessageFormatter.getLiteralLogMessage(logData)`.

## Plan

1. In `LogEvents.kt`, replace the `error(...)` branch with
   `SimpleMessageFormatter.getLiteralLogMessage(logData)`. Restore the
   explanatory comment lost with the Java original (it referenced the
   deleted file's Javadoc), covering both branches and the `%X` contract.
2. Add `Log4j2CustomConfigSpec` to the log4j2-backend tests:
   - install a non-default configuration programmatically
     (`ConfigurationBuilderFactory` → `Configurator.reconfigure(config)`);
   - assert the formatted message is the literal message, with no
     `[CONTEXT ...]` suffix;
   - assert metadata is still available via `LogEvent.contextData`;
   - assert `cause` still arrives as `LogEvent.thrown`;
   - restore the default configuration after each test
     (`Configurator.reconfigure()`), so other specs keep seeing
     `DefaultConfiguration` (tests are sequential; no parallel forks are
     configured).
3. Verify: `:backends:log4j2-backend:test` first, then the full `build`
   (JDK 17 / Corretto — build fails on JVM 11).

## Status

- [x] `LogEvents.kt` fixed
- [x] `Log4j2CustomConfigSpec` added (5 tests)
- [x] Module tests pass — `:log4j2-backend:test`: 40 tests, 0 failed
      (35 pre-existing + 5 new; sibling specs still pass after
      the config install/restore cycle)
- [x] Full build passes — `./gradlew build`: BUILD SUCCESSFUL,
      236 tasks, all module test suites green (including
      `:jvm-log4j2-backend-std-context:test` integration tests)
- [x] End-to-end smoke test — `2.0.0-SNAPSHOT.424` published to Maven
      Local; a scratch Java app (`WithLogging`, the actual
      `delivery-server` `log4j2.xml` on the classpath →
      `XmlConfiguration`) logs per the pattern, prints the cause's
      stack trace, and throws no `IllegalStateException`.
- [x] Reviewed by `spine-code-review` and `kotlin-engineer` agents;
      addressed: shared `given/TestLoggers.kt` fixture (no duplicated
      helpers), no bare `!!` (suite passed as `KClass`), `@Isolated` on
      the spec, cause-in-context-map behavior pinned by a test.
      Deferred (task chip): binding the configuration check to the
      backend logger's own context instead of the static
      `LoggerContext.getContext(false)` lookup.

## Consumer follow-up (delivery-server)

- Bump `Logging.version` to `2.0.0-SNAPSHOT.424` in
  `buildSrc/src/main/kotlin/io/spine/dependency/local/Logging.kt`
  once this fix is published (or use Maven Local).
- Metadata (tags, key-value context) no longer rides on `%msg` under a
  custom configuration; add `%X` (or `%notEmpty{ [%X]}`) to the
  `PatternLayout` to render it.
