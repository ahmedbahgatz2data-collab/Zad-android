package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppSettings
import com.example.data.CustomReminder
import com.example.data.FamilyMember
import com.example.data.WorshipProgress
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.WorshipViewModel
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val viewModel: WorshipViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Force RTL standard Arabic Layout
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        MainZadContainer(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MainZadContainer(
    viewModel: WorshipViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf("today") } // today, family, stats, settings

    // Observe state streams
    val progress by viewModel.currentProgress.collectAsStateWithLifecycle()
    val prayerTimes by viewModel.prayerTimes.collectAsStateWithLifecycle()
    val familyList by viewModel.familyMembers.collectAsStateWithLifecycle()
    val remindersList by viewModel.reminders.collectAsStateWithLifecycle()
    val settingsState by viewModel.settings.collectAsStateWithLifecycle()
    val streakCount by viewModel.worshipStreak.collectAsStateWithLifecycle()
    val isAudioUnlocked by viewModel.isAudioUnlocked.collectAsStateWithLifecycle()

    // Host reactive toasts for family likes
    LaunchedEffect(key1 = true) {
        viewModel.notificationFlow.collectLatest { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (MaterialTheme.colorScheme.background == Color(0xFF061512) || MaterialTheme.colorScheme.background == Color(0xFF090D16)) {
                        listOf(Color(0xFF0D2D26), Color(0xFF061512))
                    } else {
                        listOf(Color(0xFFECFDF5), Color(0xFFF0FDF4))
                    }
                )
            )
    ) {
        // App Header: Islamic Date & Dynamic clock widget
        ZadHeader(settings = settingsState)

        // Screen Content with Animated Content transitions
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "tab_transitions"
            ) { tab ->
                when (tab) {
                    "today" -> TodayScreen(
                        progress = progress,
                        prayerTimes = prayerTimes,
                        settings = settingsState,
                        isAudioUnlocked = isAudioUnlocked,
                        viewModel = viewModel
                    )
                    "family" -> FamilyScreen(
                        familyList = familyList,
                        viewModel = viewModel
                    )
                    "stats" -> StatsScreen(
                        streak = streakCount,
                        history = listOf(70, 85, 90, 60, 100, 80, 95) // Completion % for the last 7 days
                    )
                    "settings" -> SettingsScreen(
                        settings = settingsState,
                        reminders = remindersList,
                        viewModel = viewModel
                    )
                }
            }
        }

        // Bottom Navigation Bar
        ZadBottomNavBar(
            selectedTab = currentTab,
            onTabSelected = { currentTab = it }
        )
    }
}

