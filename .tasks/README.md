# Project Compliance Tasks

This directory contains tasks for fixing project compliance issues found during inspection against the agent guidelines in `.agents/`.

## Task Overview

| Task | Priority | Description | Status |
|------|----------|-------------|---------|
| [001](001-fix-jvm-interop-annotations.md) | Critical | Fix Missing @JvmName Annotations for Java Interoperability | Pending |
| [002](002-fix-todo-comment-format.md) | High | Fix Non-Compliant TODO Comment Format | Pending |
| [003](003-remove-inline-comments-production.md) | Medium | Remove Inline Comments from Production Code | Pending |
| [004](004-fix-formatting-issues.md) | Low | Fix Code Formatting Issues | Pending |
| [005](005-documentation-improvements.md) | Medium | Documentation Flow and Typography Improvements | Pending |

## Priority Legend
- **Critical**: Must be fixed immediately, affects functionality
- **High**: Should be fixed soon, affects compliance/standards
- **Medium**: Important for code quality, fix when convenient  
- **Low**: Nice to have, fix during maintenance

## Compliance Status Summary

### ✅ Compliant Areas
- **Build Configuration**: All files use Kotlin DSL (.gradle.kts)
- **Testing Framework**: Using Kotest assertions and stubs appropriately
- **Safety Rules**: No analytics, telemetry, or unsafe code patterns
- **Code Structure**: Generally following Kotlin idioms and best practices

### ⚠️ Issues Found
- **Java Interoperability**: Missing @JvmName annotations
- **Documentation Standards**: Non-compliant TODO format
- **Code Comments**: Inline comments in production code
- **Formatting**: Trailing whitespace and double empty lines
- **Typography**: Text flow issues in documentation

## Getting Started
1. Start with Critical priority tasks
2. Work through High and Medium priority tasks
3. Address Low priority tasks during maintenance
4. Update task status as work progresses

## Guidelines Reference
All tasks are based on compliance with guidelines in the `.agents/` directory:
- [Coding Guidelines](../.agents/coding-guidelines.md)
- [Documentation Guidelines](../.agents/documentation-guidelines.md)  
- [Documentation Typography](../.agents/documentation-typography-and-structure.md)
- [Safety Rules](../.agents/safety-rules.md)