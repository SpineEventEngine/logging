/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.logging.jvm;

import org.jspecify.annotations.Nullable;

import static io.spine.logging.jvm.util.Checks.checkNotNull;

/**
 * A log site implementation used for injected logging information
 * during compile-time or via agents.
 *
 * <p>This class maintains information about the location of a log statement,
 * including the class name, method name, line number, and source file.
 *
 * <p>The class name is stored in the internal JVM format (slash-separated) and
 * converted to the standard dot-separated format when needed.
 */
final class InjectedJvmLogSite extends JvmLogSite {

    /** Internal (slash-separated) fully qualified class name (e.g., "com/example/Foo$Bar"). */
    private final String internalClassName;

    /** Bare method name (no signature information). */
    private final String methodName;
    private final int encodedLineNumber;
    @Nullable
    private final String sourceFileName;
    private int hashcode = 0;

    InjectedJvmLogSite(
            String internalClassName,
            String methodName,
            int encodedLineNumber,
            @Nullable String sourceFileName) {
        this.internalClassName = checkNotNull(internalClassName, "class name");
        this.methodName = checkNotNull(methodName, "method name");
        this.encodedLineNumber = encodedLineNumber;
        this.sourceFileName = sourceFileName;
    }

    /**
     * Obtains dot-separated class name.
     *
     * @implNote We have to do the conversion from internal to public class name somewhere,
     * and doing it earlier could cost work in cases where the log statement is dropped.
     *
     * <p>We could cache the result somewhere, but in the default Fluent Logger backend,
     * this method is actually onl called once anyway when constructing the LogRecord instance.
     */
    @Override
    public String getClassName() {
        return internalClassName.replace('/', '.');
    }

    @Override
    public String getMethodName() {
        return methodName;
    }


    /** 
     * Strips additional "uniqueness" information from the upper 16 bits. 
     */
    @Override
    public int getLineNumber() {
        return encodedLineNumber & 0xFFFF;
    }

    @Override
    @Nullable
    public String getFileName() {
        return sourceFileName;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof InjectedJvmLogSite other) {
            // Probably not worth optimizing for "this == obj" because
            // all strings should be interned.
            return methodName.equals(other.methodName)
                    && encodedLineNumber == other.encodedLineNumber
                    // Check classname last because it isn't cached
                    && getClassName().equals(other.getClassName());
        }
        return false;
    }

    @Override
    public int hashCode() {
        if (hashcode == 0) {
            // TODO(dbeaumont): Revisit the algorithm when looking at b/22753674.
            // If the log statement uses metadata, the log site will be used as a key to look up the
            // current value. In most cases the hashcode is never needed, but in others it may be used
            // multiple times in different data structures.
            var temp = 157;
            
            // Don't include classname since it isn't cached. Other fields should be unique enough.
            temp = 31 * temp + methodName.hashCode();
            temp = 31 * temp + encodedLineNumber;
            hashcode = temp;
        }
        return hashcode;
    }
}
