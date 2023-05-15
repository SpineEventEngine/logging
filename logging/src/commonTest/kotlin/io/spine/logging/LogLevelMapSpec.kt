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

package io.spine.logging

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.spine.logging.Level.Companion.DEBUG
import io.spine.logging.Level.Companion.ERROR
import io.spine.logging.Level.Companion.INFO
import io.spine.logging.Level.Companion.OFF
import io.spine.logging.Level.Companion.WARNING
import io.spine.logging.context.LogLevelMap
import io.spine.logging.context.levelOf
import io.spine.logging.context.logLevelMap
import kotlin.reflect.KClass
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`LogLevelMap` should")
internal class LogLevelMapSpec {

    @Test
    fun `create an instance with empty map of levels`() {
        LogLevelMap.create(mapOf(), WARNING).run {
            levelOf("") shouldBe WARNING
            levelOf("com") shouldBe WARNING
            levelOf("com.example") shouldBe WARNING
        }
    }

    @Test
    fun `create an instance with overriding default level for a package`() {
        LogLevelMap.create(mapOf("com.example" to DEBUG), WARNING).run {
            levelOf("") shouldBe WARNING
            levelOf("com") shouldBe WARNING
            levelOf("com.example") shouldBe DEBUG
            levelOf("com.example.foo") shouldBe DEBUG
        }
    }

    @Test
    fun `create an instance with multiple logging levels`() {
        val levels = mapOf(
            "com.example.foo" to WARNING,
            "com.example.foobar" to INFO,
            "com.example.foo.bar" to DEBUG
        )
        LogLevelMap.create(levels, ERROR).run {
            levelOf("") shouldBe ERROR
            levelOf("com") shouldBe ERROR
            levelOf("com.example")shouldBe ERROR
            levelOf("com.example.foo") shouldBe WARNING
            levelOf("com.example.foo.foo") shouldBe WARNING
            levelOf("com.example.foo.foo.foo") shouldBe WARNING
            levelOf("com.example.foobar") shouldBe INFO
            levelOf("com.example.foo.bar") shouldBe DEBUG
        }
    }

    @Test
    fun `copy values from the given map`() {
        val p = "com.example"
        val mutableMap = mutableMapOf(p to INFO)
        LogLevelMap.create(mutableMap, WARNING).run {
            val nestedPackage = "$p.foo"
            levelOf(nestedPackage) shouldBe INFO

            mutableMap[nestedPackage] = DEBUG

            levelOf(nestedPackage) shouldBe INFO
        }
    }

    @Test
    fun `provide builder API`() {
        val map = logLevelMap {
            add(DEBUG, String::class)
            add(WARNING, String::class.java.packageName)
            setDefault(INFO)
        }
        map.run {
            levelOf(String::class.qualifiedName!!) shouldBe DEBUG
            levelOf("java.lang") shouldBe WARNING
            levelOf("io.spine") shouldBe INFO
        }
    }

    @Test
    fun `merge with another map`() {
        val map1 = logLevelMap {
            add(DEBUG, String::class)
            add(WARNING, Int::class)
        }
        val map2 = logLevelMap {
            add(DEBUG, "kotlin.collections")
        }
        val merged = map1.merge(map2)

        merged shouldNotBeSameInstanceAs map1
        merged shouldNotBeSameInstanceAs map2

        merged.run {
            levelOf(String::class) shouldBe DEBUG
            levelOf(Int::class) shouldBe WARNING
            levelOf(List::class) shouldBe DEBUG // belongs to the package from `map2`.
            levelOf(KClass::class) shouldBe OFF // not in any map.
            levelOf("") shouldBe OFF // no custom default in both maps.
        }
    }
}
