package com.example.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.R

class WorshipReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Acquire PARTIAL_WAKE_LOCK & SCREEN_BRIGHT_WAKE_LOCK to ensure screen wakes up under lock screen
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val wakeLock = powerManager?.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            "Zad:WorshipWakeLock"
        )
        @Suppress("DEPRECATION")
        val screenWakeLock = powerManager?.newWakeLock(
            android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or android.os.PowerManager.ON_AFTER_RELEASE,
            "Zad:WorshipScreenWakeLock"
        )
        try {
            wakeLock?.acquire(15000) // Keep CPU awake for 15 seconds
            screenWakeLock?.acquire(10000) // Turn screen on for 10 seconds
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val title = intent.getStringExtra("TITLE") ?: "تنبيه العبادات"
        val message = intent.getStringExtra("MESSAGE") ?: "حان وقت العبادة، تقبل الله منك!"
        val soundUri = intent.getStringExtra("SOUND") ?: "default"
        val attachedWorship = intent.getStringExtra("ATTACHED_WORSHIP")
        val notificationId = intent.getIntExtra("ID", System.currentTimeMillis().toInt() and 0xfffffff)

        showNotification(context, title, message, soundUri, attachedWorship, notificationId)
    }

    private fun showNotification(context: Context, title: String, message: String, soundUriStr: String, attachedWorship: String?, notificationId: Int) {
        val soundUri = if (soundUriStr == "default" || soundUriStr.isBlank()) {
            android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        } else {
            android.net.Uri.parse(soundUriStr)
        }

        val channelId = "worship_channel_${soundUri.hashCode()}"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(channelId) == null) {
                val attributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                
                val channel = NotificationChannel(
                    channelId,
                    "تنبيه: زاد العبادة الأكبر",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "تنبيهات العبادات والأذكار والأذان"
                    enableLights(true)
                    enableVibration(true)
                    setSound(soundUri, attributes)
                    setBypassDnd(true)
                    setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                }
                manager.createNotificationChannel(channel)
            }
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOngoing(false)
            .setSound(soundUri)
            .setLocalOnly(true)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)

        // Set Full Screen Intent to wake up and display notification on lock screen
        val fullScreenIntent = Intent(context, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = android.app.PendingIntent.getActivity(
            context,
            notificationId + 2,
            fullScreenIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        builder.setFullScreenIntent(fullScreenPendingIntent, true)

        if (attachedWorship != null) {
            val actionIntent = Intent(context, WorshipActionReceiver::class.java).apply {
                putExtra("WORSHIP_TYPE", attachedWorship)
                putExtra("NOTIFICATION_ID", notificationId)
            }
            val actionPendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                notificationId + 1,
                actionIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "تمت العبادة ✅", actionPendingIntent)
        }

        val notification = builder.build()
        // Removed FLAG_INSISTENT so that the user handles it as clearable and swipable 
        
        val manager = NotificationManagerCompat.from(context)
        try {
            manager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
