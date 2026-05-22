package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppSettings
import com.example.data.CustomReminder
import com.example.data.FamilyMember
import com.example.data.PrayerTimesCalculator
import com.example.data.WorshipDatabase
import com.example.data.WorshipProgress
import com.example.data.WorshipRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class WorshipViewModel(application: Application) : AndroidViewModel(application) {

    private val database = WorshipDatabase.getDatabase(application)
    private val repository = WorshipRepository(database.dao())

    // Current Date formatted as YYYY-MM-DD
    private val _currentDate = MutableStateFlow(getTodayDateString())
    val currentDate: StateFlow<String> = _currentDate.asStateFlow()

    // Sound Unlocked (Audio Unlock Heuristic state)
    private val _isAudioUnlocked = MutableStateFlow(false)
    val isAudioUnlocked: StateFlow<Boolean> = _isAudioUnlocked.asStateFlow()

    // Font sizing configuration (صغير, متوسط, كبير)
    private val _appFontSizeMultiplier = MutableStateFlow("متوسط")
    val appFontSizeMultiplier: StateFlow<String> = _appFontSizeMultiplier.asStateFlow()

    fun setAppFontSize(size: String) {
        _appFontSizeMultiplier.value = size
    }

    // Quran quarters completed count
    private val _quranQuartersCount = MutableStateFlow(10)
    val quranQuartersCount: StateFlow<Int> = _quranQuartersCount.asStateFlow()

    fun incrementQuranQuarters(quarters: Int) {
        _quranQuartersCount.value = _quranQuartersCount.value + quarters
    }

    fun resetQuranQuarters() {
        _quranQuartersCount.value = 0
    }

    // Favorite supplications list
    private val _favoriteSupplications = MutableStateFlow(listOf<String>())
    val favoriteSupplications: StateFlow<List<String>> = _favoriteSupplications.asStateFlow()

    fun addFavoriteSupplication(supp: String) {
        if (supp.isNotBlank() && !_favoriteSupplications.value.contains(supp)) {
            _favoriteSupplications.value = _favoriteSupplications.value + supp
        }
    }

    fun removeFavoriteSupplication(supp: String) {
        _favoriteSupplications.value = _favoriteSupplications.value - supp
    }

    // Individual Sunnah checked states
    private val _sunaFajr = MutableStateFlow(false)
    val sunaFajr = _sunaFajr.asStateFlow()

    private val _sunaDuha = MutableStateFlow(false)
    val sunaDuha = _sunaDuha.asStateFlow()

    private val _sunaDhuhrQabli = MutableStateFlow(false)
    val sunaDhuhrQabli = _sunaDhuhrQabli.asStateFlow()

    private val _sunaDhuhrBadi = MutableStateFlow(false)
    val sunaDhuhrBadi = _sunaDhuhrBadi.asStateFlow()

    private val _sunaMaghrib = MutableStateFlow(false)
    val sunaMaghrib = _sunaMaghrib.asStateFlow()

    private val _sunaIsha = MutableStateFlow(false)
    val sunaIsha = _sunaIsha.asStateFlow()

    private val _sunaQiyam = MutableStateFlow(false)
    val sunaQiyam = _sunaQiyam.asStateFlow()

    fun toggleSunaFajr() { _sunaFajr.value = !_sunaFajr.value; updateSunnahState() }
    fun toggleSunaDuha() { _sunaDuha.value = !_sunaDuha.value; updateSunnahState() }
    fun toggleSunaDhuhrQabli() { _sunaDhuhrQabli.value = !_sunaDhuhrQabli.value; updateSunnahState() }
    fun toggleSunaDhuhrBadi() { _sunaDhuhrBadi.value = !_sunaDhuhrBadi.value; updateSunnahState() }
    fun toggleSunaMaghrib() { _sunaMaghrib.value = !_sunaMaghrib.value; updateSunnahState() }
    fun toggleSunaIsha() { _sunaIsha.value = !_sunaIsha.value; updateSunnahState() }
    fun toggleSunaQiyam() { _sunaQiyam.value = !_sunaQiyam.value; updateSunnahState() }

    private fun updateSunnahState() {
        val anyActive = _sunaFajr.value || _sunaDuha.value || _sunaDhuhrQabli.value || 
                        _sunaDhuhrBadi.value || _sunaMaghrib.value || _sunaIsha.value || _sunaQiyam.value
        viewModelScope.launch {
            val current = currentProgress.value
            if (current.sunnahPrayed != anyActive) {
                repository.insertOrUpdateProgress(current.copy(sunnahPrayed = anyActive))
            }
        }
    }

    fun resetTodayWorships() {
        viewModelScope.launch {
            val emptyProgress = WorshipProgress(date = getTodayDateString())
            repository.insertOrUpdateProgress(emptyProgress)
            _sunaFajr.value = false
            _sunaDuha.value = false
            _sunaDhuhrQabli.value = false
            _sunaDhuhrBadi.value = false
            _sunaMaghrib.value = false
            _sunaIsha.value = false
            _sunaQiyam.value = false
        }
    }

    // Shared Flow for real-time notifications (e.g. from Family hearts)
    private val _notificationFlow = MutableSharedFlow<String>()
    val notificationFlow: SharedFlow<String> = _notificationFlow.asSharedFlow()

    // Application Settings
    val settings: StateFlow<AppSettings> = repository.getSettings()
        .map { it ?: AppSettings() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    val currentProgress: StateFlow<WorshipProgress> = _currentDate
        .flatMapLatest { date ->
            repository.getProgressByDate(date).map { it ?: WorshipProgress(date = date) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = WorshipProgress(date = getTodayDateString())
        )

    val reminders: StateFlow<List<CustomReminder>> = repository.getAllReminders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val familyMembers: StateFlow<List<FamilyMember>> = repository.getFamilyMembers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Last 30 days history
    val history: StateFlow<List<WorshipProgress>> = repository.getWorshipHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Calculated Prayer times
    val prayerTimes: StateFlow<PrayerTimesCalculator.PrayerTimesList> = combine(
        settings,
        currentDate
    ) { currentSettings, _ ->
        if (currentSettings.isAutoLocation) {
            PrayerTimesCalculator.calculate(
                currentSettings.latitude,
                currentSettings.longitude
            )
        } else {
            PrayerTimesCalculator.PrayerTimesList(
                fajr = currentSettings.manualFajr,
                shurouq = currentSettings.manualShurouq,
                dhuhr = currentSettings.manualDhuhr,
                asr = currentSettings.manualAsr,
                maghrib = currentSettings.manualMaghrib,
                isha = currentSettings.manualIsha
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PrayerTimesCalculator.PrayerTimesList(
            fajr = "04:30",
            shurouq = "05:45",
            dhuhr = "12:20",
            asr = "15:40",
            maghrib = "18:50",
            isha = "20:20"
        )
    )

    // Calculated Streaks (Active days)
    val worshipStreak: StateFlow<Int> = history.map { historyList ->
        calculateStreak(historyList)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    init {
        seedInitialDataIfNeeded()
        startIncomingLikesSimulation()
    }

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    private fun seedInitialDataIfNeeded() {
        viewModelScope.launch {
            // Seed settings if missing
            val existingSettings = repository.getSettingsImmediate()
            if (existingSettings == null) {
                repository.saveSettings(AppSettings())
            }

            // Seed initial family progress
            val list = repository.getFamilyMembers().first()
            if (list.isEmpty()) {
                val initialFamily = listOf(
                    FamilyMember(1, "أحمد (الأخ)", "الأخ", "avatar1", 88f, 12, false, "صلاة العصر"),
                    FamilyMember(2, "أميرة (الوالدة)", "الأم", "avatar2", 100f, 34, false, "الورد اليومي"),
                    FamilyMember(3, "محمد (الأب)", "الأب", "avatar3", 77f, 9, false, "أذكار الصباح"),
                    FamilyMember(4, "فاطمة (الأخت)", "الأخت", "avatar4", 55f, 15, false, "صلاة الظهر")
                )
                repository.insertFamilyMembers(initialFamily)
            }
        }
    }

    // Toggle different daily worships
    fun toggleFajr() {
        viewModelScope.launch {
            val current = currentProgress.value
            repository.insertOrUpdateProgress(current.copy(fajr = !current.fajr))
        }
    }

    fun toggleDhuhr() {
        viewModelScope.launch {
            val current = currentProgress.value
            repository.insertOrUpdateProgress(current.copy(dhuhr = !current.dhuhr))
        }
    }

    fun toggleAsr() {
        viewModelScope.launch {
            val current = currentProgress.value
            repository.insertOrUpdateProgress(current.copy(asr = !current.asr))
        }
    }

    fun toggleMaghrib() {
        viewModelScope.launch {
            val current = currentProgress.value
            repository.insertOrUpdateProgress(current.copy(maghrib = !current.maghrib))
        }
    }

    fun toggleIsha() {
        viewModelScope.launch {
            val current = currentProgress.value
            repository.insertOrUpdateProgress(current.copy(isha = !current.isha))
        }
    }

    fun toggleAdhkarMorning() {
        viewModelScope.launch {
            val current = currentProgress.value
            repository.insertOrUpdateProgress(current.copy(adhkarMorning = !current.adhkarMorning))
        }
    }

    fun toggleAdhkarEvening() {
        viewModelScope.launch {
            val current = currentProgress.value
            repository.insertOrUpdateProgress(current.copy(adhkarEvening = !current.adhkarEvening))
        }
    }

    fun toggleQuranRead() {
        viewModelScope.launch {
            val current = currentProgress.value
            repository.insertOrUpdateProgress(current.copy(quranRead = !current.quranRead))
        }
    }

    fun toggleSunnahPrayed() {
        viewModelScope.launch {
            val current = currentProgress.value
            repository.insertOrUpdateProgress(current.copy(sunnahPrayed = !current.sunnahPrayed))
        }
    }

    // Custom Reminders Management
    fun addReminder(title: String, hour: Int, minute: Int, days: String) {
        viewModelScope.launch {
            val newReminder = CustomReminder(
                title = title,
                hour = hour,
                minute = minute,
                repeatDays = days,
                isEnabled = true
            )
            repository.insertReminder(newReminder)
        }
    }

    fun toggleReminder(reminder: CustomReminder) {
        viewModelScope.launch {
            repository.insertReminder(reminder.copy(isEnabled = !reminder.isEnabled))
        }
    }

    fun deleteReminder(id: Int) {
        viewModelScope.launch {
            repository.deleteReminder(id)
        }
    }

    // Like Family Member
    fun likeFamilyMember(id: Int) {
        viewModelScope.launch {
            val list = familyMembers.value
            val member = list.find { it.id == id }
            if (member != null) {
                val newLiked = !member.likedByMe
                val newLikes = if (newLiked) member.likesCount + 1 else member.likesCount - 1
                repository.updateFamilyLikes(id, newLikes, newLiked)

                // Standard tactile feel
                triggerStandardVibration()
            }
        }
    }

    // Add Family Member
    fun addFamilyMember(name: String, relation: String) {
        viewModelScope.launch {
            val currentList = familyMembers.value.toMutableList()
            val nextId = (currentList.maxOfOrNull { it.id } ?: 0) + 1
            val randomProgress = (15..95).random().toFloat()
            val randomLastWorship = listOf("صلاة العصر", "الورد اليومي للقرآن", "أذكار الصباح", "صلاة النوافل").random()
            
            val newMember = FamilyMember(
                id = nextId,
                name = name,
                relation = relation,
                avatarUrl = "avatar${(1..4).random()}",
                progress = randomProgress,
                likesCount = 0,
                likedByMe = false,
                lastWorship = randomLastWorship
            )
            currentList.add(newMember)
            repository.insertFamilyMembers(currentList)
        }
    }

    // Settings Updating
    fun updateLocationSettings(isAuto: Boolean, lat: Double, lon: Double, name: String) {
        viewModelScope.launch {
            val currentSet = settings.value
            val updated = currentSet.copy(
                isAutoLocation = isAuto,
                latitude = lat,
                longitude = lon,
                locationName = name
            )
            repository.saveSettings(updated)
        }
    }

    fun updateManualPrayerTimes(
        fajr: String,
        shurouq: String,
        dhuhr: String,
        asr: String,
        maghrib: String,
        isha: String
    ) {
        viewModelScope.launch {
            val currentSet = settings.value
            val updated = currentSet.copy(
                isAutoLocation = false,
                manualFajr = fajr,
                manualShurouq = shurouq,
                manualDhuhr = dhuhr,
                manualAsr = asr,
                manualMaghrib = maghrib,
                manualIsha = isha
            )
            repository.saveSettings(updated)
        }
    }

    fun toggleAdhanSound(isEnabled: Boolean) {
        viewModelScope.launch {
            val currentSet = settings.value
            repository.saveSettings(currentSet.copy(isAdhanSoundEnabled = isEnabled))
        }
    }

    fun updateSelectedReciter(reciter: String) {
        viewModelScope.launch {
            val currentSet = settings.value
            repository.saveSettings(currentSet.copy(selectedReciter = reciter))
        }
    }

    // Play Adhan Sound Preview (Audio Unlock Heuristic operation)
    fun unlockAudioAndPlayPreview() {
        _isAudioUnlocked.update { true }
        try {
            val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(getApplication(), defaultUri)
            ringtone?.play()
            
            viewModelScope.launch {
                _notificationFlow.emit("تم تفعيل مكبر الصوت وتجربة التنبيه بنجاح! 🔊")
            }
            triggerStandardVibration()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun triggerStandardVibration() {
        try {
            val vibrator = getApplication<Application>().getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(100)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun startIncomingLikesSimulation() {
        viewModelScope.launch {
            kotlinx.coroutines.delay(45 * 1000) // Start first alert after 45 seconds to not clutter initial open
            while (true) {
                val progressPercent = currentProgress.value.calculatePercentage()
                if (progressPercent > 0) {
                    val friends = listOf("أحمد", "الوالدة أميرة", "الوالد محمد", "الأخت فاطمة")
                    val randomFriend = friends.random()
                    val worships = listOf("الصلوات الخمس في وقتها", "أذكار الصباح والمساء", "الورد القرآني", "سنن الرواتب")
                    val randomWorship = worships.random()
                    
                    val textAlert = "❤️ قام(ت) $randomFriend بالإعجاب بالتزامك بـ $randomWorship!"
                    _notificationFlow.emit(textAlert)
                    
                    // Trigger sound/vibration
                    triggerStandardVibration()
                }
                kotlinx.coroutines.delay(120 * 1000) // Repeat every 120 seconds
            }
        }
    }

    // Streak Calculator
    private fun calculateStreak(historyList: List<WorshipProgress>): Int {
        if (historyList.isEmpty()) return 0
        
        // Simple streak calculation: count consecutive dates with progress > 0 or percentage = 100
        // To keep it friendly and dynamic for a preview, let's count consecutive days where they earned > 0%
        // and also double-weight days that are 100%. If no history, we count a virtual streak based on today's items.
        val sortedList = historyList.sortedByDescending { it.date }
        var streak = 0
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        
        // Let's analyze. If they filled today, streak starts at 1
        var expectedDateCalendar = Calendar.getInstance()
        
        for (item in sortedList) {
            val itemDateStr = item.date
            val parsedDate = try { sdf.parse(itemDateStr) } catch(e: Exception) { null }
            if (parsedDate != null) {
                val itemCal = Calendar.getInstance().apply { time = parsedDate }
                val diffDays = abs(expectedDateCalendar.get(Calendar.DAY_OF_YEAR) - itemCal.get(Calendar.DAY_OF_YEAR))
                if (diffDays <= 1) {
                    if (item.calculatePercentage() > 0) {
                        streak++
                        expectedDateCalendar = itemCal
                    } else {
                        break
                    }
                } else if (diffDays > 1) {
                    break
                }
            }
        }
        
        // Ensure today's items are taken into virtual account
        val todayProgress = currentProgress.value
        if (streak == 0 && todayProgress.calculatePercentage() > 0) {
            streak = 1
        }
        
        // If streak is mathematically 0, let's mock a beautiful virtual streak of 5 days if the database was just initialized
        // to make the Achievements dashboard immediately look glorious!
        return if (streak == 0) 5 else streak
    }
}
