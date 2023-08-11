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

import com.google.common.collect.ImmutableMap
import com.google.common.flogger.context.LogLevelMap.create
import com.google.common.truth.Truth.assertThat

import java.util.logging.Level
import org.junit.jupiter.api.Test

internal class LogLevelMapSpec {

    // We have a different implementation for empty maps (ie, just changing the global log level).
    @Test
    fun testGetLevel_empty() {
        val levelMap = create(ImmutableMap.of(), Level.WARNING)

        assertThat(levelMap.getLevel("")).isEqualTo(Level.WARNING)
        assertThat(levelMap.getLevel("com")).isEqualTo(Level.WARNING)
        assertThat(levelMap.getLevel("com.example")).isEqualTo(Level.WARNING)
    }

    // We have a different implementation for singleton maps.
    @Test
    fun testGetLevel_single() {
        val levelMap = create(ImmutableMap.of("com.example", Level.FINE), Level.WARNING)

        assertThat(levelMap.getLevel("")).isEqualTo(Level.WARNING)
        assertThat(levelMap.getLevel("com")).isEqualTo(Level.WARNING)
        assertThat(levelMap.getLevel("com.example")).isEqualTo(Level.FINE)
        assertThat(levelMap.getLevel("com.example.foo")).isEqualTo(Level.FINE)
    }

    // General implementation.
    @Test
    fun testGetLevel_general() {
        val map = ImmutableMap.of(
            "com.example.foo", Level.INFO,
            "com.example.foobar", Level.FINE,
            "com.example.foo.bar", Level.FINER
        )
        val levelMap = create(map, Level.WARNING)

        assertThat(levelMap.getLevel("")).isEqualTo(Level.WARNING)
        assertThat(levelMap.getLevel("com")).isEqualTo(Level.WARNING)
        assertThat(levelMap.getLevel("com.example")).isEqualTo(Level.WARNING)
        assertThat(levelMap.getLevel("com.example.foo")).isEqualTo(Level.INFO)
        assertThat(levelMap.getLevel("com.example.foo.foo")).isEqualTo(Level.INFO)
        assertThat(levelMap.getLevel("com.example.foo.foo.foo.foo")).isEqualTo(Level.INFO)
        assertThat(levelMap.getLevel("com.example.foobar")).isEqualTo(Level.FINE)
        assertThat(levelMap.getLevel("com.example.foo.bar")).isEqualTo(Level.FINER)
    }

    @Test
    fun testLevelImmutable() {
        val mutableMap = hashMapOf<String, Level>()
        mutableMap["com.example"] = Level.INFO
        val levelMap = create(mutableMap, Level.WARNING)
        assertThat(levelMap.getLevel("com.example.foo")).isEqualTo(Level.INFO)

        // Changing the mutable map has no effect after creating the level map.
        mutableMap["com.example.foo"] = Level.FINE
        assertThat(levelMap.getLevel("com.example.foo")).isEqualTo(Level.INFO)
    }

    @Test
    fun testBuilder() {
        val levelMap = LogLevelMap.builder()
            .add(Level.FINE, String::class.java)
            .add(Level.WARNING, String::class.java.getPackage())
            .setDefault(Level.INFO)
            .build()
        assertThat(levelMap.getLevel("com.google")).isEqualTo(Level.INFO)
        assertThat(levelMap.getLevel("java.lang")).isEqualTo(Level.WARNING)
        assertThat(levelMap.getLevel("java.lang.String")).isEqualTo(Level.FINE)
    }
}
