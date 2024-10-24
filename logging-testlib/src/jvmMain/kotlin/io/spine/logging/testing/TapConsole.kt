/*
 * Copyright 2024, TeamDev. All rights reserved.
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

package io.spine.logging.testing

import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Executes the given [action] and returns the text printed to the console.
 *
 * If your console output involves Java logging (directly or indirectly), please make sure
 * to call [ConsoleTap.install] before all your tests.
 *
 * @see ConsoleTap.install
 */
public fun tapConsole(action: () -> Unit): String {
    synchronized(ConsoleTap) {
        val tap = StringOutputStream()
        ConsoleTap.executeWithStream(tap, action)
        return tap.output()
    }
}

/**
 * This test fixture is designed to intercept and control output sent
 * to [System.out] and [System.err] during the execution of a given code block
 * when [tapConsole] function is called.
 *
 * ## Implementation note
 *
 * The primary purpose of this test fixture is to address a limitation of the Java Logging
 * framework, where the [ConsoleHandler][java.util.logging.ConsoleHandler] is initialized
 * with the stream tied to [System.err] during the parameterless constructor call.
 * This makes it impossible to replace [System.err] with a custom stream after
 * the logging framework is initialized, as `ConsoleHandler` continues using
 * the original [System.err] value.
 *
 * This fixture is deliberately constructed as an `object` that does not restore
 * the original streams after they are replaced. This approach provides a reliable solution
 * for the Java Logging framework's static binding to [System.err].
 *
 * Not restoring the streams does not affect our current testing requirements.
 * However, for more extensive testing scenarios that also involve console output
 * interception and complex interactions with Java Logging framework's quirks,
 * this could potentially create issues.
 */
public object ConsoleTap {

    private val out = RedirectingPrintStream(System.out)
    private val err = RedirectingPrintStream(System.err)

    init {
        install()
    }

    /**
     * Ensures that stream redirections associated with [tapConsole] function are installed before
     * the function is called.
     *
     * Invoke this function in test suites before all the tests that call [tapConsole] to
     * ensure redirection of [System.out] and [System.err] streams.
     *
     * ## Capturing logging output
     * If your tests verify logging output, please take into account that instances of loggers
     * that you use may be already created with references to [System.out] and [System.err] streams
     * existing before this function is called. In such a case, [tapConsole] will not be able to
     * capture the logging output of these loggers. Calling this function before creation of
     * the loggers would solve the issue.
     */
    public fun install() {
        System.setOut(out)
        System.setErr(err)
    }

    internal fun executeWithStream(stream: PrintStream, action: () -> Unit) {
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
 * @param size The initial size of the underlying byte array, which grows as needed.
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