@Composable
fun ZadHeader(settings: AppSettings) {
    val sdfTime = SimpleDateFormat("hh:mm a", Locale("ar"))
    val sdfDateStr = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("ar"))
    var currentTimeStr by remember { mutableStateOf(sdfTime.format(Date())) }
    val todayDateStr = sdfDateStr.format(Date())

    // Update clock every minute
    LaunchedEffect(key1 = true) {
        while (true) {
            currentTimeStr = sdfTime.format(Date())
            kotlinx.coroutines.delay(1000 * 30) // 30 sec
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (MaterialTheme.colorScheme.background == Color(0xFF061512)) {
                Color.White.copy(alpha = 0.05f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
            }
        ),
        border = if (MaterialTheme.colorScheme.background == Color(0xFF061512)) {
            BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "منصة زاد الروحية",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "الموقع",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = settings.locationName + if (settings.isAutoLocation) " (تلقائي)" else " (يدوي)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = currentTimeStr,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = todayDateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun TodayScreen(
    progress: WorshipProgress,
    prayerTimes: com.example.data.PrayerTimesCalculator.PrayerTimesList,
    settings: AppSettings,
    isAudioUnlocked: Boolean,
    viewModel: WorshipViewModel
) {
    val percentage = progress.calculatePercentage()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // worship ledger percentage and message
        item {
            WorshipLedgerCard(percentage = percentage, progress = progress)
        }

        // AutoPlay Constraints Audio Banner
        if (!isAudioUnlocked) {
            item {
                AudioUnlockWidget(onUnlock = { viewModel.unlockAudioAndPlayPreview() })
            }
        }

        // Today Prayer Radar Group
        item {
            PrayerTimesGrid(prayerTimes = prayerTimes, settings = settings)
        }

        // Individual Worship Checklists
        item {
            Text(
                text = "أوراد وعبادات اليوم:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        item {
            WorshipCheckItem(
                title = "صلاة الفجر في وقتها",
                isChecked = progress.fajr,
                onCheckedChange = { viewModel.toggleFajr() },
                icon = "🕌"
            )
        }
        item {
            WorshipCheckItem(
                title = "صلاة الظهر في وقتها",
                isChecked = progress.dhuhr,
                onCheckedChange = { viewModel.toggleDhuhr() },
                icon = "🕌"
            )
        }
        item {
            WorshipCheckItem(
                title = "صلاة العصر في وقتها",
                isChecked = progress.asr,
                onCheckedChange = { viewModel.toggleAsr() },
                icon = "🕌"
            )
        }
        item {
            WorshipCheckItem(
                title = "صلاة المغرب في وقتها",
                isChecked = progress.maghrib,
                onCheckedChange = { viewModel.toggleMaghrib() },
                icon = "🕌"
            )
        }
        item {
            WorshipCheckItem(
                title = "صلاة العشاء في وقتها",
                isChecked = progress.isha,
                onCheckedChange = { viewModel.toggleIsha() },
                icon = "🕌"
            )
        }
        item {
            WorshipCheckItem(
                title = "الورد اليومي للأذكار (الصباحية)",
                isChecked = progress.adhkarMorning,
                onCheckedChange = { viewModel.toggleAdhkarMorning() },
                icon = "☀️"
            )
        }
        item {
            WorshipCheckItem(
                title = "الورد اليومي للأذكار (المسائية)",
                isChecked = progress.adhkarEvening,
                onCheckedChange = { viewModel.toggleAdhkarEvening() },
                icon = "🌙"
            )
        }
        item {
            WorshipCheckItem(
                title = "الورد القرآني (قراءة كافية)",
                isChecked = progress.quranRead,
                onCheckedChange = { viewModel.toggleQuranRead() },
                icon = "📖"
            )
        }
        item {
            WorshipCheckItem(
                title = "النوافل وسنن الرواتب اليومية",
                isChecked = progress.sunnahPrayed,
                onCheckedChange = { viewModel.toggleSunnahPrayed() },
                icon = "✨"
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun WorshipLedgerCard(percentage: Float, progress: WorshipProgress) {
    // Determine dynamic message
    val motivateText = when {
        percentage == 100f -> "ما شاء الله تبارك الله! لقد أكملت كامل أورادك اليوم بامتياز! 👑"
        percentage >= 70f -> "رائع جداً! لم يتبقَ إلا القليل لإغلاق دائرة اليوم بالكامل! 🌟"
        percentage >= 40f -> "بداية مباركة، تتبع واقضِ النوافل والصلوات الباقية لتكتمل المنارة ✨"
        percentage > 0f -> "خطوة الالتزام الأولى تبدأ بنية صادقة وبسيطة، احرص على إكمال زادك الروحي 💪"
        else -> "لم تسجل أي عبادة اليوم بعد. ابدأ الآن وتزوّد بالخيرات لطمأنينة قلبك 💖"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = if (MaterialTheme.colorScheme.background == Color(0xFF061512)) {
            BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        } else {
            null
        },
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (MaterialTheme.colorScheme.background == Color(0xFF061512)) {
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF0D2D26).copy(alpha = 0.4f),
                                Color(0xFF022C22).copy(alpha = 0.4f)
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        )
                    }
                )
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(80.dp)
            ) {
                // Background Track ring
                CircularProgressIndicator(
                    progress = { 1f },
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    strokeWidth = 8.dp,
                    modifier = Modifier.fillMaxSize()
                )
                // Active completion indicator
                CircularProgressIndicator(
                    progress = { percentage / 100f },
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 8.dp,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = "${percentage.toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(18.dp))

            Column {
                Text(
                    text = "لوحة تتبع عبادتك",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = motivateText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun AudioUnlockWidget(onUnlock: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF061512)
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color.Transparent else Color(0xFFFEF3C7)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) Color(0xFFF97316).copy(alpha = 0.3f) else Color(0xFFF59E0B)
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isDark) {
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFF97316).copy(alpha = 0.2f),
                                Color(0xFFF59E0B).copy(alpha = 0.2f)
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFEF3C7),
                                Color(0xFFFEF3C7)
                            )
                        )
                    }
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "🔊 تفعيل مكبر الصوت للاستماع للأذان",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFFFFEDD5) else Color(0xFF92400E)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "يتطلب المتصفح تفعيلاً أولياً لتخطي حظر التشغيل الصوتي التلقائي وسماع التنبيه في موعده.",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Color(0xFFFFEDD5).copy(alpha = 0.8f) else Color(0xFFB45309),
                    lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onUnlock,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF97316)
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("audio_unlock_btn")
            ) {
                Text("تفعيل الآن", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PrayerTimesGrid(
    prayerTimes: com.example.data.PrayerTimesCalculator.PrayerTimesList,
    settings: AppSettings
) {
    val items = listOf(
        Triple("الفجر", prayerTimes.fajr, "🌅"),
        Triple("الإشراق", prayerTimes.shurouq, "☀️"),
        Triple("الظهر", prayerTimes.dhuhr, "🌤️"),
        Triple("العصر", prayerTimes.asr, "🌥️"),
        Triple("المغرب", prayerTimes.maghrib, "🌇"),
        Triple("العشاء", prayerTimes.isha, "🌌")
    )

    // Calculate current next active prayer for styling highlighting
    val sdf = SimpleDateFormat("HH:mm", Locale.US)
    val curTimeStr = sdf.format(Date())
    
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF061512)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
        ),
        border = if (isDark) BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)) else null,
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "مواقيت الصلوات والقبلة اليوم:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp) // Bound height
            ) {
                items(items) { p ->
                    val isNext = curTimeStr < p.second
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) {
                                if (isNext) Color(0xFF10B981).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f)
                            } else {
                                if (isNext) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            }
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isDark) {
                                if (isNext) Color(0xFF10B981).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f)
                            } else {
                                if (isNext) MaterialTheme.colorScheme.primary else Color.Transparent
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                this.alpha = if (isNext || !isDark) 1f else 0.6f
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(10.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = p.third, fontSize = 20.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = p.first,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = p.second,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🧭 زاوية اتجاه القبلة: 21.4° (شمال شرق نحو الكعبة المشرفة)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun WorshipCheckItem(
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: String
) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF061512)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) },
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) {
                if (isChecked) Color(0xFF10B981).copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f)
            } else {
                if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
            }
        ),
        border = if (isDark) {
            BorderStroke(
                width = 1.dp,
                color = if (isChecked) Color(0xFF10B981).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f)
            )
        } else {
            null
        },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 20.sp, modifier = Modifier.padding(end = 12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )

            Checkbox(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.testTag("chk_${title.replace(" ", "_")}")
            )
        }
    }
}

