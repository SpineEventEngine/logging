/*
 * Copyright 2023, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

package io.spine.logging.backend

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.logging.backend.given.BadToString
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for extensions of `Any?` declared in the `AnyExts.kt` file.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/test/java/com/google/common/flogger/backend/MessageUtilsTest.java">
 *   Original Java code</a> for historical context.
 */
@DisplayName("`Any?` extensions should")
internal class AnyMessagesSpec {

    @Nested
    inner class
    `safely convert to string` {

        @Test
        fun `literals and arrays`() {
            "Hello World".safeToString() shouldBeSameInstanceAs "Hello World"
            10.safeToString() shouldBe "10"
            false.safeToString() shouldBe "false"

            // Not what you would normally get from `Any.toString()` ...
            arrayOf("Foo", "Bar").safeToString() shouldBe "[Foo, Bar]"
            arrayOf(1, 2, 3).safeToString() shouldBe "[1, 2, 3]"
            null.safeToString() shouldBe "null"
        }

        @Test
        fun `objects that return 'null' on 'toString()'`() {
            val badToString = BadToString()
            badToString.safeToString() shouldContain badToString::class.simpleName!!
            badToString.safeToString() shouldContain "toString() returned null"
        }

        @Test
        fun `objects that throw on 'toString()'`() {
            val any = object {
                override fun toString(): String = throw IllegalArgumentException("Badness")
            }
            any.safeToString() shouldContain "java.lang.IllegalArgumentException: Badness"
        }
    }
}
