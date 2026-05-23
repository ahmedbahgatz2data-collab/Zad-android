package com.example.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.data.WorshipDatabase
import com.example.data.WorshipRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WorshipActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val worshipType = intent.getStringExtra("WORSHIP_TYPE") ?: return
        val notificationId = intent.getIntExtra("NOTIFICATION_ID", -1)

        val database = WorshipDatabase.getDatabase(context)
        val repository = WorshipRepository(database.dao())
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val progress = repository.getProgressByDateImmediate(today, "default") 
                    ?: com.example.data.WorshipProgress(date = today, userId = "default")
                
                val updatedProgress = when (worshipType) {
                    "الفجر" -> progress.copy(fajr = true)
                    "الظهر" -> progress.copy(dhuhr = true)
                    "العصر" -> progress.copy(asr = true)
                    "المغرب" -> progress.copy(maghrib = true)
                    "العشاء" -> progress.copy(isha = true)
                    "أذكار الصباح" -> progress.copy(adhkarMorning = true)
                    "أذكار المساء" -> progress.copy(adhkarEvening = true)
                    "ورد القرآن" -> progress.copy(quranRead = true)
                    else -> progress
                }
                
                repository.insertOrUpdateProgress(updatedProgress)
                
                if (notificationId != -1) {
                    NotificationManagerCompat.from(context).cancel(notificationId)
                }
                
                // Stop the sound
                val stopSoundIntent = Intent(context, WorshipReceiver::class.java).apply {
                    action = "STOP_SOUND"
                }
                context.sendBroadcast(stopSoundIntent)
            } catch (e: Exception) {
                Log.e("WorshipActionReceiver", "Error updating progress: ${e.message}")
            }
        }
    }
}
