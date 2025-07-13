# Migration of `io.spine.logging.jvm.parser` package to Kotlin

This directory contains Kotlin versions of the Java files in the `io.spine.logging.jvm.parser` package.
The version has been bumped to 2.0.0-SNAPSHOT.259 to reflect this change.

## Migration Steps

1. Delete the following Java files from `src/main/java/io/spine/logging/jvm/parser/`:
   - BraceStyleMessageParser.java
   - DefaultBraceStyleMessageParser.java
   - DefaultPrintfMessageParser.java
   - MessageBuilder.java
   - MessageParser.java
   - ParseException.java
   - PrintfMessageParser.java
   - package-info.java

2. Create the following Kotlin files in `src/main/kotlin/io/spine/logging/jvm/parser/`:
   - BraceStyleMessageParser.kt
   - DefaultBraceStyleMessageParser.kt
   - DefaultPrintfMessageParser.kt
   - MessageBuilder.kt
   - MessageParser.kt
   - ParseException.kt
   - PrintfMessageParser.kt

3. Copy the content from the files in this directory to the corresponding files in step 2.

## Conversion Notes

- All Java code has been converted to idiomatic Kotlin
- Javadoc has been converted to KDoc
- Nullability has been properly handled
- All functionality has been preserved
