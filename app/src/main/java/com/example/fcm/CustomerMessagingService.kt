package com.example.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.FirestoreProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CustomerMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        runCatching {
            val db = FirestoreProvider.createFirestore(applicationContext)
            db.collection("deviceTokens").document("${uid}_android").set(
                mapOf(
                    "userId" to uid,
                    "role" to "CUSTOMER",
                    "platform" to "android",
                    "token" to token,
                    "updatedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
        }.onFailure { e ->
            Log.w("CustomerFCM", "Failed saving refreshed FCM token", e)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "FixMechanic Update"
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: "Your roadside assistance status has updated."

        val channelId = "fixmechanic_customer_dispatch"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Emergency Dispatch Updates",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
