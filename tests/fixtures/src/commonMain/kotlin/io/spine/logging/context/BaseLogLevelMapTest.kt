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

package io.spine.logging.context

import given.map.CustomLoggingLevel.ANNOUNCEMENT
import given.map.L1Direct
import given.map.LoggingTestFixture
import given.map.nested.L2Direct
import given.map.nested.sibling.Level3Sibling
import given.map.nested.type.Level3
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.spine.logging.Level.Companion.DEBUG
import io.spine.logging.Level.Companion.ERROR
import io.spine.logging.Level.Companion.INFO
import io.spine.logging.Level.Companion.TRACE

/**
 * This class defines tests for [LogLevelMap].
 *
 * This class extends [AbstractLogLevelMapTest] for defining the log level map against
 * which tests are executed.
 *
 * The class is still abstract so that its descendants can implement [createRecorder] for
 * various logging backends. Another reason for having this interim abstract base is
 * to isolate abstract spec. code defined in [AbstractLogLevelMapTest] from actual tests
 * defined by this class.
 *
 * The execution of tests is arranged by having non-abstract classes extending this one.
 * These classes are created in projects that define implementation-specific details for
 * the testing environments.
 *
 * @see AbstractLogLevelMapTest
 */
public abstract class BaseLogLevelMapTest: AbstractLogLevelMapTest() {

    /**
     * Configures a log level map builder with the data used by the test of this spec class.
     *
     * We set a default level higher than `INFO` to test the case of not restricting
     * the level of logging by an installed context. Please see the documentation
     * of [ScopedLoggingContext] for the details.
     */
    override fun configureBuilder(builder: LogLevelMap.Builder) {
        with(builder) {
            // Custom logging level, which is above `INFO`.
            setDefault(ANNOUNCEMENT)
            add(DEBUG, L1Direct::class)
            add(DEBUG, L2Direct::class)
            add(TRACE, "given.map.nested")
        }
    }

    init {
        should("set default level", DefLoggingFixture::class) {
            it.logAt(DEBUG)
            records shouldHaveSize 0

            it.logAt(ERROR)
            records shouldHaveSize 1 // because logging context should not reduce logging.
            records[0].level shouldBe ERROR
        }

        should("use a level set for a class", L1Direct::class) {
            it.logAt(TRACE)
            records shouldHaveSize 0 /* because `TRACE` < `DEBUG`, which is set directly for
                this class */

            it.logAt(DEBUG)
            records shouldHaveSize 1 /* as we specifically set in the map. */
            records.last().level shouldBe DEBUG

            it.logAt(INFO)
            records.last().level shouldBe INFO /* because `INFO` > `DEBUG`. */
        }

        val loggingClasses = listOf(Level3::class, Level3Sibling::class, L2Direct::class)
        shouldMany("use a level set on a package", loggingClasses) { fixtures, recorders ->
            val records0 = recorders[0].records
            val records1 = recorders[1].records

            // `Level3` class belongs to a package under the one specified in the map.
            fixtures[0].logAt(TRACE, "From fixture 0.")
            records0 shouldHaveSize 1
            records0.last().run {
                level shouldBe TRACE
                message shouldContain "From fixture 0."
            }

            // `Level3Sibling` class is in another package under the one specified in the map.
            val sibling = fixtures[1]
            sibling.logAt(TRACE, "From fixture 1.")
            records1.last().run {
                level shouldBe TRACE
                message shouldContain "From fixture 1."
            }

            // `L2Direct` is the class which is directly in the package mentioned in the map.
            // The class has its own logging level. Logging at lower level should not occur.
            val records2 = recorders[2].records
            val samePackageClass = fixtures[2]
            samePackageClass.logAt(TRACE, "Fixture 2 at TRACE.")
            // When under JUL we may get records at the parent handler, so our recorder
            // may already be not empty.
            if (records2.isNotEmpty()) {
                records2.last().run {
                    message shouldNotContain "Fixture 2 at TRACE."
                }
            }
        }
    }
}

/**
 * The fixture which is not mentioned in log level map, and package of which
 * is outside the package hierarchy configured in the map.
 *
 * The class must be public so that Kotlin Reflection can call its constructor.
 */
public class DefLoggingFixture: LoggingTestFixture()
