/*
 * Copyright (C) 2019 The Flogger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.flogger.context

import com.google.common.flogger.context.LogLevelMap.create
import io.kotest.matchers.shouldBe
import java.util.logging.Level

import java.util.logging.Level.FINE
import java.util.logging.Level.FINER
import java.util.logging.Level.INFO
import java.util.logging.Level.WARNING
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`LogLevelMap` should")
internal class LogLevelMapSpec {

    @Test
    fun `return the default level when given no mapping`() {
        val levelMap = create(emptyMap(), WARNING)
        levelMap[""] shouldBe WARNING
        levelMap["com"] shouldBe WARNING
        levelMap["com.example"] shouldBe WARNING
    }

    @Test
    fun `return level in accordance to a single mapping`() {
        val levelMap = create(mapOf("com.example" to FINE), WARNING)
        levelMap[""] shouldBe WARNING
        levelMap["com"] shouldBe WARNING
        levelMap["com.example"] shouldBe FINE
        levelMap["com.example.foo"] shouldBe FINE
    }

    @Test
    fun `return level in accordance to a diverse mapping`() {
        val mapping = mapOf(
            "com.example.foo" to INFO,
            "com.example.foobar" to FINE,
            "com.example.foo.bar" to FINER
        )
        val levelMap = create(mapping, WARNING)
        levelMap[""] shouldBe WARNING
        levelMap["com"] shouldBe WARNING
        levelMap["com.example"] shouldBe WARNING
        levelMap["com.example.foo"] shouldBe INFO
        levelMap["com.example.foo.foo"] shouldBe INFO
        levelMap["com.example.foo.foo.foo.foo"] shouldBe INFO
        levelMap["com.example.foobar"] shouldBe FINE
        levelMap["com.example.foo.bar"] shouldBe FINER
    }

    @Test
    fun `not alter level mapping when the input map changes`() {
        val mutableMapping = hashMapOf("com.example" to INFO)
        val levelMap = create(mutableMapping, WARNING)
        levelMap["com.example.foo"] shouldBe INFO

        // Changing the mutable map has no effect after creating the level map.
        mutableMapping["com.example.foo"] = FINE
        levelMap["com.example.foo"] shouldBe INFO
    }

    @Test
    fun `provide a builder`() {
        val levelMap = LogLevelMap.builder()
            .add(FINE, String::class.java)
            .add(WARNING, String::class.java.getPackage())
            .setDefault(INFO)
            .build()
        levelMap["com.google"] shouldBe INFO
        levelMap["java.lang"] shouldBe WARNING
        levelMap["java.lang.String"] shouldBe FINE
    }
}

// For readability.
private operator fun LogLevelMap.get(logger: String): Level {
    return getLevel(logger)
}
