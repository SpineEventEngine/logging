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

package io.spine.logging.dynamic.backend

import com.google.common.flogger.backend.LogData

/**
 * Runs the given [action] using [FakeLoggerBackendFactory].
 *
 * This method substitutes the default backend factory (JUL-based backend)
 * with [FakeLoggerBackendFactory], executes the given [action] and
 * returns the captured log data.
 *
 * Please make sure, the [action] doesn't use a logger from the outside,
 * but creates its own. Otherwise, the substituted backend factory will NOT be
 * used because a logger had already been created, and the corresponding backend too.
 * A logger instantiation should be done within the [action].
 *
 * This method has been made `inline` to preserve the original the log site.
 * *
 * @return all [LogData], captured by the created fake backend.
 */
public inline fun captureLogData(action: () -> Unit): List<LogData> {
    val fakeBackends = FakeLoggerBackendFactory()
    val memoizingFactory = MemoizingBackendFactory(fakeBackends)

    // Runs the given action with a substituted backend factory.
    withBackendFactory(memoizingFactory, action)

    // It is important that all log statements came to the same backend, created within
    // the given action. Otherwise, we can't say if every log data has been captured.
    check(memoizingFactory.createdBackends.size == 1) {
        "Zero or multiple backends were created where only one was expected. " +
                "Created backends: ${memoizingFactory.createdBackends}."
    }

    val usedBackend = memoizingFactory.createdBackends.first()
    return usedBackend.logged
}

/**
 * Sets the given backend [factory], and runs the given [action].
 *
 * After [action] is performed, the default backend factory (JUL-based one)
 * is restored.
 *
 * This method is public due to presence of `inline` modifier.
 * Otherwise, it would have been private.
 */
public inline fun withBackendFactory(factory: TypedBackendFactory<*>, action: () -> Unit) {
    DynamicBackendFactory.delegate(factory)
    action()
    DynamicBackendFactory.reset()
}
