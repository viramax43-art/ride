package com.rideminiapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionStateTest {
    @Test
    fun `defaults are stable`() {
        val state = SessionState()

        assertNull(state.role)
        assertNull(state.passengerToken)
        assertEquals(false, state.driverCookieReady)
        assertEquals(false, state.adminCookieReady)
        assertEquals("ru", state.language)
    }
}
