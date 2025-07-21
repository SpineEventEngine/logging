# Task 003: Remove Inline Comments from Production Code

## Priority: Medium

## Description
According to the coding guidelines, inline comments should be avoided in production code. They are acceptable in test files, but main source code should be self-documenting with clear naming and structure.

## Guideline Reference
From `documentation-guidelines.md`:
> "Avoid inline comments in production code unless necessary. Inline comments are helpful in tests."

## Issues Found

### Production Code Files with Inline Comments:

1. **LoggingDomainClassValue.kt** (3 occurrences)
   - **File**: `/logging/src/jvmMain/kotlin/io/spine/logging/LoggingDomainClassValue.kt`
   - **Lines**: Multiple inline comments explaining implementation details

2. **LoggingFactory.kt** (1 occurrence)
   - **File**: `/logging/src/jvmMain/kotlin/io/spine/logging/LoggingFactory.kt`
   - **Lines**: Inline comment that could be converted to KDoc or removed

3. **Additional files** in main source directories

## Solution Strategy

### For each inline comment, apply one of these approaches:

1. **Remove if obvious**: If the code is self-explanatory, remove the comment
2. **Convert to KDoc**: If explaining public API behavior, move to method/class KDoc
3. **Refactor code**: If comment explains complex logic, consider refactoring for clarity
4. **Keep only if essential**: Only keep if absolutely necessary for understanding

### Example Transformations:

**Before (inline comment to remove):**
```kotlin
val result = calculateValue() // Calculate the result
```

**After:**
```kotlin
val result = calculateValue()
```

**Before (inline comment to convert to KDoc):**
```kotlin
fun processData(data: String) {
    // This method handles special characters by escaping them
    return data.replace("\\", "\\\\")
}
```

**After:**
```kotlin
/**
 * Processes the input data by escaping special characters.
 * 
 * @param data the input string to process
 * @return the processed string with escaped special characters
 */
fun processData(data: String): String {
    return data.replace("\\", "\\\\")
}
```

## Acceptance Criteria
- [ ] Identify all inline comments in production code (src/main directories)
- [ ] Evaluate each comment for necessity
- [ ] Remove, convert, or refactor as appropriate
- [ ] Ensure code remains clear and understandable
- [ ] Preserve any truly necessary comments with justification
- [ ] Test files (src/test) are exempt from this requirement

## Files to Modify
- `/logging/src/jvmMain/kotlin/io/spine/logging/LoggingDomainClassValue.kt`
- `/logging/src/jvmMain/kotlin/io/spine/logging/LoggingFactory.kt`
- Any other production source files with inline comments

## Testing Required
- [ ] Run full test suite to ensure no functionality is lost
- [ ] Review code readability after comment removal
- [ ] Verify KDoc generation if comments were converted