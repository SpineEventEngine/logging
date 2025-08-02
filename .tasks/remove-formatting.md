# Instructions for LLMs: Removing Formatted Logging Output

This file describes the exact sequence of steps LLMs should take to safely and completely remove all formatted or argument-based logging support (such as `printf`-style formatting and `log()` overloads with argument arrays) from the project’s logging subsystem.

---

## Step-by-Step Plan

1. **Remove Tests for Formatted Logging**
    - Identify and delete all tests that verify formatted message output, template interpolation, or argument-based logging.

2. **Delete Formatting-Related `log()` Method Overloads**
    - In the logging API and all related implementations, remove every `log()` (and similar) overload that accepts a format string and/or argument arrays.
    - Retain only simple, unformatted message logging methods.

3. **Remove Formatting Logic from Implementations**
    - Eliminate any and all formatting/interpolation/template-parsing code or branches from the core logging logic.

4. **Delete Formatting-Specific Utilities**
    - Remove any helper classes, utilities, or types that exist solely to support message formatting (for example: template parsers, value queues, etc.).
    - Double-check that their removal does not impact non-formatting (plain) logging flows.

5. **Update All Call Sites**
    - Find all usages of now-deleted formatting-based `log()` methods and convert them to use the plain-message form.

6. **Update Documentation**
    - Revise or remove all Javadoc/KDoc/comments in the API and implementation that reference log message formatting or argument-based logging.

7. **Verify Plain Logging Functionality**
    - Test and confirm that basic logging of plain, unformatted messages still works throughout the system.
    - Run all remaining, relevant tests to ensure functionality and safety were preserved.

---

**Note:** Skip any steps that are not relevant or become unnecessary as code is refactored. Avoid touching unrelated modules or code.

---
