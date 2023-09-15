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
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`ValueQueue` should")
internal class ValueQueueSpec {

    @Nested
    inner class
    `create a new instance` {

        @Test
        fun `with a value`() {
            val list = listOf(1, 2, 3)
            val queue = appendValueToNewQueue(list)
            "$queue" shouldBe "$list"
        }

        @Test
        fun `with a value and 'null'`() {
            val value = 1
            val existingValue = null
            val queue = maybeWrap(value, existingValue)
            "$queue" shouldBe "$value"
        }

        @Test
        fun `with a value and another queue`() {
            val values = listOf(1, 2)
            val existingQueue = maybeWrap(values[0], null)
            val queue = maybeWrap(values[1], existingQueue) as ValueQueue
            "$queue" shouldBe "$values"
        }

        @Test
        fun `with a value and a nested queue`() {
            val values = listOf(1, 2, 3)
            val existingQueue1 = maybeWrap(1, null)
            val existingQueue2 = maybeWrap(2, existingQueue1) as ValueQueue
            val queue = maybeWrap(3, existingQueue2) as ValueQueue
            "$queue" shouldBe "$values"
        }

        @Test
        fun `with an empty string`() {
            val value = ""
            val queue = appendValueToNewQueue(value)
            "$queue" shouldBe value
        }

        @Test
        fun `with nested lists`() {
            val innerList = listOf(1, 2)
            val outerList = listOf(innerList, 3)
            val queue = appendValueToNewQueue(outerList)
            "$queue" shouldBe "$outerList"
        }
    }

    @Test
    fun `throw when given a nullable value`() {
        val value = null
        val existingValue = null
        shouldThrow<NullPointerException> {
            maybeWrap(value, existingValue)
        }
    }
}
