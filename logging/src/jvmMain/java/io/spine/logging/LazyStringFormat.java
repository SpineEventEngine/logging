package io.spine.logging;

import kotlin.jvm.functions.Function0;

import java.util.Locale;

import static java.lang.String.format;

/**
 * Provides a utility method to simplify passing formatted strings
 * to {@linkplain LoggingApi#log(Function0) loggers}.
 */
public final class LazyStringFormat {

    /**
     * Prevents instantiation of this utility class.
     */
    private LazyStringFormat() {
    }

    /**
     * Returns a string supplier with a formatted string.
     *
     * <p>The formatter uses {@link Locale#ENGLISH ENGLISH} locale.
     *
     * @see String#format(String, Object...)
     */
    public static Function0<String> lazyFormat(String format, Object... args) {
        return () -> format(Locale.ENGLISH, format, args);
    }
}
