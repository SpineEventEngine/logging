/*
 * Copyright 2023, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package com.google.common.flogger.backend.log4j2

import com.google.common.flogger.backend.log4j2.ValueQueue.appendValueToNewQueue
import com.google.common.flogger.backend.log4j2.ValueQueue.maybeWrap
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for [ValueQueue].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/log4j2/src/test/java/com/google/common/flogger/backend/log4j2/ValueQueueTest.java">
 *     Original Java code of Google Flogger</a>
 */
@DisplayName("`ValueQueue` should")
internal class ValueQueueSpec {

    @Nested
    inner class
    `create a new instance` {

        @Test
        fun `with a value`() {
            val list = listOf(1, 2, 3)
            val queue = appendValueToNewQueue(list)
            queue shouldHaveSingleElement list
        }

        @Test
        fun `with another and existing values`() {
            val existingValue = 1
            val anotherValue = 2
            val queue = maybeWrap(anotherValue, existingValue)
            queue.shouldBeInstanceOf<ValueQueue>()
            queue.shouldContainInOrder(existingValue, anotherValue)
        }

        @Test
        fun `with a value and another queue`() {
            val existingValue = 1
            val anotherValue = 2
            val existingQueue = appendValueToNewQueue(existingValue)
            val queue = maybeWrap(anotherValue, existingQueue)
            queue.shouldBeInstanceOf<ValueQueue>()
            queue.shouldContainInOrder(existingValue, anotherValue)
        }

        @Test
        fun `with a value and a nested queue`() {
            val (rootValue, innerValue, outerValue) = listOf(1, 2, 3)
            val rootQueue = appendValueToNewQueue(rootValue)
            val innerQueue = maybeWrap(innerValue, rootQueue)
            val outerQueue = maybeWrap(outerValue, innerQueue)
            outerQueue.shouldBeInstanceOf<ValueQueue>()
            outerQueue.shouldContainInOrder(rootValue, innerValue, outerValue)
        }
    }

    @Test
    fun `not create a new instance when existing value is 'null'`() {
        val value = 1
        val existingValue = null
        val queue = maybeWrap(value, existingValue)
        queue shouldBeSameInstanceAs value
    }

    @Test
    fun `throw when given a 'null' value`() {
        val value = null
        val existingValue = null
        shouldThrow<NullPointerException> {
            maybeWrap(value, existingValue)
        }
    }
}
