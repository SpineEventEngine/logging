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

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream

/**
 * Executes the given [action] and returns the text printed to the console.
 *
 * @see TapConsole
 */
internal fun tapConsole(action: () -> Unit): String {
    val bytes = TapBytesStream(4096)
    val printer = PrintStream(bytes, true)
    TapConsole.executeWithStream(printer, action)
    return bytes.output()
}

/**
 * This object is a test fixture that allows to tap the output
 * sent to [System.out] and [System.err] when [executing][executeWithStream]
 * a code block which potentially can produce console output.
 *
 * This arrangement is needed as a workaround for the fact that Java Logging framework
 * initializes the [ConsoleHandler][java.util.logging.ConsoleHandler] with the stream
 * object assigned to [System.err] at the time of the calling parameterless constructor of
 * [ConsoleHandler][java.util.logging.ConsoleHandler]. That's why replacing the value of
 * [System.err] with a custom stream after the initialization of the logging framework has
 * no effect for intercepting the output.
 *
 * During the initialization the object replaces both [System.out] and [System.err] with
 * the custom streams which can be redirected. When [executeWithStream] is called,
 * a stream passed as a parameter is used to replace the current streams. After the execution
 * is complete, the original streams are restored.
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
private class RedirectingPrintStream(
    initial: PrintStream
): PrintStream(DelegatingOutputStream(initial)) {

    private val output = out as DelegatingOutputStream
    private var prevStream: OutputStream = initial

    fun redirect(newStream: PrintStream) {
        prevStream = output.delegate
        output.redirect(newStream)
    }

    fun restore() = output.redirect(prevStream)
}

/**
 * A stream which performs writing via the [delegate].
 */
private class DelegatingOutputStream(var delegate: OutputStream): OutputStream()  {

    override fun write(b: Int) =
        delegate.write(b)

    fun redirect(newOutputStream: OutputStream) {
        delegate = newOutputStream
    }
}

/**
 * A typed replacement for [ByteArrayOutputStream] for easier debugging.
 */
private class TapBytesStream(size: Int): ByteArrayOutputStream(size) {

    fun output(): String {
        flush()
        return toString()
    }
}
