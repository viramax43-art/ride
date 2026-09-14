package com.rideminiapp

import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.rideminiapp.BuildConfig
import com.rideminiapp.data.repository.SessionRepository
import com.rideminiapp.ui.common.resolveTelegramHandoffTarget
import com.rideminiapp.ui.web.WebAppFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private companion object {
        const val TAG = "MainActivity"
    }

    @Inject lateinit var sessionRepository: SessionRepository

    private val containerId = android.R.id.content

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "onCreate savedInstanceState=${savedInstanceState != null} data=${intent?.data}")
        val handledIncoming = handleIncomingIntent(intent)
        if (handledIncoming) return
        if (BuildConfig.IS_ADMIN_APP) {
            setContentView(FrameLayout(this).apply { id = containerId })
            if (savedInstanceState == null) {
                supportFragmentManager.commit {
                    replace(containerId, WebAppFragment.newInstance())
                }
            }
            return
        }
        val state = sessionRepository.loadState()
        val hasAnyLocalAuth = !state.passengerToken.isNullOrBlank() || state.driverCookieReady || state.adminCookieReady || state.role != null
        val telegramHandoffAlreadyAttempted = sessionRepository.hasTelegramHandoffAttempted()
        if (!hasAnyLocalAuth && !telegramHandoffAlreadyAttempted) {
            Log.d(TAG, "onCreate route -> telegram because local auth is missing")
            openTelegramAndFinish(state.handoffUrl)
            return
        }

        setContentView(FrameLayout(this).apply { id = containerId })
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(containerId, WebAppFragment.newInstance())
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (handleIncomingIntent(intent)) return
    }

    private fun handleIncomingIntent(intent: Intent?): Boolean {
        val data: Uri = intent?.data ?: return false
        Log.d(TAG, "handleIncomingIntent data=$data")
        if (data.scheme != "ride" || data.host != "auth") return false

        val token = data.getQueryParameter("token")
        val initData = data.getQueryParameter("initData")

        if (token.isNullOrBlank() && initData.isNullOrBlank()) {
            showWebAppFragment()
            return true
        }

        lifecycleScope.launch {
            Log.d(TAG, "auth intent tokenPresent=${!token.isNullOrBlank()} initDataPresent=${!initData.isNullOrBlank()}")
            when {
                !initData.isNullOrBlank() -> sessionRepository.loginPassengerWithTelegramInitData(initData)
                !token.isNullOrBlank() -> sessionRepository.storePassengerToken(token)
            }
            showWebAppFragment()
        }
        return true
    }

    private fun showWebAppFragment() {
        setContentView(FrameLayout(this).apply { id = containerId })
        supportFragmentManager.commit {
            replace(containerId, WebAppFragment.newInstance())
        }
    }

    private fun openTelegramAndFinish(handoffUrl: String) {
        sessionRepository.markTelegramHandoffAttempted()
        val target = resolveTelegramHandoffTarget(handoffUrl)
        val httpsUri = buildString {
            append("https://t.me/")
            append(Uri.encode(target.botName))
            append("?start=")
            append(Uri.encode(target.startParam))
        }
        val fallbackUri = buildString {
            append("tg://resolve?domain=")
            append(Uri.encode(target.botName))
            append("&start=")
            append(Uri.encode(target.startParam))
        }
        val telegramIntent = Intent(Intent.ACTION_VIEW, Uri.parse(httpsUri)).apply {
            setPackage("org.telegram.messenger")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        Log.d(TAG, "openTelegramAndFinish https=$httpsUri fallback=$fallbackUri")
        runCatching { startActivity(telegramIntent) }
            .recoverCatching {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUri)).apply {
                    setPackage("org.telegram.messenger")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(fallbackIntent)
            }
            .recoverCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUri)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        overridePendingTransition(0, 0)
        finishAndRemoveTask()
    }
}
