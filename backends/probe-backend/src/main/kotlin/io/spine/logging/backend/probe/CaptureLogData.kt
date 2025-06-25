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

package io.spine.logging.backend.probe

import io.spine.logging.jvm.backend.LogData

/**
 * Runs the given [action], capturing all log data that are passed
 * to backend by the API.
 *
 * To make this work, please create a logger instance inside of [action].
 * This method captures log data for loggers that are both created and used
 * within the [action].
 *
 * For example:
 *
 * ```
 * val message = "logged text"
 * val logged = captureLogData {
 *     val logger = LoggingFactory.forEnclosingClass()
 *     logger.atInfo().log { message }
 * }
 * check(logged[0].literalArgument == message)
 * ```
 *
 * ### Implementation details
 *
 * `logging-probe-backend` configures [DynamicBackendFactory] for the API.
 * And until [captureLogData] is called, this factory delegates backend
 * creation to the default backend factory.
 *
 * When called, [captureLogData] substitutes the default backend factory
 * with [MemoizingLoggerBackendFactory] (that memoizes log data),
 * executes the given [action], switches back to the default backend factory,
 * and returns the captured log data. Out of this comes a restriction
 * on logger creation within the [action]. If a logger is created outside
 * the [action], its log data will be passed to the default backend because
 * the factory has not been substituted in the moment of a logger creation.
 *
 * The method is inlined to preserve the original log site.
 */
public inline fun captureLogData(action: () -> Unit): List<LogData> {
    val memoizingBackends = MemoizingLoggerBackendFactory()
    val memoizingFactory = MemoizingBackendFactory(memoizingBackends)

    // Runs the given action with a substituted backend factory.
    withBackendFactory(memoizingFactory, action)

    // Makes sure `action` has created at least one logger inside the `action`.
    // Otherwise, calling of this method is doubtful.
    check(memoizingFactory.createdBackends.isNotEmpty()) {
        "Zero backends were created where at least one was expected."
    }

    // Several loggers could have been created within the `action`.
    // Each logger spawns its own instance of backend.
    val loggedFromAllBackends = memoizingFactory.createdBackends.flatMap { it.logged }
    return loggedFromAllBackends
}

/**
 * Sets the given backend [factory], and runs the given [action].
 *
 * After [action] is performed, the default backend factory is restored.
 *
 * This method is public because it is inlined.
 */
public inline fun withBackendFactory(factory: TypedBackendFactory<*>, action: () -> Unit) {
    DynamicBackendFactory.delegate(factory)
    action()
    DynamicBackendFactory.reset()
}
