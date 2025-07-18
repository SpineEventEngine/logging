/*
 * Copyright 2023, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

package io.spine.logging.jvm.parameter

import java.util.Calendar
import java.util.Collections
import java.util.Date

/**
 * Supported date/time sub-format characters for the `%t/%T` formatting pattern.
 *
 * **WARNING:** Many date/time format specifiers use the system default time-zone for formatting
 * [Date] or [Long] arguments. This makes it non-system-portable, and its use is heavily
 * discouraged with non-[Calendar] arguments.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a783099ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parameter/DateTimeFormat.java">
 *   Original Java code of Google Flogger</a> for historical context.
 */
public enum class DateTimeFormat(public val char: Char) {

    // The following conversion characters are used for formatting times:

    /**
     * Hour of the day for the 24-hour clock, formatted as two digits with a leading zero as
     * necessary, i.e., `00 - 23`.
     */
    TIME_HOUR_OF_DAY_PADDED('H'),

    /**
     * Hour of the day for the 24-hour clock, i.e., `0 - 23`.
     */
    TIME_HOUR_OF_DAY('k'),

    /**
     * Hour for the 12-hour clock, formatted as two digits with a leading zero as necessary,
     * i.e., `01 - 12`.
     */
    TIME_HOUR_12H_PADDED('I'),

    /**
     * Hour for the 12-hour clock, i.e., `1 - 12`.
     */
    TIME_HOUR_12H('l'),

    /**
     * Minute within the hour formatted as two digits with a leading zero as necessary,
     * i.e., `00 - 59`.
     */
    TIME_MINUTE_OF_HOUR_PADDED('M'),

    /**
     * Seconds within the minute, formatted as two digits with a leading zero as necessary,
     * i.e., `00 - 60` ("60" is a special value required to support leap seconds).
     */
    TIME_SECONDS_OF_MINUTE_PADDED('S'),

    /**
     * Millisecond within the second formatted as three digits with leading zeros as necessary,
     * i.e., `000 - 999`.
     */
    TIME_MILLIS_OF_SECOND_PADDED('L'),

    /**
     * Nanosecond within the second, formatted as nine digits with leading zeros as necessary,
     * i.e., `000000000 - 999999999`.
     */
    TIME_NANOS_OF_SECOND_PADDED('N'),

    /**
     * Locale-specific morning or afternoon marker in lower case, e.g., `"am"` or `"pm"`.
     */
    TIME_AM_PM('p'),

    /**
     * RFC-822 style numeric time zone offset from GMT, e.g., `"-0800"`.
     *
     * This value will be adjusted as necessary for Daylight Saving Time.
     * For `long`, `Long`, and `Date` the time zone used is the
     * default time zone for this instance of the Java virtual machine.
     */
    TIME_TZ_NUMERIC('z'),

    /**
     * A string representing the abbreviation for the time zone.
     *
     * This value will be adjusted as necessary for Daylight Saving Time.
     * For `long`, `Long`, and `Date` the time zone used is the
     * default time zone for this instance of the Java virtual machine.
     */
    TIME_TZ_SHORT('Z'),

    /**
     * Seconds since the beginning of the epoch starting at `1 January 1970 00:00:00 UTC`.
     */
    TIME_EPOCH_SECONDS('s'),

    /**
     * Milliseconds since the beginning of the epoch starting at `1 January 1970 00:00:00 UTC`.
     */
    TIME_EPOCH_MILLIS('Q'),

    // The following conversion characters are used for formatting dates:

    /**
     * Locale-specific full month name, e.g., "January", "February".
     */
    DATE_MONTH_FULL('B'),

    /** Locale-specific abbreviated month name, e.g., "Jan", "Feb". */
    DATE_MONTH_SHORT('b'),

    /**
     * Same as `'b'`.
     */
    DATE_MONTH_SHORT_ALT('h'),

    /**
     * Locale-specific full name of the day of the week, e.g., `"Sunday"`, `"Monday"`.
     */
    DATE_DAY_FULL('A'),

    /**
     * Locale-specific short name of the day of the week, e.g., `"Sun"`, `"Mon"`.
     */
    DATE_DAY_SHORT('a'),

    /**
     * Four-digit year divided by 100, formatted as two digits with leading zero as necessary,
     * i.e., `00 - 99`.
     *
     * Note that this is not strictly the `"century"`, because `"19xx"` is `"19"`, not `"20"`.
     */
    DATE_CENTURY_PADDED('C'),

    /**
     * Year, formatted as at least four digits with leading zeros as necessary, e.g., `0092`.
     */
    DATE_YEAR_PADDED('Y'),

    /**
     * Last two digits of the year, formatted with leading zeros as necessary, i.e., `00 - 99`.
     */
    DATE_YEAR_OF_CENTURY_PADDED('y'),

    /**
     * Day of a year, formatted as three digits with leading zeros as necessary, e.g., `001 - 366`.
     */
    DATE_DAY_OF_YEAR_PADDED('j'),

    /**
     * Month, formatted as two digits with leading zeros as necessary, i.e., `01 - 13`.
     */
    DATE_MONTH_PADDED('m'),

    /**
     * Day of a month, formatted as two digits with leading zeros as necessary, i.e., `01 - 31`.
     */
    DATE_DAY_OF_MONTH_PADDED('d'),

    /**
     * Day of a month, formatted as two digits, i.e., `1 - 31`.
     */
    DATE_DAY_OF_MONTH('e'),

    // The following conversion characters are used for formatting common date/time compositions.

    /**
     * Time formatted for the 24-hour clock as `"%tH:%tM"`.
     */
    DATETIME_HOURS_MINUTES('R'),

    /**
     * Time formatted for the 24-hour clock as `"%tH:%tM:%tS"`.
     */
    DATETIME_HOURS_MINUTES_SECONDS('T'),

    /**
     * Time formatted for the 12-hour clock as `"%tI:%tM:%tS %Tp"`.
     */
    DATETIME_HOURS_MINUTES_SECONDS_12H('r'),

    /**
     * Date formatted as `"%tm/%td/%ty"`.
     */
    DATETIME_MONTH_DAY_YEAR('D'),

    /**
     * ISO 8601 complete date formatted as `"%tY-%tm-%td"`.
     */
    DATETIME_YEAR_MONTH_DAY('F'),

    /**
     * Date and time formatted as `"%ta %tb %td %tT %tZ %tY"`,
     * e.g., `"Sun Jul 20 16:17:00 EDT 1969"`.
     */
    DATETIME_FULL('c');

    public companion object {

        private val MAP: Map<Char, DateTimeFormat> = run {
            val map = HashMap<Char, DateTimeFormat>()
            for (dtf in entries) {
                if (map.put(dtf.char, dtf) != null) {
                    error("Duplicate format character: `$dtf`")
                }
            }
            Collections.unmodifiableMap(map)
        }

        /**
         * Obtains [DateTimeFormat] instance corresponding to the given format character.
         */
        @JvmStatic
        public fun of(c: Char): DateTimeFormat? = MAP[c]
    }
}
