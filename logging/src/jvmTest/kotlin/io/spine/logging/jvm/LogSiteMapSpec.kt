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

package io.spine.logging.jvm

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.spine.logging.LogSiteKey
import io.spine.logging.LoggingScope
import io.spine.logging.backend.Metadata
import io.spine.logging.backend.given.FakeMetadata
import io.spine.logging.jvm.given.FakeLogSite
import java.lang.Thread.sleep
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for [LogSiteMap].
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/test/java/com/google/common/flogger/LogSiteMapTest.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
@DisplayName("`LogSiteMap` should")
internal class LogSiteMapSpec {

    @Test
    fun `get value for the given key`() {
        val map = createMap()
        val logSite1 = FakeLogSite("class1", "method1", 1, "path1")
        val logSite2 = FakeLogSite("class2", "method2", 2, "path2")
        val stats1 = map[logSite1, Metadata.empty()]
        val stats2 = map[logSite2, Metadata.empty()]
        stats1.shouldNotBeNull()
        stats2.shouldNotBeNull()
        stats1 shouldNotBeSameInstanceAs stats2
        map[logSite1, Metadata.empty()] shouldBeSameInstanceAs stats1
        map[logSite2, Metadata.empty()] shouldBeSameInstanceAs stats2
    }

    @Test
    fun `remove entries when a scope is manually closed`() {
        val map = createMap()
        val foo = LoggingScope.WeakScope("foo")
        val fooMetadata = FakeMetadata().add(LogContext.Key.LOG_SITE_GROUPING_KEY, foo)
        val bar = LoggingScope.WeakScope("bar")
        val barMetadata = FakeMetadata().add(LogContext.Key.LOG_SITE_GROUPING_KEY, bar)
        val logSite = FakeLogSite("com.google.foo.Foo", "doFoo", 42, "<unused>")
        val fooKey = LogContext.specializeLogSiteKeyFromMetadata(logSite, fooMetadata)
        val barKey = LogContext.specializeLogSiteKeyFromMetadata(logSite, barMetadata)

        // First increment.
        map[fooKey, fooMetadata].incrementAndGet() shouldBe 1
        // Same metadata, non-specialized key (scope is also not in the metadata).
        map[logSite, Metadata.empty()].incrementAndGet() shouldBe 1
        // Same metadata, specialized key (2nd time).
        map[fooKey, fooMetadata].incrementAndGet() shouldBe 2
        // Different metadata, new specialized key.
        map[barKey, barMetadata].incrementAndGet() shouldBe 1

        map.contains(logSite).shouldBeTrue()
        map.contains(fooKey).shouldBeTrue()
        map.contains(barKey).shouldBeTrue()

        foo.closeForTesting()
        map.contains(logSite).shouldBeTrue()
        map.contains(fooKey).shouldBeFalse()
        map.contains(barKey).shouldBeTrue()
    }

    @Test
    @Suppress("ExplicitGarbageCollectionCall") // Needed in the test.
    fun `remove entries when a scope is garbage collected`() {
        val map = createMap()

        // The scope of the returned key should be garbage collected
        // once the recursion is over.
        val fooKey = recurseAndCall(10) {
            useAndReturnScopedKey(map, "foo")
        }

        // GC should collect the `Scope` reference used in the recursive call.
        System.gc()
        sleep(1000)
        System.gc()

        // Adding new keys in a different scope triggers tidying up of keys
        // from unreachable scopes.
        val barKey = useAndReturnScopedKey(map, "bar")
        map.contains(barKey).shouldBeTrue()

        // This is what's being tested! The scope becoming unreachable
        // causes old keys to be removed.
        map.contains(fooKey).shouldBeFalse()
    }
}

private fun createMap(): LogSiteMap<AtomicInteger> = object : LogSiteMap<AtomicInteger>() {
    override fun initialValue(): AtomicInteger = AtomicInteger(0)
}

private fun <T> recurseAndCall(n: Int, action: Callable<T>): T {
    val i = n - 1
    return if (i <= 0) action.call() else recurseAndCall(i, action)
}

private fun useAndReturnScopedKey(map: LogSiteMap<AtomicInteger>, label: String): LogSiteKey {
    val scope = LoggingScope.create(label)
    val metadata = FakeMetadata().add(LogContext.Key.LOG_SITE_GROUPING_KEY, scope)
    val logSite = FakeLogSite("com.example", label, 42, "<unused>")
    val key = LogContext.specializeLogSiteKeyFromMetadata(logSite, metadata)
    map[key, metadata].incrementAndGet()
    map.contains(key).shouldBeTrue()
    return key
}
