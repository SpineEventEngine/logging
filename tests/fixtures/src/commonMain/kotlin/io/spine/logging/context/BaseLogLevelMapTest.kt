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
import given.map.CustomLoggingLevel.TRACE
import given.map.L1Direct
import given.map.LoggingTestFixture
import given.map.nested.L2Direct
import given.map.nested.sibling.Level3Sibling
import given.map.nested.type.Level3
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.spine.logging.Level.Companion.ALL
import io.spine.logging.Level.Companion.DEBUG
import io.spine.logging.Level.Companion.INFO

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
abstract class BaseLogLevelMapTest: AbstractLogLevelMapTest() {

    /**
     * Configures a log level map builder with the data used by the test of this spec class.
     *
     * We set a default level higher than `INFO` to test the case of not restricting
     * the level of logging by an installed context. Please see the documentation
     * of [ScopedLoggingContext] for the details.
     */
    override fun configureBuilder(builder: LogLevelMap.Builder) {
        with(builder) {
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

            it.logAt(INFO)
            records shouldHaveSize 1 // because logging context should not reduce logging.
            records[0].level shouldBe INFO
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

        should("use a level set on a package", Level3::class) {
            it.logAt(ALL)
            records shouldHaveSize 0

            // `Level3` class belongs to a package under the one specified in the map.
            it.logAt(TRACE)
            records shouldHaveSize 1
            records.last().level shouldBe TRACE

            // `Level3Sibling` class is in another package under the one specified in the map.
            val sibling = Level3Sibling()
            sibling.logAt(TRACE)
            records shouldHaveSize 2
            records.last().level shouldBe TRACE

            // `L2Direct` is the class which is directly in the package mentioned in the map.
            // The class has its own logging level, but should log at the package level too.
            val samePackageClass = L2Direct()
            samePackageClass.logAt(TRACE)
            records shouldHaveSize 2
            records.last().level shouldBe TRACE
        }
    }
}

/**
 * The fixture which is not mentioned in log level map, and package of which
 * is outside the package hierarchy configured in the map.
 */
class DefLoggingFixture: LoggingTestFixture()