package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "worship_progress", primaryKeys = ["date", "userId"])
data class WorshipProgress(
    val date: String, // format "YYYY-MM-DD"
    val userId: String = "default",
    val fajr: Boolean = false,
    val dhuhr: Boolean = false,
    val asr: Boolean = false,
    val maghrib: Boolean = false,
    val isha: Boolean = false,
    val adhkarMorning: Boolean = false,
    val adhkarEvening: Boolean = false,
    val quranRead: Boolean = false,
    val sunnahPrayed: Boolean = false,
    
    // Fine-grained Sunnah tracking
    val sunnahFajr: Boolean = false,
    val sunnahDuha: Boolean = false,
    val sunnahDhuhrQabli: Boolean = false,
    val sunnahDhuhrBadi: Boolean = false,
    val sunnahMaghrib: Boolean = false,
    val sunnahIsha: Boolean = false,
    val sunnahQiyam: Boolean = false
) {
    fun calculatePercentage(): Float {
        var completed = 0
        val total = 9 // Total 9 core disciplines
        if (fajr) completed++
        if (dhuhr) completed++
        if (asr) completed++
        if (maghrib) completed++
        if (isha) completed++
        if (adhkarMorning) completed++
        if (adhkarEvening) completed++
        if (quranRead) completed++
        // Overall Sunnah was prayed if any fine-grained sunnah is done or the old field was checked
        val isAnySunnahPrayed = sunnahPrayed || sunnahFajr || sunnahDuha || 
                sunnahDhuhrQabli || sunnahDhuhrBadi || sunnahMaghrib || 
                sunnahIsha || sunnahQiyam
        if (isAnySunnahPrayed) completed++
        
        val score = (completed.toFloat() / total.toFloat()) * 100f
        return score.coerceIn(0f, 100f)
    }
}

@Entity(tableName = "custom_reminders")
data class CustomReminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true,
    val soundUri: String = "default", // Custom sound selection
    val repeatDays: String = "السبت, الأحد, الاثنين, الثلاثاء, الأربعاء, الخميس, الجمعة" // comma-separated Days
)

@Entity(tableName = "family_sync")
data class FamilyMember(
    @PrimaryKey val id: Int,
    val name: String,
    val relation: String,
    val avatarUrl: String,
    val progress: Float, // 0 to 100
    val likesCount: Int = 0,
    val likedByMe: Boolean = false,
    val lastWorship: String = ""
)

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val isAutoLocation: Boolean = true,
    val latitude: Double = 21.4225, // Default Mecca
    val longitude: Double = 39.8262,
    val locationName: String = "مكة المكرمة",
    val manualFajr: String = "04:30 ص",
    val manualShurouq: String = "05:45 ص",
    val manualDhuhr: String = "12:20 م",
    val manualAsr: String = "03:40 م",
    val manualMaghrib: String = "06:50 م",
    val manualIsha: String = "08:20 م",
    val isAdhanSoundEnabled: Boolean = true,
    val selectedReciter: String = "الشيخ عبد الباسط",
    
    // Additional Customization Features
    val appFontSizeMultiplier: String = "متوسط", // "صغير", "متوسط", "كبير"
    val isDarkMode: Boolean = true,             // App-wide dark mode
    val quranQuartersCount: Int = 0,             // Quran quarters read
    val favoriteSupplicationsJson: String = "",   // Comma separated supplications

    // Google Sign-In Integration
    val isGoogleSignedIn: Boolean = false,
    val googleUserId: String = "default",
    val googleUserName: String = "",
    val googleUserEmail: String = "",
    val googleUserAvatarUrl: String = "",

    // Family Group Space Integration
    val familyGroupName: String = "",
    val familyGroupInviteCode: String = "",

    // Vibration Customization
    val isVibrationEnabled: Boolean = true,
    val vibrationPattern: String = "نبض الفرح" // "نبض خفيف", "نبض الفرح", "نبض قوي ومستمر", "نبض متقطع"
)

@Dao
interface WorshipDao {
    @Query("SELECT * FROM worship_progress WHERE date = :date AND userId = :userId")
    fun getProgressByDate(date: String, userId: String): Flow<WorshipProgress?>

    @Query("SELECT * FROM worship_progress WHERE date = :date AND userId = :userId")
    suspend fun getProgressByDateImmediate(date: String, userId: String): WorshipProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProgress(progress: WorshipProgress)

    @Query("SELECT * FROM worship_progress WHERE userId = :userId ORDER BY date DESC LIMIT 30")
    fun getWorshipHistory(userId: String): Flow<List<WorshipProgress>>

    // Custom Reminders
    @Query("SELECT * FROM custom_reminders ORDER BY hour, minute ASC")
    fun getAllReminders(): Flow<List<CustomReminder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: CustomReminder)

    @Query("DELETE FROM custom_reminders WHERE id = :id")
    suspend fun deleteReminder(id: Int)

    // Family Space
    @Query("SELECT * FROM family_sync ORDER BY name ASC")
    fun getFamilyMembers(): Flow<List<FamilyMember>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFamilyMembers(members: List<FamilyMember>)

    @Query("UPDATE family_sync SET likesCount = :likes, likedByMe = :liked WHERE id = :id")
    suspend fun updateFamilyLikes(id: Int, likes: Int, liked: Boolean)

    // Settings
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun getSettings(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettingsImmediate(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettings)
}

@Database(
    entities = [WorshipProgress::class, CustomReminder::class, FamilyMember::class, AppSettings::class],
    version = 8,
    exportSchema = false
)
abstract class WorshipDatabase : RoomDatabase() {
    abstract fun dao(): WorshipDao

    companion object {
        @Volatile
        private var INSTANCE: WorshipDatabase? = null

        fun getDatabase(context: Context): WorshipDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WorshipDatabase::class.java,
                    "zad_worship_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class WorshipRepository(private val dao: WorshipDao) {
    fun getProgressByDate(date: String, userId: String): Flow<WorshipProgress?> = dao.getProgressByDate(date, userId)
    suspend fun getProgressByDateImmediate(date: String, userId: String): WorshipProgress? = dao.getProgressByDateImmediate(date, userId)
    suspend fun insertOrUpdateProgress(progress: WorshipProgress) = dao.insertOrUpdateProgress(progress)
    fun getWorshipHistory(userId: String): Flow<List<WorshipProgress>> = dao.getWorshipHistory(userId)

    fun getAllReminders(): Flow<List<CustomReminder>> = dao.getAllReminders()
    suspend fun insertReminder(reminder: CustomReminder) = dao.insertReminder(reminder)
    suspend fun deleteReminder(id: Int) = dao.deleteReminder(id)

    fun getFamilyMembers(): Flow<List<FamilyMember>> = dao.getFamilyMembers()
    suspend fun insertFamilyMembers(members: List<FamilyMember>) = dao.insertFamilyMembers(members)
    suspend fun updateFamilyLikes(id: Int, likes: Int, liked: Boolean) = dao.updateFamilyLikes(id, likes, liked)

    fun getSettings(): Flow<AppSettings?> = dao.getSettings()
    suspend fun getSettingsImmediate(): AppSettings? = dao.getSettingsImmediate()
    suspend fun saveSettings(settings: AppSettings) = dao.saveSettings(settings)
}
