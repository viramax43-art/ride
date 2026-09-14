package com.rideminiapp.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminPolicyTest {
    @Test
    fun `recognizes configured admin usernames`() {
        assertTrue(AdminPolicy.isChiefAdminUsername("ShitshiBB"))
        assertTrue(AdminPolicy.isChiefAdminUsername("@ShitshiBB"))
        assertTrue(AdminPolicy.isChiefAdminUsername("  shitshibb  "))
        assertTrue(AdminPolicy.isChiefAdminUsername("varl_blask"))
        assertFalse(AdminPolicy.isChiefAdminUsername("driver"))
        assertFalse(AdminPolicy.isChiefAdminUsername(null))
        assertFalse(AdminPolicy.isBlockedFromAdmin("ShitshiBB"))
        assertFalse(AdminPolicy.isBlockedFromAdmin("@ShitshiBB"))
        assertFalse(AdminPolicy.isBlockedFromAdmin("driver"))
    }
}
