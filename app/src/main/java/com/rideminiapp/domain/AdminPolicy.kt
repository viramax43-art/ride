package com.rideminiapp.domain

object AdminPolicy {
    private val ADMIN_USERNAMES = setOf("shitshibb", "varl_blask")
    private val BLOCKED_ADMIN_USERNAMES = emptySet<String>()

    fun isChiefAdminUsername(username: String?): Boolean {
        val normalized = username?.trim()?.removePrefix("@")?.lowercase().orEmpty()
        return normalized in ADMIN_USERNAMES
    }

    fun isBlockedFromAdmin(username: String?): Boolean {
        val normalized = username?.trim()?.removePrefix("@")?.lowercase().orEmpty()
        return normalized in BLOCKED_ADMIN_USERNAMES
    }
}
