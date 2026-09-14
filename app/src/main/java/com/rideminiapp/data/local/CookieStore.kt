package com.rideminiapp.data.local

import android.util.Base64
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

class CookieStore(
    private val store: MmkvStore,
) : CookieJar {
    private val cache = ConcurrentHashMap<String, MutableSet<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val hostKey = hostKey(url)
        val merged = cache[hostKey] ?: load(url).toMutableSet()
        cookies.forEach { cookie ->
            merged.removeAll { existing -> existing.name == cookie.name && existing.domain == cookie.domain && existing.path == cookie.path }
            if (cookie.expiresAt > System.currentTimeMillis()) {
                merged += cookie
            }
        }
        cache[hostKey] = merged
        persist(hostKey, merged)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val hostKey = hostKey(url)
        val cookies = cache[hostKey] ?: load(url).toMutableSet()
        val now = System.currentTimeMillis()
        return cookies.filter { it.expiresAt > now && it.matches(url) }
    }

    private fun hostKey(url: HttpUrl): String = url.host.lowercase()

    private fun load(url: HttpUrl): Set<Cookie> {
        val encoded = store.getString(cookieKey(hostKey(url)), "")
        if (encoded.isBlank()) return emptySet()
        return encoded.split('\n')
            .mapNotNull { decodeCookie(it) }
            .toSet()
    }

    private fun persist(hostKey: String, cookies: Set<Cookie>) {
        val encoded = cookies.joinToString("\n") { encodeCookie(it) }
        store.putString(cookieKey(hostKey), encoded)
    }

    private fun cookieKey(hostKey: String) = "cookie_$hostKey"

    private fun encodeCookie(cookie: Cookie): String {
        val raw = listOf(
            cookie.name,
            cookie.value,
            cookie.expiresAt.toString(),
            cookie.domain,
            cookie.path,
            cookie.secure.toString(),
            cookie.httpOnly.toString(),
            cookie.hostOnly.toString(),
            cookie.persistent.toString(),
        ).joinToString("|")
        return Base64.encodeToString(raw.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
    }

    private fun decodeCookie(encoded: String): Cookie? = runCatching {
        val raw = String(Base64.decode(encoded, Base64.NO_WRAP), StandardCharsets.UTF_8)
        val parts = raw.split('|')
        if (parts.size < 9) return null
        val builder = Cookie.Builder()
            .name(parts[0])
            .value(parts[1])
            .expiresAt(parts[2].toLong())
            .path(parts[4])
        if (parts[5].toBoolean()) builder.secure()
        if (parts[6].toBoolean()) builder.httpOnly()
        if (parts[7].toBoolean()) {
            builder.hostOnlyDomain(parts[3])
        } else {
            builder.domain(parts[3])
        }
        builder.build()
    }.getOrNull()
}
