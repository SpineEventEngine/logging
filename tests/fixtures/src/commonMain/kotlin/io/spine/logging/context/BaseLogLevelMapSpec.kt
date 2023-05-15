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
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.spine.logging.Level.Companion.ALL
import io.spine.logging.Level.Companion.DEBUG
import io.spine.logging.Level.Companion.ERROR
import io.spine.logging.Level.Companion.INFO

/**
 * This class defines tests for [LogLevelMap].
 *
 * This class extends [AbstractLogLevelMapSpec] for defining the log level map against
 * which tests are executed.
 *
 * The class is still abstract so that its descendants can implement [createRecorder] for
 * various logging backends. Another reason for having this interim abstract base is
 * to isolate abstract spec. code defined in [AbstractLogLevelMapSpec] from actual tests
 * defined by this class.
 */
abstract class BaseLogLevelMapSpec: AbstractLogLevelMapSpec() {

    override fun configureBuilder(builder: LogLevelMap.Builder) {
        with(builder) {
            setDefault(ANNOUNCEMENT)
            add(DEBUG, L1Direct::class)
            add(DEBUG, L2Direct::class)
        }
    }

    init {
        should("set default level", DefLoggingFixture::class) {
            it.logAt(ALL)
            records shouldHaveSize 0

            it.logAt(INFO)
            records shouldHaveSize 1
            records[0].level shouldBe INFO
        }

        should("use a level set for a class", L1Direct::class) {
            it.logAt(TRACE)
            records shouldHaveSize 0 // because `TRACE` < `DEBUG`, which is set directly.

            it.logAt(DEBUG)
            records shouldHaveSize 1
            records.last().level shouldBe DEBUG

            it.logAt(INFO)
            records.last().level shouldBe INFO // because `INFO` > `DEBUG`.
        }
    }
}

/**
 * The fixture which is not mentioned in log level map, and package of which
 * is outside the package hierarchy configured in the map.
 */
class DefLoggingFixture: LoggingTestFixture()
