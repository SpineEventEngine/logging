# Plan for Removing `templateContext` and `arguments` from `LogData` and Related Code

This plan describes the steps to eliminate the `templateContext` and `arguments` properties from the `LogData` interface, its implementing classes, and all affected usages and tests, as the formatting is no longer handled by the backend.

---

## 1. API Changes

### 1.1. Remove From LogData

- Remove `val templateContext: TemplateContext?` and `val arguments: Array<Any?>` from the `LogData` interface.
- Remove their KDoc and any related documentation in `LogData`.

---

## 2. Implementation Changes

### 2.1. Update All Implementing Classes

- **Remove properties, constructor parameters, fields, and method overrides** related to `templateContext` and `arguments` from all `LogData` implementations, including:
    - `FakeLogData` (test implementation)
    - Any other custom/mock/test LogData classes

- **Remove or adapt any methods** that interact with these properties, including factory methods and internal logic.

---

## 3. Usage Refactoring

### 3.1. Remove/Refactor Call Sites

- **Search for all usages** of `LogData.templateContext` and `LogData.arguments` throughout the codebase and:
    - Remove code that queries these properties.
    - Refactor formatting logic to use only literal arguments, or eliminate formatting entirely.
- **Update backends** (e.g., `FormattingBackend`) and any place that relies on formatting with these properties.

---

## 4. Test Changes

### 4.1. Update Unit and Integration Tests

- Remove tests and test code that check or utilize `templateContext` and `arguments` in test `LogData` instances.
- Refactor test data and assertions to match the new, simplified API.
- Ensure test factories (`withPrintfStyle`, `withBraceStyle` etc.) do not create or expect formatting data.
- Remove or update documentation in tests referring to formatting support in backends.

---

## 5. Documentation

### 5.1. Update Library Documentation

- Remove documentation and comments in all code and markdown files that reference formatting arguments in backends, `LogData`, or related API.

---

## 6. Validation

### 6.1. Ensure Clean Build and Test State

- Run the build and all tests, ensuring that all configurations relying on the previous properties have been refactored/removed.
- Ensure public API documentation and usage examples are updated and accurate.

---

## 7. (Optional) Deprecate and Announce

- Announce the breaking change (if applicable) in release notes.
- (Optional) Add migration note for downstream users about removal of formatting support in the backend.

---

## Summary Table

| Area                 | Action                                      |
|----------------------|---------------------------------------------|
| API                  | Remove properties from `LogData`            |
| Implementation       | Clean up in all implementations             |
| Usages               | Remove/refactor all code using the props    |
| Tests                | Update/remove dependencies on these props   |
| Documentation        | Remove mentions of backend formatting       |
| Validation           | Ensure build/test & API docs are clean      |
| Communication (opt.) | Announce/document the breaking change       |

---

## Checklist

- [ ] Remove `templateContext` and `arguments` from `LogData`
- [ ] Update all implementations (e.g., `FakeLogData`)
- [ ] Refactor/remove usages (tests, backends, factories, utils)
- [ ] Update all affected tests and examples
- [ ] Remove/update documentation/comments/references
- [ ] Verify build and test completion

---
