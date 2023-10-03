## Spine Fake Backend

**Note:** This is a specific backend implementation that is designed to be used in tests.

Fake backend provides a backend factory that can switch the currently used backend 
implementation in runtime. The logging facade doesn't provide such functionality. 
Take a look on `DynamicBackendFactory` for details.

This feature is quite helpful in tests. For example, to test a sole backend instance
or intercept the logged statements.

### Logging interception

For log statements interception, this module exposes `captureLogData { ... }` function. 
This function uses `DynamicBackendFactory` to catch all `LogData` emitted during the execution
of the passed `action`.

Usage example:

```kotlin
val message = "logged text"
val logged = captureLogData {
    val logger = LoggingFactory.forEnclosingClass()
    logger.atInfo().log { message }
}
check(logged[0].literalArgument == message)
```

To use it, add this backend to `implementation` configuration instead of `runtimeOnly`.
