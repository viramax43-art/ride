package com.rideminiapp.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rideminiapp.MainActivity
import com.rideminiapp.R
import com.rideminiapp.data.repository.SessionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RideFirebaseMessagingService : FirebaseMessagingService() {
    @Inject lateinit var sessionRepository: SessionRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            runCatching { sessionRepository.registerPushToken(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val type = data["type"] ?: "general"
        val title = data["title"] ?: message.notification?.title ?: getString(R.string.app_name)
        val body = data["body"] ?: message.notification?.body ?: return

        ensureChannel(type)

        val deepLink = data["deep_link"] ?: "ride://auth"
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(deepLink)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            setClass(this@RideFirebaseMessagingService, MainActivity::class.java)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, channelForType(type))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(priorityForType(type))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationId = data["ride_id"]?.hashCode() ?: System.currentTimeMillis().toInt()
        NotificationManagerCompat.from(this).notify(notificationId, notification)
    }

    private fun ensureChannel(type: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            channelForType(type),
            nameForType(type),
            importanceForType(type),
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun channelForType(type: String) = when (type) {
        "new_ride" -> "new_rides"
        "ride_status" -> "ride_statuses"
        else -> "general"
    }

    private fun nameForType(type: String) = when (type) {
        "new_ride" -> "New rides"
        "ride_status" -> "Ride statuses"
        else -> "General"
    }

    private fun importanceForType(type: String) = when (type) {
        "new_ride" -> NotificationManager.IMPORTANCE_HIGH
        "ride_status" -> NotificationManager.IMPORTANCE_DEFAULT
        else -> NotificationManager.IMPORTANCE_LOW
    }

    private fun priorityForType(type: String) = when (type) {
        "new_ride" -> NotificationCompat.PRIORITY_HIGH
        "ride_status" -> NotificationCompat.PRIORITY_DEFAULT
        else -> NotificationCompat.PRIORITY_LOW
    }
}