@Composable
fun FamilyScreen(
    familyList: List<FamilyMember>,
    viewModel: WorshipViewModel
) {
    var showJoinDialog by remember { mutableStateOf(false) }
    var newMemberName by remember { mutableStateOf("") }
    var newMemberRelation by remember { mutableStateOf("صديق(ة)") }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                val isDark = MaterialTheme.colorScheme.background == Color(0xFF061512)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF10B981).copy(alpha = 0.08f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                    ),
                    border = if (isDark) BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.2f)) else null,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "👥 شبكة \"الأهل والأصدقاء\"",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "شارِك التزامك اليومي وتسابق في الخيرات، تواصل مع عائلتك وشجّعهم بإرسال التفاعلات والقلوب اللحظية الروحية.",
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "جدول تقدم أفراد العائلة:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Button(
                        onClick = { showJoinDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إضافة فرد", fontSize = 12.sp)
                    }
                }
            }

            if (familyList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("لا يوجد أفراد آخرين في دائرتك الحالية.", color = Color.Gray)
                    }
                }
            } else {
                items(familyList) { member ->
                    FamilyMemberItemCard(member = member, onLikeClick = { viewModel.likeFamilyMember(member.id) })
                }
            }

            item { Spacer(modifier = Modifier.height(30.dp)) }
        }

        // Add Member Dialog
        if (showJoinDialog) {
            AlertDialog(
                onDismissRequest = { showJoinDialog = false },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newMemberName.isNotBlank()) {
                                viewModel.addFamilyMember(
                                    name = newMemberName,
                                    relation = newMemberRelation
                                )
                                showJoinDialog = false
                                newMemberName = ""
                            }
                        }
                    ) {
                        Text("إضافة")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showJoinDialog = false }) {
                        Text("إلغاء")
                    }
                },
                title = { Text("إضافة فرد لدائرة الأهل", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = newMemberName,
                            onValueChange = { newMemberName = it },
                            label = { Text("الاسم") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = newMemberRelation,
                            onValueChange = { newMemberRelation = it },
                            label = { Text("صلة القرابة (مثال: الأخ، ابن العم، الصديق)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun FamilyMemberItemCard(
    member: FamilyMember,
    onLikeClick: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF061512)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
        ),
        border = if (isDark) BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)) else null,
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = member.name.take(1),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = member.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = member.relation,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }

                // Likes Hearts Trigger
                IconButton(
                    onClick = onLikeClick,
                    modifier = Modifier.testTag("like_member_${member.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (member.likedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "تفاعل",
                            tint = if (member.likedByMe) Color.Red else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = member.likesCount.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (member.likedByMe) Color.Red else Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Bar representing daily worship ledger
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الإنجاز اليومي: ",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.width(6.dp))

                LinearProgressIndicator(
                    progress = { member.progress / 100f },
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "${member.progress.toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (member.lastWorship.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "آخر عمل مسجل: ${member.lastWorship}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun StatsScreen(
    streak: Int,
    history: List<Int> // Percentage completions last 7 days
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Streaks ring Card
        item {
            val isDark = MaterialTheme.colorScheme.background == Color(0xFF061512)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF10B981).copy(alpha = 0.08f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                border = if (isDark) BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.2f)) else null,
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(Color(0xFFFEF3C7), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🔥", fontSize = 28.sp)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = "الالتزام المستمر: $streak أيام متتالية!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "واصِل السعي والعبادة لتثبيت العادات الروحية وجعل اليوم شاهداً لك عند ربك.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Custom canvas charts representing last week completion
        item {
            val isDark = MaterialTheme.colorScheme.background == Color(0xFF061512)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ),
                border = if (isDark) BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)) else null,
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "📈 إحصائيات الالتزام بالأوراد والصلوات الأسبوعية:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Draw elegant custom Canvas diagram
                    WorshipBarChart(data = history)

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "يُمثل الرسم البياني نسبة إنجازك اليومي للأيام السبعة الماضية من الأوراد والعبادات الفردية.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Unlocked Badges list
        item {
            Text(
                text = "الأوسمة والإنجازات الروحية المحرزة:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        item {
            BadgeItem(
                title = "حارس الفجر الأول",
                desc = "الالتزام بصلاة الفجر في وقتها لـ 5 أيام متتالية",
                emoji = "🎖️",
                isUnlocked = true
            )
        }
        item {
            BadgeItem(
                title = "الذاكر الشاكر",
                desc = "إتمام أوراد الأذكار اليومية الصباحية والمسائية دون انقطاع",
                emoji = "⭐",
                isUnlocked = true
            )
        }
        item {
            BadgeItem(
                title = "صديق القرآن",
                desc = "قراءة ورد قرآني مستمر لأسبوع متتالٍ",
                emoji = "🕌",
                isUnlocked = false
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun WorshipBarChart(data: List<Int>) {
    val barColor = MaterialTheme.colorScheme.primary
    val daysOfWeek = listOf("السبت", "الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة")

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(top = 16.dp, bottom = 10.dp)
    ) {
        val widthSpace = size.width / 7f
        val maxBarHeight = size.height - 30.dp.toPx()

        for (i in data.indices) {
            val progressPercent = data[i]
            val barHeight = (progressPercent / 100f) * maxBarHeight
            val xOffset = i * widthSpace + (widthSpace * 0.25f)
            val yOffset = size.height - 30.dp.toPx() - barHeight

            // Draw Bar
            drawRect(
                color = barColor.copy(alpha = 0.85f),
                topLeft = Offset(xOffset, yOffset),
                size = Size(widthSpace * 0.5f, barHeight),
            )

            // Draw Bar Header percentage text could be simulated
        }

        // Draw horizontal baseline
        drawLine(
            color = Color.LightGray,
            start = Offset(0f, size.height - 30.dp.toPx()),
            end = Offset(size.width, size.height - 30.dp.toPx()),
            strokeWidth = 2f
        )
    }

    // Days labeling text Row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        for (day in daysOfWeek) {
            Text(
                text = day,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun BadgeItem(
    title: String,
    desc: String,
    emoji: String,
    isUnlocked: Boolean
) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF061512)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) {
                if (isUnlocked) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.02f)
            } else {
                if (isUnlocked) MaterialTheme.colorScheme.surface.copy(alpha = 0.8f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            }
        ),
        border = if (isDark) {
            BorderStroke(
                width = 1.dp,
                color = if (isUnlocked) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.04f)
            )
        } else {
            null
        },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (isUnlocked) Color(0xFFD1FAE5) else Color.LightGray.copy(alpha = 0.3f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = if (isUnlocked) emoji else "🔒", fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else Color.Gray
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            if (isUnlocked) {
                Text(
                    text = "مكتمل",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color(0xFFD1FAE5), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            } else {
                Text(
                    text = "مغلق",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier
                        .background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    reminders: List<CustomReminder>,
    viewModel: WorshipViewModel
) {
    var showAddReminderDialog by remember { mutableStateOf(false) }

    // Forms for setting inputs
    var reminderTitle by remember { mutableStateOf("") }
    var reminderHour by remember { mutableStateOf(19) }
    var reminderMinute by remember { mutableStateOf(0) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Location auto vs manual card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⚙️ ضبط الموقع ومواقيت الأذان",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تحديد أوتوماتيكي للموقع (GPS):",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Switch(
                            checked = settings.isAutoLocation,
                            onCheckedChange = { isAuto ->
                                if (isAuto) {
                                    // Use default Mecca
                                    viewModel.updateLocationSettings(true, 21.4225, 39.8262, "مكة المكرمة")
                                } else {
                                    viewModel.updateLocationSettings(false, 30.0444, 31.2357, "القاهرة")
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    if (!settings.isAutoLocation) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "نمط التعديل اليدوي مفعل (القاهرة). يمكنك تصحيح المواقيت لتلائم المسجد المجاور لك بدقة:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            lineHeight = 16.sp
                        )

                        // Manual offset times input display setup
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = settings.manualFajr,
                                onValueChange = {
                                    viewModel.updateManualPrayerTimes(
                                        it, settings.manualShurouq, settings.manualDhuhr,
                                        settings.manualAsr, settings.manualMaghrib, settings.manualIsha
                                    )
                                },
                                label = { Text("الفجر", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = settings.manualDhuhr,
                                onValueChange = {
                                    viewModel.updateManualPrayerTimes(
                                        settings.manualFajr, settings.manualShurouq, it,
                                        settings.manualAsr, settings.manualMaghrib, settings.manualIsha
                                    )
                                },
                                label = { Text("الظهر", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = settings.manualAsr,
                                onValueChange = {
                                    viewModel.updateManualPrayerTimes(
                                        settings.manualFajr, settings.manualShurouq, settings.manualDhuhr,
                                        it, settings.manualMaghrib, settings.manualIsha
                                    )
                                },
                                label = { Text("العصر", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Custom Reminders Hub (CRUD Interface)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔔 بوابة إدارة التنبيهات والأذكار المخصصة",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(
                            onClick = { showAddReminderDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("add_reminder_btn")
                        ) {
                            Text("إضافة تذكير", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "ضبط أوقات إضافية للتنبيه بالأوراد، قيام الليل، السنن الرواتب، أو صيام التطوع مع تكرار مرن:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (reminders.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("لا يوجد تنبيهات مخصصة مسجلة حالياً.", color = Color.Gray, fontSize = 12.sp)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            reminders.forEach { r ->
                                ReminderItemRow(
                                    reminder = r,
                                    onToggle = { viewModel.toggleReminder(r) },
                                    onDelete = { viewModel.deleteReminder(r.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Preferences Sound Settings Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📻 إعدادات صوت الأذان",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تشغيل صوت الأذان كاملاً عند موعد الصلاة:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Switch(
                            checked = settings.isAdhanSoundEnabled,
                            onCheckedChange = { viewModel.toggleAdhanSound(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "القارئ النشط للتنبيه: ${settings.selectedReciter}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(40.dp)) }
    }

    // New Custom Reminder Dialog
    if (showAddReminderDialog) {
        AlertDialog(
            onDismissRequest = { showAddReminderDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        if (reminderTitle.isNotBlank()) {
                            viewModel.addReminder(
                                title = reminderTitle,
                                hour = reminderHour,
                                minute = reminderMinute,
                                days = "يومي"
                            )
                            showAddReminderDialog = false
                            reminderTitle = ""
                        }
                    }
                ) {
                    Text("إضافة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddReminderDialog = false }) {
                    Text("إلغاء")
                }
            },
            title = { Text("إضافة تنبيه مخصص جديد", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = reminderTitle,
                        onValueChange = { reminderTitle = it },
                        label = { Text("عنوان التنبيه (مثال: أذكار ما بعد الصلاة)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("موعد التنبيه (ساعة : دقيقة):", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = reminderHour.toString(),
                            onValueChange = { reminderHour = it.toIntOrNull() ?: 12 },
                            label = { Text("الساعة (0-23)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = reminderMinute.toString(),
                            onValueChange = { reminderMinute = it.toIntOrNull() ?: 0 },
                            label = { Text("الدقيقة (0-59)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        )
    }
}

@Composable
fun ReminderItemRow(
    reminder: CustomReminder,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = String.format(Locale.US, "%02d:%02d (%s)", reminder.hour, reminder.minute, reminder.repeatDays),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = reminder.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("toggle_rem_${reminder.id}")
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_rem_${reminder.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف التنبيه",
                        tint = Color.Red.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ZadBottomNavBar(
    selectedTab: String,
    onTabSelected: (String) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF061512)
    Column {
        if (isDark) {
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.05f),
                thickness = 0.8.dp
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars), // Strictly avoid gesture overlap
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.Transparent
        ) {
            NavigationBarItem(
                selected = selectedTab == "today",
                onClick = { onTabSelected("today") },
                icon = { Icon(Icons.Default.Home, contentDescription = "اليوم") },
                label = { Text("اليوم", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.testTag("tab_today")
            )

            NavigationBarItem(
                selected = selectedTab == "family",
                onClick = { onTabSelected("family") },
                icon = { Icon(Icons.Default.Person, contentDescription = "الأهل") },
                label = { Text("الأهل", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.testTag("tab_family")
            )

            NavigationBarItem(
                selected = selectedTab == "stats",
                onClick = { onTabSelected("stats") },
                icon = { Icon(Icons.Default.Star, contentDescription = "إنجازاتي") },
                label = { Text("إنجازاتي", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.testTag("tab_stats")
            )

            NavigationBarItem(
                selected = selectedTab == "settings",
                onClick = { onTabSelected("settings") },
                icon = { Icon(Icons.Default.Settings, contentDescription = "الإعدادات") },
                label = { Text("الإعدادات", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.testTag("tab_settings")
            )
        }
    }
}
}
