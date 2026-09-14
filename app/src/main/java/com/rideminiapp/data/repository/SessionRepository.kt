package com.rideminiapp.data.repository

import com.rideminiapp.BuildConfig
import com.rideminiapp.data.local.AppPrefs
import com.rideminiapp.data.local.MmkvStore
import com.rideminiapp.data.local.SecureStorage
import com.rideminiapp.util.AppLog
import com.rideminiapp.data.remote.InitDataRequest
import com.rideminiapp.data.remote.CurrentUserDto
import com.rideminiapp.data.remote.RideApiService
import com.rideminiapp.di.CookieApi
import com.rideminiapp.di.PassengerApi
import com.rideminiapp.di.PublicApi
import com.rideminiapp.domain.AdminPolicy
import com.rideminiapp.domain.Role
import com.rideminiapp.domain.SessionState
import com.rideminiapp.ui.common.resolveTelegramHandoffTarget
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val store: MmkvStore,
    private val secureStorage: SecureStorage,
    @PublicApi private val publicApi: RideApiService,
    @CookieApi private val cookieApi: RideApiService,
    @PassengerApi private val bearerApi: RideApiService,
) {
    private companion object {
        const val TAG = "SessionRepository"
    }

    fun loadState(): SessionState {
        val role = Role.fromBackend(store.getString(AppPrefs.PREFERRED_ROLE, ""))
        val currentUsername = store.getString(AppPrefs.CURRENT_USERNAME, "").takeIf { it.isNotBlank() }
        val passengerToken = secureStorage.getAccessToken()
            ?: store.getString(AppPrefs.PASSENGER_TOKEN, "").takeIf { it.isNotBlank() }
        val storedHandoffUrl = store.getString(AppPrefs.HANDOFF_URL, BuildConfig.DEFAULT_HANDOFF_URL).trim()
        val storedLanguage = store.getString(AppPrefs.PREFERRED_LANGUAGE, "ru").trim().lowercase()
        val language = when {
            storedLanguage.isBlank() -> "ru"
            storedLanguage == "lt" -> {
                store.putString(AppPrefs.PREFERRED_LANGUAGE, "ru")
                "ru"
            }
            else -> storedLanguage
        }
        val state = SessionState(
            role = role,
            currentUsername = currentUsername,
            passengerToken = passengerToken,
            driverCookieReady = store.getBoolean("driver_cookie_ready", false),
            adminCookieReady = store.getBoolean("admin_cookie_ready", false),
            language = language,
            apiBaseUrl = store.getString(AppPrefs.API_BASE_URL, BuildConfig.DEFAULT_API_BASE_URL),
            handoffUrl = normalizeTelegramHandoffUrl(storedHandoffUrl),
        )
        AppLog.d(TAG, "loadState username=${state.currentUsername} role=${state.role} passengerToken=${state.passengerToken != null} driverCookie=${state.driverCookieReady} adminCookie=${state.adminCookieReady}")
        return state
    }

    fun saveApiBaseUrl(url: String) {
        val normalized = url.trim()
        AppLog.d(TAG, "saveApiBaseUrl length=${normalized.length}")
        store.putString(AppPrefs.API_BASE_URL, normalized)
    }

    fun saveHandoffUrl(url: String) {
        val normalized = normalizeTelegramHandoffUrl(url.trim())
        AppLog.d(TAG, "saveHandoffUrl normalized=${normalized}")
        store.putString(AppPrefs.HANDOFF_URL, normalized)
    }

    fun saveLanguage(language: String) {
        AppLog.d(TAG, "saveLanguage language=$language")
        store.putString(AppPrefs.PREFERRED_LANGUAGE, language)
    }

    fun saveRole(role: Role) {
        AppLog.d(TAG, "saveRole role=$role")
        store.putString(AppPrefs.PREFERRED_ROLE, role.name.lowercase())
    }

    fun saveCurrentUsername(username: String?) {
        val normalized = username?.trim().orEmpty()
        AppLog.d(TAG, "saveCurrentUsername username=${normalized.ifBlank { "<blank>" }}")
        if (normalized.isBlank()) {
            store.remove(AppPrefs.CURRENT_USERNAME)
        } else {
            store.putString(AppPrefs.CURRENT_USERNAME, normalized)
        }
    }

    fun storePassengerToken(token: String) {
        val normalized = token.trim()
        AppLog.d(TAG, "storePassengerToken tokenSet=${normalized.isNotBlank()}")
        secureStorage.saveAccessToken(normalized)
        store.remove(AppPrefs.PASSENGER_TOKEN)
        clearTelegramHandoffAttempted()
    }

    fun markWelcomeRedirected() = store.putBoolean(AppPrefs.WELCOME_REDIRECTED, true)
    fun shouldAutoRedirectWelcome() = !store.getBoolean(AppPrefs.WELCOME_REDIRECTED, false)
    fun resetWelcomeRedirect() = store.remove(AppPrefs.WELCOME_REDIRECTED)
    fun clearSelectedRole() = store.remove(AppPrefs.PREFERRED_ROLE)

    fun clearAdminAccess() {
        store.remove(AppPrefs.PREFERRED_ROLE)
        store.remove("admin_cookie_ready")
    }

    suspend fun loginPassengerWithTelegramInitData(initData: String): String {
        AppLog.d(TAG, "loginPassengerWithTelegramInitData initDataSet=${initData.isNotBlank()}")
        val response = publicApi.loginWithTelegram(InitDataRequest(initData))
        storePassengerToken(response.accessToken)
        saveRole(Role.PASSENGER)
        store.remove("driver_cookie_ready")
        store.remove("admin_cookie_ready")
        runCatching { refreshCurrentUserFromPassengerToken() }
        return response.accessToken
    }

    suspend fun registerPushToken(token: String): Boolean {
        if (token.isBlank()) return false
        if (secureStorage.getAccessToken().isNullOrBlank()) return false
        return runCatching {
            bearerApi.registerPushToken(mapOf("token" to token.trim()))
            true
        }.getOrDefault(false)
    }

    suspend fun refreshCurrentUserFromPassengerToken(): CurrentUserDto? {
        val token = secureStorage.getAccessToken() ?: return null
        AppLog.d(TAG, "refreshCurrentUserFromPassengerToken tokenSet=${token.isNotBlank()}")
        return runCatching {
            val currentUser = bearerApi.getCurrentUser()
            saveCurrentUsername(currentUser.username)
            when (Role.fromBackend(currentUser.role)) {
                Role.PASSENGER -> saveRole(Role.PASSENGER)
                Role.DRIVER -> saveRole(Role.DRIVER)
                Role.ADMIN -> if (BuildConfig.IS_ADMIN_APP) saveRole(Role.ADMIN) else clearSelectedRole()
                else -> {}
            }
            if (BuildConfig.IS_ADMIN_APP && AdminPolicy.isChiefAdminUsername(currentUser.username)) {
                saveRole(Role.ADMIN)
            }
            currentUser
        }.getOrNull()
    }

    suspend fun loginDriverWithKey(key: String): Boolean {
        AppLog.d(TAG, "loginDriverWithKey keySet=${key.isNotBlank()}")
        cookieApi.loginDriver(mapOf("key" to key))
        store.putBoolean("driver_cookie_ready", true)
        saveRole(Role.DRIVER)
        return true
    }

    suspend fun loginAdminWithKey(key: String): Boolean {
        AppLog.d(TAG, "loginAdminWithKey keySet=${key.isNotBlank()}")
        cookieApi.loginAdmin(mapOf("key" to key))
        store.putBoolean("admin_cookie_ready", true)
        saveRole(Role.ADMIN)
        return true
    }

    suspend fun bootstrapPassenger(): Boolean {
        val token = secureStorage.getAccessToken().orEmpty()
        if (token.isBlank()) return false
        return runCatching {
            bearerApi.getCurrentUser()
            true
        }.getOrDefault(false)
    }

    suspend fun resolveRoleOrNull(): Role? {
        val state = loadState()
        return when {
            AdminPolicy.isBlockedFromAdmin(state.currentUsername) -> {
                clearAdminAccess()
                null
            }
            AdminPolicy.isChiefAdminUsername(state.currentUsername) -> if (BuildConfig.IS_ADMIN_APP) Role.ADMIN else null
            state.role == Role.PASSENGER -> state.passengerToken?.let { Role.PASSENGER }
            state.role == Role.DRIVER -> if (state.driverCookieReady) Role.DRIVER else null
            state.role == Role.ADMIN -> if (BuildConfig.IS_ADMIN_APP && AdminPolicy.isChiefAdminUsername(state.currentUsername)) Role.ADMIN else null
            state.driverCookieReady -> Role.DRIVER
            state.passengerToken != null -> if (BuildConfig.IS_ADMIN_APP) null else Role.PASSENGER
            else -> null
        }
    }

    fun logoutAll() {
        AppLog.d(TAG, "logoutAll")
        secureStorage.clear()
        store.remove(AppPrefs.PASSENGER_TOKEN)
        store.remove(AppPrefs.PREFERRED_ROLE)
        store.remove(AppPrefs.CURRENT_USERNAME)
        store.remove("driver_cookie_ready")
        store.remove("admin_cookie_ready")
        clearTelegramHandoffAttempted()
        resetWelcomeRedirect()
    }

    fun markTelegramHandoffAttempted() {
        AppLog.d(TAG, "markTelegramHandoffAttempted")
        store.putBoolean(AppPrefs.TELEGRAM_HANDOFF_ATTEMPTED, true)
    }

    fun hasTelegramHandoffAttempted(): Boolean {
        return store.getBoolean(AppPrefs.TELEGRAM_HANDOFF_ATTEMPTED, false)
    }

    fun clearTelegramHandoffAttempted() {
        store.remove(AppPrefs.TELEGRAM_HANDOFF_ATTEMPTED)
    }

    private fun normalizeTelegramHandoffUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return BuildConfig.DEFAULT_HANDOFF_URL

        val parsed = runCatching { android.net.Uri.parse(trimmed) }.getOrNull() ?: return BuildConfig.DEFAULT_HANDOFF_URL
        val host = parsed.host?.lowercase().orEmpty()
        if (host != "t.me" && host != "telegram.me" && host != "telegram.dog") {
            return BuildConfig.DEFAULT_HANDOFF_URL
        }

        val target = resolveTelegramHandoffTarget(trimmed)
        return buildString {
            append("https://t.me/")
            append(android.net.Uri.encode(target.botName))
            append("?start=")
            append(android.net.Uri.encode(target.startParam))
        }
    }
}
