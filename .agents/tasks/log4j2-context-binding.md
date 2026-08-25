# Bind the Log4j2 configuration check to the backend logger's context

## Problem

`toLog4jLogEvent(loggerName, logData)` in
`backends/log4j2-backend/.../LogEvents.kt` selects the message-formatting
strategy by checking `LoggerContext.getContext(false).configuration`.
That static lookup answers "which context belongs to the *calling code*"
(a stack walk in `ClassLoaderContextSelector` keyed by the caller's
classloader), while the event is actually rendered by the context of the
`core.Logger` held by `Log4j2LoggerBackend` (`Logger.context` is a final
field, kept current on reconfiguration via `Logger.updateConfiguration`).

Why change it:

1. **Correctness by construction.** The branch asks "can the layout show
   context data?" — and "the layout" belongs to the configuration of the
   logger that renders the event. In single-context JVMs the static
   lookup returns the same configuration only *by coincidence*; with
   non-default context selectors (per-webapp, OSGi, JNDI) the two can
   diverge, silently taking the wrong branch (metadata duplicated or
   lost).
2. **Hot-path cost.** The lookup runs per log event and performs a stack
   walk (`StackLocatorUtil.getCallerClass`). The replacement is a field
   read on an object the backend already holds.
3. **Testability.** With the check bound to the logger's own context,
   specs can use a private `LoggerContext` instead of mutating the
   process-global configuration (`Configurator.reconfigure` +
   `@AfterEach` restore + `@Isolated`).

## Agreed constraints (from review discussion)

- Divergence from upstream Flogger (which uses the static lookup) is
  acceptable: KDocs reference the original code, and the Kotlin
  conversion already made the delta large. Still *document* the
  divergence in the code comment.
- `toLog4jLogEvent(loggerName, logData)` has **no external usages**;
  the signature may change freely, no deprecation cycle needed.

## Recommended shape (A) — for review

Make the conversion functions extensions on `LogData`, taking the
rendering logger, and drop `public` in favor of `internal` (the module's
public contract is the `BackendFactory` service, not these helpers):

```kotlin
internal fun LogData.toLog4jEvent(logger: Logger): LogEvent
internal fun LogData.toLog4jEvent(logger: Logger, error: RuntimeException): LogEvent
```

- The decision reads `logger.context.configuration` — per event, never
  cached, so reconfiguration keeps working exactly as before.
- `loggerName` parameter disappears; the name comes from `logger.name`,
  removing the current half-injection (name from the logger,
  configuration from ambient state).
- Call sites in `Log4j2LoggerBackend`:
  `logger.get().log(data.toLog4jEvent(logger))`, and the analogous form
  in `handleError`.
- Matches the existing conversion-extension precedent in the same file
  (`Level.toLog4j()`).

Alternatives, if (A) is unwanted:

- (B) Minimal: keep top-level functions, add a `config: Configuration`
  parameter; backend passes `logger.context.configuration`.
- (C) Keep top-level functions, replace `loggerName: String` with
  `logger: Logger`.

## Plan

1. Reshape the two conversion functions per the chosen option; replace
   the `LoggerContext.getContext(false)` lookup with the logger's own
   configuration.
2. Update both call sites in `Log4j2LoggerBackend`.
3. Update KDoc and the in-function comment: the check now uses the
   configuration of the rendering logger; note the deliberate divergence
   from upstream Flogger and keep the caveat that `DefaultConfiguration`
   detection relies on Log4j2 internals.
4. Rework `Log4j2CustomConfigSpec`: build a private started
   `LoggerContext` with a programmatic configuration and create the test
   logger from it; delete the `Configurator.reconfigure` install/restore
   choreography and `@Isolated`; stop the context after each test.
5. Add a multi-context test (previously impossible): one logger from the
   default context and one from a private custom context, logging
   through their backends in the same JVM — assert each takes its own
   branch (`[CONTEXT ...]` suffix vs. plain message).
6. Verify: `:log4j2-backend:test`, then full `./gradlew build` (JDK 17).
7. Re-run `publishToMavenLocal` so the local `2.0.0-SNAPSHOT.424`
   artifacts include this change (no version re-bump: the branch is
   already one step ahead of master, per the bump-once-per-branch
   policy; nothing was published beyond Maven Local).

## Review resolution

Shape (A) approved by the user (including `internal` visibility);
the multi-context test included.

Agent reviews of the implementation:

- `kotlin-engineer` — approve with changes; both applied: named
  arguments at the two five-parameter `toLog4jEvent(...)` call sites
  (its first and third parameters are both `String`), and the spec
  KDoc corrected to "the global logger context is never reconfigured"
  (the multi-context test legitimately *reads* the default context).
- `spine-code-review` — approve, no findings; independently confirmed
  no leftover `toLog4jLogEvent` usages, no unused imports, `@Isolated`
  removal safety, and the bump-once-per-branch version gate.

## Status

- [x] Conversion functions reshaped; static lookup removed
- [x] `Log4j2LoggerBackend` call sites updated
- [x] KDoc / comments updated (divergence documented)
- [x] `Log4j2CustomConfigSpec` reworked to a private `LoggerContext`
      (no global-state mutation; `@Isolated` and the restore step gone)
- [x] Multi-context test added — `decide the formatting per the context
      of the logger`: custom-context backend emits the plain message
      while a default-context backend appends `[CONTEXT ...]` in the
      same JVM
- [x] Module tests and full build pass — `:log4j2-backend:test`:
      41 tests, 0 failed; `./gradlew build publishToMavenLocal`:
      BUILD SUCCESSFUL, 361 tasks
- [x] Maven Local `.424` refreshed
