package com.chelmodeev.altimeter.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthReaderPagingTest {

    @Test
    fun emptyTokenMeansLastPageOnOemProviders() {
        assertNull(nextHealthPageToken(null))
        assertNull(nextHealthPageToken(""))
        assertEquals("next", nextHealthPageToken("next"))
    }
}
