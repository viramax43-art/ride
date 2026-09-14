package com.rideminiapp.ui.common

import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.util.Log
import androidx.fragment.app.Fragment
import com.rideminiapp.BuildConfig

private const val TELEGRAM_PACKAGE = "org.telegram.messenger"
private const val TAG = "TelegramHandoff"
private const val DEFAULT_BOT_NAME = "rideminiapp_bot"
private const val DEFAULT_START_PARAM = "auth"

data class TelegramHandoffTarget(
    val botName: String,
    val startParam: String,
)

fun resolveTelegramHandoffTarget(handoffUrl: String): TelegramHandoffTarget {
    val parsed = Uri.parse(handoffUrl.ifBlank { BuildConfig.DEFAULT_HANDOFF_URL })
    val host = parsed.host?.lowercase().orEmpty()
    val pathSegments = parsed.pathSegments.orEmpty().filter { it.isNotBlank() }
    val botName = when {
        host == "t.me" || host == "telegram.me" || host == "telegram.dog" ->
            pathSegments.firstOrNull().orEmpty().ifBlank { DEFAULT_BOT_NAME }
        else -> DEFAULT_BOT_NAME
    }

    val startParam = parsed.getQueryParameter("start")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: DEFAULT_START_PARAM

    return TelegramHandoffTarget(
        botName = botName,
        startParam = startParam,
    )
}

fun Fragment.openTelegramHandoff(handoffUrl: String): Boolean {
    val target = resolveTelegramHandoffTarget(handoffUrl)

    val httpsUri = buildString {
        append("https://t.me/")
        append(Uri.encode(target.botName))
        append("?start=")
        append(Uri.encode(target.startParam))
    }

    val httpsIntent = Intent(Intent.ACTION_VIEW, Uri.parse(httpsUri)).apply {
        setPackage(TELEGRAM_PACKAGE)
    }

    val fallbackUri = buildString {
        append("tg://resolve?domain=")
        append(Uri.encode(target.botName))
        append("&start=")
        append(Uri.encode(target.startParam))
    }

    Log.d(TAG, "openTelegramHandoff https=$httpsUri fallback=$fallbackUri")
    return runCatching {
        startActivity(httpsIntent)
        true
    }.recoverCatching {
        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUri)).apply {
            setPackage(TELEGRAM_PACKAGE)
        }
        startActivity(fallbackIntent)
        true
    }.getOrElse { error ->
        if (error is ActivityNotFoundException) {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUri)))
                true
            }.getOrDefault(false)
        } else {
            false
        }
    }
}
