## Spine Probe Backend for JVM

**Note:** This is a specific backend implementation that is designed to be used in tests.

The probe backend provides a backend factory that can switch
the current backend implementation on runtime.
The logging facade does not provide such a capability.
Take a look at `DynamicBackendFactory` for details.

This feature is quite helpful in tests. For example, to test a sole backend
instance or intercept the logged statements.

### Logging interception

For log statements interception, this module exposes `captureLogData { ... }`. 
This function uses `DynamicBackendFactory` to catch all `LogData` emitted 
during the execution of the passed `action`.

Usage example:

```kotlin
import io.spine.logging.backend.probe.captureLogData

val message = "logged text"
val logged = captureLogData {
    val logger = LoggingFactory.forEnclosingClass()
    logger.atInfo().log { message }
}

check(logged[0].literalArgument == message)
```

### Gradle configuration

Please note, this module is **not published**. It can be used only within 
`spine-logging` itself as a project dependency **for JVM**.

Unlike other backends that are put to the runtime classpath, this one should be
available during compilation. It is because `DynamicBackendFactory` (which is 
a Kotlin object) and/or `captureLogData { ... }` are meant to be used in code.

An example usage of `logging-probe-backend` in a JVM module:

```kotlin
dependencies {
    implementation(project(":logging"))
    testImplementation(project(":logging-probe-backend"))
}
```

In KMP modules there's no `testImplementation` configuration anymore.
Dependencies are split on the level of source sets.

An example usage of `logging-probe-backend` in a KMP module:

```kotlin
sourceSets {
    val commonMain by getting {
        dependencies {
            implementation(project(":logging"))
        }
    }
    val jvmTest by getting {
        dependencies {
            implementation(project(":logging-probe-backend"))
        }
    }
}
```
