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
        val title = intent.getStringExtra("TITLE") ?: "تنبيه العبادات"
        val message = intent.getStringExtra("MESSAGE") ?: "حان وقت العبادة، تقبل الله منك!"
        val soundUri = intent.getStringExtra("SOUND") ?: "default"
        val attachedWorship = intent.getStringExtra("ATTACHED_WORSHIP")

        showNotification(context, title, message, soundUri, attachedWorship)
    }

    private fun showNotification(context: Context, title: String, message: String, soundUriStr: String, attachedWorship: String?) {
        val soundUri = if (soundUriStr == "default" || soundUriStr.isBlank()) {
            android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        } else {
            android.net.Uri.parse(soundUriStr)
        }

        val notificationId = System.currentTimeMillis().toInt()
        val channelId = "worship_channel_${soundUri.hashCode()}"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(channelId) == null) {
                val attributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                
                val channel = NotificationChannel(
                    channelId,
                    "تنبيه: زاد العبادة",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "تنبيهات العبادات والأذكار"
                    enableLights(true)
                    enableVibration(true)
                    setSound(soundUri, attributes)
                }
                manager.createNotificationChannel(channel)
            }
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setSound(soundUri)

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

        val manager = NotificationManagerCompat.from(context)
        try {
            manager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
