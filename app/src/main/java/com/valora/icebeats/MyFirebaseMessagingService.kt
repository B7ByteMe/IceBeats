package com.valora.icebeats

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val from = remoteMessage.from ?: ""
        if (from.startsWith("/topics/")) {
            val topic = from.removePrefix("/topics/")
            val rawTopic = topic.removePrefix("v").removeSuffix("-nightly")
            // Check if this is a version-based topic (e.g. 6.1.1, 6.1.2)
            if (rawTopic.matches(Regex("""^\d+\.\d+.*""")) && rawTopic != BuildConfig.VERSION_NAME) {
                // Device is on a different/newer version, so unsubscribe immediately from this outdated topic
                com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
                android.util.Log.w("FCM", "Unsubscribed and dropped message for obsolete version topic: $topic")
                return
            }
        }

        val title = remoteMessage.notification?.title ?: "IceBeats"
        val body = remoteMessage.notification?.body ?: "New message"

        showNotification(title, body)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Optional: send token to your server
    }

    private fun showNotification(title: String, message: String) {

        val channelId = "airbeats_channel"
        val channelName = "IceBeats Notifications"

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 🔔 Create channel (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // 🔥 Open app when clicked
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        // 🔔 Build notification
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.notification_on) // use your existing icon
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // 🔥 Show notification
        notificationManager.notify(Random.nextInt(), notification)
    }
}
