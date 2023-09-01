package io.spine.logging

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`AnnotationsLookup` should")
internal class AnnotationsLookupSpec {

    @Nested inner class
    `return annotation if the asked package` {

        @Test
        fun `is directly annotated`() {

        }

        @Test
        fun `is directly annotated root`() {

        }

        @Test
        fun `is indirectly annotated by midway package`() {

        }

        @Test
        fun `is indirectly annotated by root package`() {

        }
    }

    @Nested inner class
    `return 'null' if the asked` {

        @Test
        fun `root package is not annotated`() {

        }

        @Test
        fun `midway package and its parents are not annotated`() {

        }
    }

    @Nested inner class
    cache {

        @Test
        fun `the asked package`() {

        }

        @Test
        fun `the asked root package`() {

        }

        @Test
        fun `midway parental packages`() {

        }
    }
}
