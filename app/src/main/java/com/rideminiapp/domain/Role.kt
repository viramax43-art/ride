package com.rideminiapp.domain

enum class Role {
    PASSENGER,
    DRIVER,
    ADMIN,
    MODERATOR;

    companion object {
        fun fromBackend(value: String?): Role? = when (value?.trim()?.lowercase()) {
            "passenger" -> PASSENGER
            "driver" -> DRIVER
            "admin" -> ADMIN
            "moderator" -> MODERATOR
            else -> null
        }
    }
}
