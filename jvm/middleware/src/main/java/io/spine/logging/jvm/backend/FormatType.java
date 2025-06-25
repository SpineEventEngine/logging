/*
 * Copyright 2019, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

package io.spine.logging.jvm.backend;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * The general formatting type of any one of the predefined {@code FormatChar} instances.
 *
 * @see <a
 *         href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/FormatType.java">
 *         Original Java code of Google Flogger</a>
 */
public enum FormatType {
    /** General formatting that can be applied to any type. */
    GENERAL(false, true) {
        @Override
        public boolean canFormat(Object arg) {
            return true;
        }
    },

    /** Formatting that can be applied to any boolean type. */
    BOOLEAN(false, false) {
        @Override
        public boolean canFormat(Object arg) {
            return arg instanceof Boolean;
        }
    },

    /**
     * Formatting that can be applied to Character or any integral type that can be losslessly
     * converted to an int and for which {@link Character#isValidCodePoint(int)} returns true.
     */
    CHARACTER(false, false) {
        @Override
        public boolean canFormat(Object arg) {
            // Ordering in relative likelihood.
            if (arg instanceof Character) {
                return true;
            } else if ((arg instanceof Integer) || (arg instanceof Byte) ||
                    (arg instanceof Short)) {
                return Character.isValidCodePoint(((Number) arg).intValue());
            } else {
                return false;
            }
        }
    },

    /**
     * Formatting that can be applied to any integral Number type. Logging backends must support
     * Byte,
     * Short, Integer, Long and BigInteger but may also support additional numeric types directly.
     * A
     * logging backend that encounters an unknown numeric type should fall back to using
     * {@code toString()}.
     */
    INTEGRAL(true, false) {
        @Override
        public boolean canFormat(Object arg) {
            // Ordering in relative likelihood.
            return (arg instanceof Integer)
                    || (arg instanceof Long)
                    || (arg instanceof Byte)
                    || (arg instanceof Short)
                    || (arg instanceof BigInteger);
        }
    },

    /**
     * Formatting that can be applied to any Number type. Logging backends must support all the
     * integral types as well as Float, Double and BigDecimal, but may also support additional
     * numeric
     * types directly. A logging backend that encounters an unknown numeric type should fall back
     * to
     * using {@code toString()}.
     */
    FLOAT(true, true) {
        @Override
        public boolean canFormat(Object arg) {
            // Ordering in relative likelihood.
            return (arg instanceof Double) || (arg instanceof Float) || (arg instanceof BigDecimal);
        }
    };

    private final boolean isNumeric;
    private final boolean supportsPrecision;

    private FormatType(boolean isNumeric, boolean supportsPrecision) {
        this.isNumeric = isNumeric;
        this.supportsPrecision = supportsPrecision;
    }

    /**
     * True if the notion of a specified precision value makes sense to this format type. Precision
     * is
     * specified in addition to width and can control the resolution of a formatting operation
     * (e.g.
     * how many digits to output after the decimal point for floating point values).
     */
    boolean supportsPrecision() {
        return supportsPrecision;
    }

    /**
     * True if this format type requires a {@link Number} instance (or one of the corresponding
     * fundamental types) as an argument.
     */
    public boolean isNumeric() {
        return isNumeric;
    }

    public abstract boolean canFormat(Object arg);
}
