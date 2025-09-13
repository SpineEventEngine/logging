/*
 * Copyright 2019, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

package io.spine.logging.util

import com.google.errorprone.annotations.CanIgnoreReturnValue

/**
 * Preconditions for simple often used checks.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/util/Checks.java">
 *   Original Java code</a> for historical context.
 */
public object Checks {

    @CanIgnoreReturnValue
    @JvmStatic
    public fun <T> checkNotNull(value: T?, name: String): T {
        checkNotNull(value) { "$name must not be null" }
        return value
    }

    /**
     * Checks if the given string is a valid metadata identifier.
     */
    @JvmStatic
    @CanIgnoreReturnValue
    public fun checkMetadataIdentifier(s: String): String {
        require(s.isNotEmpty()) { "An identifier must not be empty." }
        require(isLetter(s[0])) { "An identifier must start with an ASCII letter: `$s`." }
        for (n in 1 until s.length) {
            val c = s[n]
            require(isLetter(c) || (c in '0'..'9') || c == '_') {
                "An identifier must contain only ASCII letters, digits or underscore: `$s`."
            }
        }
        return s
    }
}

/**
 * Tells if this character is a letter or not.
 *
 * ## Implementation note
 *
 * The reason we are NOT using method from Character like `isLetter()`,
 * `isJavaLetter()`, `isJavaIdentifierStart()` or the like is that these rely on
 * the Unicode definitions of "LETTER", which are not stable between releases.
 *
 * In theory something marked as a letter in Unicode could be changed
 * to not be a letter in a later release.
 *
 * There is a notion of stable identifiers in Unicode, which is what should be used here,
 * but that needs more investigation.
 */
private fun isLetter(c: Char): Boolean = (c in 'a'..'z') || (c in 'A'..'Z')
