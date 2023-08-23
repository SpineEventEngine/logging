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

package io.spine.logging.testutil

import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Executes the given [action] and returns the text printed to the console.
 *
 * @see TapConsole
 */
public fun tapConsole(action: () -> Unit): String {
    val tap = StringOutputStream()
    TapConsole.executeWithStream(tap, action)
    return tap.output()
}

/**
 * This test fixture is designed to intercept and control output sent
 * to [System.out] and [System.err] during the execution of a given code block
 * when [executeWithStream] method is called.
 *
 * The purpose of this test fixture is to address a limitation of the Java Logging
 * framework, where the [ConsoleHandler][java.util.logging.ConsoleHandler] is initialized
 * with the stream tied to [System.err] during the parameterless constructor call.
 * This makes it impossible to replace [System.err] with a custom stream after
 * the logging framework is initialized, as `ConsoleHandler` continues using
 * the original [System.err] value.
 *
 * At initialization, this object replaces [System.out] and [System.err] with custom,
 * redirectable streams. When [executeWithStream] is invoked, the current streams are
 * replaced with a stream passed as an argument. Once the execution concludes,
 * the original streams are reinstated.
 *
 * This fixture is deliberately constructed as an `object` that doesn't restore
 * the original streams after construction. This approach provides a reliable solution
 * for the Java Logging framework's static binding to [System.err].
 *
 * Not restoring the streams does not affect our current testing requirements.
 * However, for more extensive testing scenarios that also involve console output
 * interception and complex interactions with Java Logging framework's quirks,
 * this could potentially create issues.
 */
private object TapConsole {

    private val out = RedirectingPrintStream(System.out)
    private val err = RedirectingPrintStream(System.err)

    init {
        System.setOut(out)
        System.setErr(err)
    }

    fun executeWithStream(stream: PrintStream, action: () -> Unit) {
        out.redirect(stream)
        err.redirect(stream)
        try {
            action()
        } finally {
            out.restore()
            err.restore()
        }
    }
}

/**
 * Redirects the output to the given [PrintStream] and restores the original
 * stream when instructed.
 */
private class RedirectingPrintStream(initial: PrintStream): PrintStream(initial) {

    private var prevStream: PrintStream = initial

    fun redirect(newStream: PrintStream) {
        prevStream = out as PrintStream
        out = newStream
    }

    fun restore() {
        out = prevStream
    }
}

/**
 * A [PrintStream] which stores the output in a byte array and allows to retrieve it.
 *
 * @param size the initial size of the underlying byte array, which grows as needed.
 */
private class StringOutputStream(size: Int = 4096): PrintStream(ByteArrayOutputStream(size), true) {

    /**
     * Flushes the underlying stream and returns the text written to it.
     */
    fun output(): String {
        out.flush()
        return out.toString()
    }
}
