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

package com.google.common.flogger.context

import com.google.common.flogger.MetadataKey
import com.google.common.flogger.MetadataKey.repeated
import com.google.common.flogger.MetadataKey.single
import com.google.common.flogger.context.given.shouldBeEmpty
import com.google.common.flogger.context.given.shouldContainInOrder
import com.google.common.flogger.context.given.shouldHaveFirstValue
import com.google.common.flogger.context.given.shouldHaveSize
import com.google.common.flogger.context.given.shouldNotContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("`ContextMetadata` should")
internal class ContextMetadataSpec {

    companion object {
        private val FOO_KEY: MetadataKey<String> = single("FOO", String::class.java)
        private val BAR_KEY: MetadataKey<String> = repeated("BAR", String::class.java)
        private val UNUSED_KEY: MetadataKey<String> = single("UNUSED", String::class.java)
    }

    @Test
    fun `provide an empty instance`() {
        val metadata = ContextMetadata.none()
        metadata.shouldBeEmpty()
    }

    @Test
    fun `create a single piece of metadata`() {
        val metadata = ContextMetadata.singleton(FOO_KEY, "foo")
        metadata shouldHaveSize 1
        metadata.shouldContainInOrder(FOO_KEY, "foo")
        metadata shouldNotContain UNUSED_KEY
    }

    @Test
    fun `create a new instance with repeated keys`() {
        val metadata = ContextMetadata.builder()
            .add(FOO_KEY, "one")
            .add(BAR_KEY, "two")
            .add(BAR_KEY, "three")
            .add(FOO_KEY, "four")
            .build()

        metadata shouldHaveSize 4
        metadata.shouldContainInOrder(FOO_KEY, "one", "four")
        metadata.shouldContainInOrder(BAR_KEY, "two", "three")
        metadata shouldNotContain UNUSED_KEY

        // The most recent single keyed value.
        metadata.shouldHaveFirstValue(FOO_KEY, "four")

        assertThrows<IllegalArgumentException> {
            // Throws an exception when a repeated key is treated as single.
            // Remember that `BAR_KEY` is repeated.
            metadata.findValue(BAR_KEY)
        }
    }

    @Nested inner class
    `concatenate with` {

        @Test
        fun `another instance`() {
            val first = ContextMetadata.builder()
                .add(FOO_KEY, "one")
                .add(BAR_KEY, "two")
                .build()
            val second = ContextMetadata.builder()
                .add(BAR_KEY, "three")
                .add(FOO_KEY, "four")
                .build()
            val metadata = first.concatenate(second)
            metadata shouldHaveSize 4
            metadata.shouldContainInOrder(FOO_KEY, "one", "four")
            metadata.shouldContainInOrder(BAR_KEY, "two", "three")
        }

        @Test
        fun `an empty instance`() {
            val fooMetadata = ContextMetadata.singleton(FOO_KEY, "foo")
            val emptyMetadata = ContextMetadata.none()
            emptyMetadata.concatenate(fooMetadata) shouldBeSameInstanceAs fooMetadata
            fooMetadata.concatenate(emptyMetadata) shouldBeSameInstanceAs fooMetadata
        }

        @Test
        fun `the same single key`() {
            val metadata = ContextMetadata.singleton(FOO_KEY, "foo")
                .concatenate(ContextMetadata.singleton(FOO_KEY, "bar"))

            // No reordering, no de-duplication.
            metadata.shouldContainInOrder(FOO_KEY, "foo", "bar")
            metadata shouldHaveSize 2
        }
    }
}
