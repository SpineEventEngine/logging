# Task 004: Fix Code Formatting Issues

## Priority: Low

## Description
The codebase has various formatting issues that violate the coding guidelines. These need to be cleaned up to maintain consistency and comply with the project standards.

## Issues Found

### 1. Double Empty Lines (29 occurrences in 27 files)

According to the guidelines: "Remove double empty lines in the code."

**Files with double empty lines:**
- `/logging/src/jvmTest/kotlin/io/spine/logging/JvmWithLoggingSpec.kt`
- `/logging/src/commonTest/kotlin/io/spine/logging/WithLoggingSpec.kt`
- `/jvm/middleware/src/main/kotlin/io/spine/logging/jvm/InjectedJvmLogSite.kt`
- Additional files (29 total occurrences)

### 2. Trailing Whitespace (29 files affected)

According to the guidelines: "Remove trailing space characters in the code."

**Files with trailing whitespace:**
- `/config/buildSrc/src/main/kotlin/io/spine/dependency/local/ProtoData.kt`
- `/jvm/middleware/src/main/kotlin/io/spine/logging/jvm/InjectedJvmLogSite.kt`
- `/logging/src/jvmTest/kotlin/io/spine/logging/given/MyLoggingHelper.kt`
- Additional files (29 total files)

## Solution

### Automated Fix Approach:
1. Use project's `.editorconfig` settings to ensure consistency
2. Apply automated formatting tools where possible
3. Manual review for any edge cases

### Steps:
1. **Remove double empty lines**: Replace multiple consecutive empty lines with single empty lines
2. **Remove trailing whitespace**: Strip all trailing spaces and tabs from line endings
3. **Verify final newlines**: Ensure all files end with exactly one newline (per .editorconfig)

## Implementation Strategy

### Automated Tools:
- Use IDE formatting with `.editorconfig` compliance
- Apply `detekt` formatting rules
- Use `ktlint` or similar tools if available

### Manual Verification:
- Spot-check critical files
- Ensure no semantic changes
- Verify build still passes

## Acceptance Criteria
- [ ] Remove all double empty lines from the codebase
- [ ] Remove all trailing whitespace
- [ ] Ensure all files comply with `.editorconfig` rules
- [ ] Verify no functional changes were made
- [ ] All files pass detekt formatting checks
- [ ] Build and tests continue to pass

## Files to Modify
**Double Empty Lines (27 files):**
- All files identified in the project scan with multiple consecutive empty lines

**Trailing Whitespace (29 files):**
- All files identified with trailing spaces/tabs

## Testing Required
- [ ] Run full build to ensure no compilation errors
- [ ] Run detekt to verify formatting compliance  
- [ ] Run full test suite to ensure no functional regressions
- [ ] Verify `.editorconfig` compliance

## Tools to Use
- IntelliJ IDEA auto-formatting with `.editorconfig`
- `detekt` formatting rules
- Command line tools for batch processing if needed:
  ```bash
  # Remove trailing whitespace
  find . -name "*.kt" -exec sed -i 's/[[:space:]]*$//' {} \;
  
  # Remove double empty lines
  find . -name "*.kt" -exec sed -i '/^$/N;/^\n$/d' {} \;
  ```