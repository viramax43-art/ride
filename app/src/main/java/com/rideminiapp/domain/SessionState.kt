package com.rideminiapp.domain

data class SessionState(
    val role: Role? = null,
    val currentUsername: String? = null,
    val passengerToken: String? = null,
    val driverCookieReady: Boolean = false,
    val adminCookieReady: Boolean = false,
    val language: String = "ru",
    val apiBaseUrl: String = "",
    val handoffUrl: String = "",
)
