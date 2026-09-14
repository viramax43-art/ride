package com.rideminiapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoleTest {
    @Test
    fun `maps backend roles`() {
        assertEquals(Role.PASSENGER, Role.fromBackend("passenger"))
        assertEquals(Role.DRIVER, Role.fromBackend("driver"))
        assertEquals(Role.ADMIN, Role.fromBackend("admin"))
        assertEquals(Role.MODERATOR, Role.fromBackend("moderator"))
    }

    @Test
    fun `returns null for unknown role`() {
        assertNull(Role.fromBackend("unknown"))
    }
}
