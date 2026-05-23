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

        showNotification(context, title, message, soundUri)
    }

    private fun showNotification(context: Context, title: String, message: String, soundUriStr: String) {
        val soundUri = if (soundUriStr == "default" || soundUriStr.isBlank()) {
            android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        } else {
            android.net.Uri.parse(soundUriStr)
        }

        // Generate a unique channel ID based on the sound to ensure custom sounds work on Android 8.0+
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
                    "تنبيه: $title",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "تنبيات مخصصة بفضل الله"
                    enableLights(true)
                    enableVibration(true)
                    setSound(soundUri, attributes)
                }
                manager.createNotificationChannel(channel)
            }
        }

        // Create notification builder
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setSound(soundUri)

        val manager = NotificationManagerCompat.from(context)
        try {
            manager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
