# Task 001: Fix Missing @JvmName Annotations for Java Interoperability

## Priority: Critical

## Description
The project has missing `@JvmName` annotations that are required for proper Java interoperability according to the coding guidelines.

## Issues Found

### WithLogging.kt
- **File**: `/logging/src/jvmMain/kotlin/io/spine/logging/WithLogging.kt`
- **Line**: 58
- **Issue**: Function `public fun logger(): Logger<*> = logger` needs `@JvmName("logger")` annotation
- **Impact**: Java code cannot properly access this function due to naming conflicts

## Solution
Add the missing `@JvmName` annotation to ensure proper Java interoperability:

```kotlin
@JvmName("logger")
public fun logger(): Logger<*> = logger
```

## Acceptance Criteria
- [ ] Add `@JvmName("logger")` annotation to the identified function
- [ ] Verify Java compilation still works
- [ ] Run tests to ensure no breaking changes
- [ ] Check for any other missing `@JvmName` annotations in similar patterns

## Files to Modify
- `/logging/src/jvmMain/kotlin/io/spine/logging/WithLogging.kt`

## Testing Required
- [ ] Run Java compilation tests
- [ ] Run full test suite
- [ ] Verify Java interop functionality