# 🪄 Converting Java code to Kotlin

* Java code API comments are Javadoc format.
* Kotlin code API comments are in KDoc format.

## Javadoc to KDoc conversion

* The wording of original Javadoc comments must be preserved.

## Treating nullability

* Use nullable Kotlin type only if the type in Java is annotated as `@Nullable`.

## Efficient Conversion Workflow

* First, analyze the entire Java file structure before beginning conversion to understand dependencies and class relationships.
* Convert Java code to Kotlin systematically: imports first, followed by class definitions, methods, and finally expressions.
* Preserve all existing functionality and behavior during conversion.
* Maintain original code structure and organization to ensure readability.

## Common Java to Kotlin Patterns

* Convert Java getters/setters to Kotlin properties with appropriate visibility modifiers.
* Transform Java static methods to companion object functions or top-level functions as appropriate.
* Replace Java anonymous classes with Kotlin lambda expressions when possible.
* Convert Java interfaces with default methods to Kotlin interfaces with implementations.
* Transform Java builders to Kotlin DSL patterns when appropriate.

## Error Prevention

* Pay special attention to Java's checked exceptions versus Kotlin's unchecked exceptions.
* Be cautious with Java wildcards (`? extends`, `? super`) conversion to Kotlin's `out` and `in` type parameters.
* Ensure proper handling of Java static initialization blocks in Kotlin companion objects.
* Verify that Java overloaded methods convert correctly with appropriate default parameter values in Kotlin.
* Remember that Kotlin has smart casts which can eliminate explicit type casting needed in Java.
* Suppress `detekt` build errors using this format: `[Error]` with `@Suppress("Error")`.

## Documentation Conversion
* Do not convert documentation links in HTML hyperlink (`<a href="...">...</a>`) format.
* Keep the original Javadoc comments but convert them to KDoc format.
* When converting type or method Javadoc, make the first paragraph to be exactly one sentence,
  moving the rest of the text to the new paragraph.
  Do not apply this rule to paragraphs other than the first one.
  Do not apply this rule to the `@param`, `@return`, and `@throws` tags.
* Convert `@param` to `@param` with the same description:
  - Start the description on the same line with the `@param` tag
  - Break at 90 chars
  - Continue with the margin of 7 space chars.

* Convert `@return` to `@return` with the same description.
* Convert `@throws` to `@throws` with the same description.
* Convert `{@link}` to `[name][fully.qualified.Name]` format.
* Convert `{@code}` to inline code with backticks (`).

## Conversion tasks
 * Convert end-line comments above methods and fields to KDoc.
 * Preserve the content of original comments but convert the format and markup.
 * Always use braces `{}` for `if`, `for`, and `while` statements.
 * If the converted function has more than two `return` statements, annotate it with `@Suppress("ReturnCount")`.
 * Keep inline comments from the original code.
 * Ensure a single new line at the end of the produced Kotlin file without asking.


