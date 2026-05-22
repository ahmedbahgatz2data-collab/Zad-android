package com.example

import android.os.Bundle
import android.widget.Toast
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.zIndex
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
            val settingsState by viewModel.settings.collectAsStateWithLifecycle()
            MyApplicationTheme(darkTheme = settingsState.isDarkMode) {
                val fontScale = when (settingsState.appFontSizeMultiplier) {
                    "صغير" -> 0.85f
                    "كبير" -> 1.25f
                    else -> 1.0f
                }
                val density = androidx.compose.ui.platform.LocalDensity.current
                val customDensity = androidx.compose.ui.unit.Density(
                    density = density.density,
                    fontScale = density.fontScale * fontScale
                )
                // Force RTL standard Arabic Layout and dynamic scale
                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                    androidx.compose.ui.platform.LocalDensity provides customDensity
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        if (!settingsState.isGoogleSignedIn) {
                            AppWideWelcomeLoginScreen(
                                viewModel = viewModel,
                                settings = settingsState,
                                modifier = Modifier.padding(innerPadding)
                            )
                        } else {
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
    var activeBannerMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(key1 = true) {
        viewModel.notificationFlow.collectLatest { msg ->
            activeBannerMessage = msg
        }
    }

    LaunchedEffect(activeBannerMessage) {
        if (activeBannerMessage != null) {
            kotlinx.coroutines.delay(4500)
            activeBannerMessage = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (settingsState.isDarkMode) {
                            listOf(Color(0xFF0D2D26), Color(0xFF061512))
                        } else {
                            listOf(Color(0xFFECFDF5), Color(0xFFF0FDF4))
                        }
                    )
                )
        ) {
            // App Header: Islamic Date & Dynamic clock widget
            ZadHeader(
                settings = settingsState,
                viewModel = viewModel,
                onTabSelected = { currentTab = it }
            )

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
                        "qibla" -> QiblaScreen(settings = settingsState)
                        "family" -> FamilyScreen(
                            familyList = familyList,
                            viewModel = viewModel
                        )
                        "stats" -> StatsScreen(
                            streak = streakCount,
                            history = listOf(70, 85, 90, 60, 100, 80, 95), // Completion % for the last 7 days
                            viewModel = viewModel
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

        // Custom Styled Floating System Notification Banner 
        AnimatedVisibility(
            visible = activeBannerMessage != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .zIndex(999f)
        ) {
            activeBannerMessage?.let { message ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .border(
                            1.dp,
                            if (settingsState.isDarkMode) Color(0xFF1D5C4F).copy(alpha = 0.5f)
                            else Color(0xFF34D399).copy(alpha = 0.5f),
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { activeBannerMessage = null },
                    colors = CardDefaults.cardColors(
                        containerColor = if (settingsState.isDarkMode) Color(0xEE0D2D26) else Color(0xECECFDF5)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Styled icon container
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (settingsState.isDarkMode) Color(0xFF154C40)
                                    else Color(0xFFA7F3D0)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (message.contains("اهتزاز") || message.contains("نبض")) Icons.Default.Notifications
                                            else if (message.contains("طاعة") || message.contains("صلوات") || message.contains("أذكار")) Icons.Default.CheckCircle
                                            else if (message.contains("صلاة") || message.contains("🕌")) Icons.Default.Star
                                            else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (settingsState.isDarkMode) Color(0xFF34D399) else Color(0xFF047857),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "زاد العبادة • تنبيه مخصص",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (settingsState.isDarkMode) Color(0xFF34D399) else Color(0xFF047857)
                                )
                                Text(
                                    text = "الآن",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (settingsState.isDarkMode) Color.White else Color(0xFF0F172A),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ZadHeader(
    settings: AppSettings,
    viewModel: WorshipViewModel,
    onTabSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val sdfDateStr = SimpleDateFormat("EEEE، d MMMM", Locale("ar"))
    val todayDateStr = "اليوم، " + sdfDateStr.format(Date())

    val isDark = settings.isDarkMode

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        // Left Column: 4 Circular Buttons + Welcome user
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Button 1: Refresh Location
                HeaderActionButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = "تحديث الموقع",
                    isDark = isDark,
                    onClick = {
                        viewModel.fetchAndSaveRealLocation(context)
                    }
                )

                // Button 2: Alert Sound Experience
                HeaderActionButton(
                    icon = Icons.Default.Notifications,
                    contentDescription = "تجربة التنبيه والآذان",
                    isDark = isDark,
                    onClick = {
                        viewModel.unlockAudioAndPlayPreview()
                    }
                )

                // Button 3: Go to settings
                HeaderActionButton(
                    icon = Icons.Default.Settings,
                    contentDescription = "الإعدادات السريعة",
                    isDark = isDark,
                    onClick = {
                        onTabSelected("settings")
                    }
                )

                // Button 4: Sun/Moon Theme Toggle
                HeaderActionButton(
                    emoji = if (isDark) "☀️" else "🌙",
                    contentDescription = if (isDark) "تفعيل الوضع المضيء" else "تفعيل الوضع المظلم",
                    isDark = isDark,
                    onClick = {
                        viewModel.toggleDarkMode(!isDark)
                    }
                )
            }

            // Welcome Text
            val welcomeText = if (settings.isGoogleSignedIn && settings.googleUserName.isNotEmpty()) {
                settings.googleUserName
            } else {
                "أحمد الدقميري"
            }
            Text(
                text = "مرحبًا، $welcomeText",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White.copy(alpha = 0.9f) else Color(0xFF064E3B)
            )
        }

        // Right Column: Brand Name & Date
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "زاد",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = if (isDark) Color.White else Color(0xFF047857),
                fontSize = 32.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = if (isDark) Color.White.copy(alpha = 0.4f) else Color(0xFF047857).copy(alpha = 0.4f),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = todayDateStr,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF064E3B).copy(alpha = 0.7f)
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = if (isDark) Color.White.copy(alpha = 0.4f) else Color(0xFF047857).copy(alpha = 0.4f),
                    modifier = Modifier.size(14.dp)
                )
            }

            Text(
                text = "مواقيت الصلاة\nتلقائي حسب الموقع",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.End,
                color = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.8f),
                lineHeight = 12.sp,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun HeaderActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    emoji: String? = null,
    contentDescription: String,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(
                if (isDark) Color.White.copy(alpha = 0.08f)
                else Color(0xFF047857).copy(alpha = 0.08f)
            )
            .border(
                1.dp,
                if (isDark) Color.White.copy(alpha = 0.15f)
                else Color(0xFF047857).copy(alpha = 0.15f),
                CircleShape
            )
            .clickable(
                onClick = onClick,
                role = androidx.compose.ui.semantics.Role.Button
            ),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isDark) Color.White else Color(0xFF047857),
                modifier = Modifier.size(20.dp)
            )
        } else if (emoji != null) {
            Text(
                text = emoji,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

data class SunnahBundle(
    val title: String,
    val isChecked: Boolean,
    val onToggle: () -> Unit,
    val emoji: String
)

@Composable
fun TodayScreen(
    progress: WorshipProgress,
    prayerTimes: com.example.data.PrayerTimesCalculator.PrayerTimesList,
    settings: AppSettings,
    isAudioUnlocked: Boolean,
    viewModel: WorshipViewModel
) {
    val percentage = progress.calculatePercentage()
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF061512)

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

        // Detailed Sunnah Segmented card
        item {
            var isExpanded by remember { mutableStateOf(false) }
            val completedSunnahs = listOf(
                progress.sunnahFajr, progress.sunnahDuha, progress.sunnahDhuhrQabli,
                progress.sunnahDhuhrBadi, progress.sunnahMaghrib, progress.sunnahIsha, progress.sunnahQiyam
            ).count { it }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) {
                        if (progress.sunnahPrayed || completedSunnahs > 0) Color(0xFF10B981).copy(alpha = 0.08f) else Color.White.copy(alpha = 0.05f)
                    } else {
                        if (progress.sunnahPrayed || completedSunnahs > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                    }
                ),
                border = if (isDark) {
                    BorderStroke(1.dp, if (progress.sunnahPrayed || completedSunnahs > 0) Color(0xFF10B981).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f))
                } else null,
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExpanded = !isExpanded }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "✨", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "النوافل وسنن الرواتب الصلاة",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (completedSunnahs > 0) "تم إنجاز $completedSunnahs من ٧ سنن اليوم! 🎉" else "اضغط لتفصيل ركعات وسنن اليوم تالياً 👇",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                        IconButton(onClick = { isExpanded = !isExpanded }) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "عرض السنن",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (isExpanded) {
                        HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.05f) else Color.LightGray.copy(alpha = 0.3f))
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val sunnahsList = listOf(
                                SunnahBundle("ركعتا الفجر القبلية", progress.sunnahFajr, { viewModel.toggleSunnahFajr() }, "🌅"),
                                SunnahBundle("سنة الضحى المباركة", progress.sunnahDuha, { viewModel.toggleSunnahDuha() }, "☀️"),
                                SunnahBundle("سنة الظهر القبلية (٤ ركعات)", progress.sunnahDhuhrQabli, { viewModel.toggleSunnahDhuhrQabli() }, "🌤️"),
                                SunnahBundle("سنة الظهر البعدية (ركعتان)", progress.sunnahDhuhrBadi, { viewModel.toggleSunnahDhuhrBadi() }, "🌤️"),
                                SunnahBundle("سنة المغرب البعدية (ركعتان)", progress.sunnahMaghrib, { viewModel.toggleSunnahMaghrib() }, "🌇"),
                                SunnahBundle("سنة العشاء البعدية (ركعتان)", progress.sunnahIsha, { viewModel.toggleSunnahIsha() }, "🌌"),
                                SunnahBundle("قيام الليل والسر والوتر", progress.sunnahQiyam, { viewModel.toggleSunnahQiyam() }, "✨")
                            )

                            sunnahsList.forEach { s ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { s.onToggle() }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(s.emoji, fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(s.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    Checkbox(
                                        checked = s.isChecked,
                                        onCheckedChange = { s.onToggle() },
                                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Prophetic Supplications Reference Library Card
        item {
            Text(
                text = "مكتبة الأدعية والأورداد النبوية المأثورة 🕌:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )
        }

        val supps = listOf(
            "اللَّهُمَّ إِنَّكَ عَفُوٌّ تُحِبُّ الْعَفْوَ فَاعْفُ عَنِّي.",
            "رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً وَفِي الْآخِرَةِ حَسَنَةً وَقِنَا عَذَابَ النَّارِ.",
            "يَا مُقَلِّبَ الْقُلُوبِ ثَبِّتْ قَلْبِي عَلَى دِينِكَ.",
            "اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالعَافِيَةَ فِي الدُّنْيَا وَالْآخِرَةِ.",
            "لَا إِلَهَ إِلَّا أَنْتَ سُبْحَانَكَ إِنِّي كُنْتُ مِنَ الظَّالِمِينَ."
        )

        items(supps) { supp ->
            val favsStr = settings.favoriteSupplicationsJson
            val isFav = remember(favsStr) {
                favsStr.contains(supp)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color.White.copy(alpha = 0.04f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.05f) else Color.LightGray.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = supp,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            lineHeight = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            if (isFav) {
                                viewModel.removeFavoriteSupplication(supp)
                            } else {
                                viewModel.addFavoriteSupplication(supp)
                            }
                        }) {
                            Icon(
                                imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (isFav) "إزالة من المفضلات" else "إضافة للمفضلات",
                                tint = if (isFav) Color.Red else Color.LightGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
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

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Slicing items into 2 rows of 3 elements
                val chunkedItems = items.chunked(3)
                chunkedItems.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { p ->
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
                                    .weight(1f)
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
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showGoogleAuthDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var newMemberName by remember { mutableStateOf("") }
    var newMemberRelation by remember { mutableStateOf("صديق(ة)") }

    var groupCreationName by remember { mutableStateOf("") }
    var groupInviteCodeToJoin by remember { mutableStateOf("") }

    val isDark = MaterialTheme.colorScheme.background == Color(0xFF061512)

    Box(modifier = Modifier.fillMaxSize()) {
        if (!settings.isGoogleSignedIn) {
            // Google Sign-In Required Screen
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Spacer(modifier = Modifier.height(60.dp))
                    Text(
                        text = "👥 شبكة \"الأهل والأسرة مشتركة\"",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF10B981).copy(alpha = 0.08f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "تسجيل الدخول عبر Google مطلوب 🔑",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "انضم إلينا لتتمكن من إنشاء مجموعات الأهل، دعوتهم لمتابعة التقدم اليومي وتحفيز بعضكم البعض بإرسال قلوب الدعم الروحي والتفاعلات المباشرة بشكل آمن وسلس.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(30.dp))
                    
                    // Styled Google Sign-In Button
                    Button(
                        onClick = { showGoogleAuthDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, Color.LightGray),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("google_signin_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Custom high-fidelity Google G Vector Letter Draw
                            Text(
                                text = "G ", 
                                style = MaterialTheme.typography.titleMedium, 
                                fontWeight = FontWeight.Bold, 
                                color = Color(0xFF4285F4)
                            )
                            Text(
                                text = "تسجيل الدخول الآمن بحساب Google",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            // Signed In Family Circle view
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // User Google Profile Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = settings.googleUserName.take(1).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = settings.googleUserName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = settings.googleUserEmail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                            
                            OutlinedButton(
                                onClick = { viewModel.signOutGoogle() },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f))
                            ) {
                                Text("خروج", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // If NOT in Family Group: Join or Create
                if (settings.familyGroupName.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "🆕 إنشاء مجموعة عائلية جديدة",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "سيتيح لك هذا إنشاء كود دعوة خاص بك لترسله لبقية الأهل لينضموا لشبكتكم المشتركة.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray,
                                    lineHeight = 16.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = groupCreationName,
                                    onValueChange = { groupCreationName = it },
                                    label = { Text("اسم العائلة (مثال: عائلة المهتدي)", fontSize = 12.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        if (groupCreationName.isNotBlank()) {
                                            viewModel.createFamilyGroup(groupCreationName)
                                            groupCreationName = ""
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("إنشاء المجموعة وتوليد رمز الدعوة 👥", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "📥 الانضمام لمجموعة عائلية عبر كود دعوة",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "أدخل رمز الدعوة المكون من 8 خانات (مثال: ZAD-1234) الذي تم توليده بواسطة فرد عائلتك للاندماج معه.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray,
                                    lineHeight = 16.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = groupInviteCodeToJoin,
                                    onValueChange = { groupInviteCodeToJoin = it },
                                    label = { Text("رمز الدعوة (ZAD-XXXX)", fontSize = 12.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        if (groupInviteCodeToJoin.isNotBlank()) {
                                            viewModel.joinFamilyGroup(groupInviteCodeToJoin)
                                            groupInviteCodeToJoin = ""
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("انضمام للمجموعة الآن 🟢", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    // Signed In AND has family group joined
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) Color(0xFF10B981).copy(alpha = 0.08f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                            ),
                            border = if (isDark) BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.2f)) else null,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "👪 مجموعة: ${settings.familyGroupName}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = settings.familyGroupInviteCode,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "كود الدعوة بالأعلى جاهز لترسله للأهل. شارك التزامك اليومي وتسابق في الطاعات بكل بهجة وتواصل لحظي.",
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
                                text = "مستوى التزام دائرتك الحالية:",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
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
                                Text("لا يوجد أعضاء آخرين في المجموعة العائلية، ابدأ بإضافة فرد يدويًا أو شارك الرمز.", color = Color.Gray, fontSize = 13.sp)
                            }
                        }
                    } else {
                        items(familyList) { member ->
                            FamilyMemberItemCard(
                                member = member,
                                onLikeClick = { viewModel.likeFamilyMember(member.id) },
                                onSimulateWorshipClick = { viewModel.simulateWorshipForMember(member.id) },
                                onDeleteClick = { viewModel.deleteFamilyMember(member.id) }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        OutlinedButton(
                            onClick = { viewModel.leaveFamilyGroup() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("مغادرة المجموعة العائلية ودائرتها 👥", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(30.dp)) }
            }
        }

        // Beautiful Google Accounts simulation sheet / dialog
        if (showGoogleAuthDialog) {
            AlertDialog(
                onDismissRequest = { showGoogleAuthDialog = false },
                title = { Text("اختر حسابًا للمتابعة لـ زاد", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "سيقوم تطبيق زاد بالوصول لملفك الشخصي ونظام الغيميات السحابي الآمن لتسجيل الدخول:",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Active user metadata-driven account
                        Card(
                            onClick = {
                                viewModel.signInWithGoogle(
                                    id = "medoo51195",
                                    name = "محمد البدري",
                                    email = "medoo51195@gmail.com",
                                    avatar = "avatar1"
                                )
                                showGoogleAuthDialog = false
                            },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4285F4)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("م", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("محمد البدري (أنت)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text("medoo51195@gmail.com", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
                        }

                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showGoogleAuthDialog = false }) {
                        Text("إلغاء المتابعة")
                    }
                }
            )
        }

        // Removed Add Member dialog, handled by invite code.
    }
}

@Composable
fun FamilyMemberItemCard(
    member: FamilyMember,
    onLikeClick: () -> Unit,
    onSimulateWorshipClick: () -> Unit,
    onDeleteClick: () -> Unit
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

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Simulate worship button
                Button(
                    onClick = onSimulateWorshipClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "محاكاة طاعة ⚡",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Delete member button
                OutlinedButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.width(90.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.Red.copy(alpha = 0.8f)
                    ),
                    border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "حذف 🗑️",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun StatsScreen(
    streak: Int,
    history: List<Int>, // Percentage completions last 7 days
    viewModel: WorshipViewModel
) {
    val settingsState by viewModel.settings.collectAsStateWithLifecycle()
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF061512)
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = LocalContext.current
    val quarters = settingsState.quranQuartersCount

    val favoriteSupplicationsStr = settingsState.favoriteSupplicationsJson
    val favoriteList = remember(favoriteSupplicationsStr) {
        if (favoriteSupplicationsStr.isBlank()) emptyList() else favoriteSupplicationsStr.split("|#|").map { it.trim() }.filter { it.isNotBlank() }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Streaks ring Card
        item {
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

        // Quranic Quarters Tracker Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF0F2621).copy(alpha = 0.4f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "📖", fontSize = 28.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "حصاد وتلاوة القرآن الكريم",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "تتبع حصيلتك من الأرباع والأجزاء المنجزة لحفظ وتدارس كتاب الله بانتظام.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val juzCount = quarters / 4
                    val remainingQuarters = quarters % 4

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (juzCount > 0) {
                                "إجمالي المـنجز: $juzCount أجزاء و $remainingQuarters ربع ($quarters ربعاً)"
                            } else {
                                "إجمالي المـنجز: $quarters ربعاً من القرآن الكريم"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.incrementQuranQuarters(-1) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("- ربع", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.incrementQuranQuarters(1) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Text("+ ربع واحد", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.incrementQuranQuarters(4) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Text("+ جزء (٤ أرباع)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Favorites Supplications Section
        item {
            Text(
                text = "الأدعية والأذكار المفضلة ❤️:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        if (favoriteList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color.White.copy(alpha = 0.03f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لم تقم بإضافة أي دعاء للمفضلة حتى الآن.\nاستخدم أيقونة القلب على الأدعية في قائمة الذكر لتظهر هنا!",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        } else {
            items(favoriteList) { supp ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardColors(
                        containerColor = if (isDark) Color(0xFF0F1E1A).copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = Color.Transparent
                    ),
                    border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.05f) else Color.LightGray.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = supp,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground,
                                lineHeight = 18.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row {
                            IconButton(onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(supp))
                                Toast.makeText(context, "تم نسخ الدعاء الجميل بنجاح! 📋", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "نسخ الدعاء", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { viewModel.removeFavoriteSupplication(supp) }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف من المفضلة", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
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
                isUnlocked = (quarters >= 12)
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
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.fetchAndSaveRealLocation(context)
        } else {
            Toast.makeText(context, "لم يتم منح صلاحية الموقع الجغرافي.", Toast.LENGTH_LONG).show()
        }
    }

    var showAddReminderDialog by remember { mutableStateOf(false) }

    // Forms for setting inputs
    var reminderTitle by remember { mutableStateOf("") }
    var reminderHour by remember { mutableStateOf(19) }
    var reminderMinute by remember { mutableStateOf(0) }
    var reminderSound by remember { mutableStateOf("الافتراضي") }

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
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                } else {
                                    viewModel.updateLocationSettings(false, 30.0444, 31.2357, "القاهرة")
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    if (settings.isAutoLocation) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تحديث الموقع الفعلي الآن 📍", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
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

        // Vibration and tactile styling
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
                        text = "📳 التحكم في الاهتزاز والتنبيه اللمسي",
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
                            text = "تمكين الاهتزاز مع التنبيهات والآذان:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Switch(
                            checked = settings.isVibrationEnabled,
                            onCheckedChange = { viewModel.toggleVibration(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    if (settings.isVibrationEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "نمط نبض التنبيه (اضغط للاختيار والتجربة):",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val patterns = listOf("نبض خفيف", "نبض الفرح", "نبض قوي ومستمر", "نبض متقطع")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            patterns.forEach { pattern ->
                                val scoreSelected = settings.vibrationPattern == pattern
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (scoreSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            else Color.Gray.copy(alpha = 0.1f)
                                        )
                                        .border(
                                            1.dp,
                                            if (scoreSelected) MaterialTheme.colorScheme.primary
                                            else Color.Transparent,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            viewModel.updateVibrationPattern(pattern)
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = pattern,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (scoreSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (scoreSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Font Size Configuration and Customizations
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
                        text = "🔍 التحكم في مظهر التطبيق وحجم الخط",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "اختر حجم الخط المناسب لك لتسهيل تلاوة الأدعية والأوراد في كافة شاشات التطبيق:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("صغير", "متوسط", "كبير").forEach { size ->
                            val isSelected = settings.appFontSizeMultiplier == size
                            Button(
                                onClick = { viewModel.setAppFontSize(size) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(size, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Danger Zone Resets Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⚠️ منطقة الصيانة وإعادة التهيئة",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "يمكنك تصفير قيم العبادات اليومية أو تتبع القرآن للبدء من جديد عند الضرورة:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.resetTodayWorships() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("تصفير اليوم", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.resetQuranQuarters() },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("تصفير الورد القرآني", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
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
                                days = "يومي",
                                sound = reminderSound
                            )
                            showAddReminderDialog = false
                            reminderTitle = ""
                            reminderSound = "الافتراضي"
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
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("صوت التنبيه:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("الافتراضي", "هادئ", "تنبيه قوي").forEach { soundOpt ->
                            FilterChip(
                                selected = reminderSound == soundOpt,
                                onClick = { reminderSound = soundOpt },
                                label = { Text(soundOpt, fontSize = 11.sp) }
                            )
                        }
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
                        text = String.format(Locale.US, "%02d:%02d (%s) - %s", reminder.hour, reminder.minute, reminder.repeatDays, reminder.soundUri),
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
                selected = selectedTab == "qibla",
                onClick = { onTabSelected("qibla") },
                icon = { Icon(Icons.Default.LocationOn, contentDescription = "القبلة") },
                label = { Text("القبلة", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.testTag("tab_qibla")
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

@Composable
fun AppWideWelcomeLoginScreen(
    viewModel: WorshipViewModel,
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    var showGoogleAuthDialog by remember { mutableStateOf(false) }
    val isDark = settings.isDarkMode

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(Color(0xFF0D2D26), Color(0xFF061512))
                    } else {
                        listOf(Color(0xFFECFDF5), Color(0xFFF0FDF4))
                    }
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar on Login Screen for Theme Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "مرحبًا بك في منصة زاد الروحية ✨",
                style = MaterialTheme.typography.labelMedium,
                color = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF064E3B).copy(alpha = 0.6f)
            )
            
            HeaderActionButton(
                emoji = if (isDark) "☀️" else "🌙",
                contentDescription = if (isDark) "الوضع المضيء" else "الوضع المظلم",
                isDark = isDark,
                onClick = {
                    viewModel.toggleDarkMode(!isDark)
                }
            )
        }

        Spacer(modifier = Modifier.height(30.dp))

        // Large Brand Logo "زاد"
        Text(
            text = "زاد",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
            color = if (isDark) Color.White else Color(0xFF047857),
            fontSize = 72.sp
        )
        Text(
            text = "رفيق المسلم اليومي للعبادات والطاعة",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color(0xFF34D399) else Color(0xFF047857),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Features list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                LoginFeatureCard(
                    title = "🕌 مواقيت الصلاة والآذان بدقة",
                    description = "تنبيهات ومواقيت صلاة تتبع موقعك الفعلي بدقة متناهية مع خيار ضبط يدوي ملائم.",
                    isDark = isDark
                )
            }
            item {
                LoginFeatureCard(
                    title = "👥 دائرة الأهل والأسرة الروحية",
                    description = "شارك عباداتك مع عائلتك بشكل تفاعلي، لتتبادلوا التشجيع والإعجاب بالطاعات اليومية.",
                    isDark = isDark
                )
            }
            item {
                LoginFeatureCard(
                    title = "📊 سجل الإنجازات والالتزام الروحاني",
                    description = "إحصائيات ذكية ونسب مئوية تعرض تقدمك اليومي وتذكرك بالسنن الرواتب بدقة.",
                    isDark = isDark
                )
            }
            item {
                LoginFeatureCard(
                    title = "📖 الورد القرآني وأذكار اليوم",
                    description = "تلاوة القرآن الكريم وأذكار الصباح والمساء المأثورة مجمعة في شاشة واحدة.",
                    isDark = isDark
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Google Sign-In Button
        Button(
            onClick = { showGoogleAuthDialog = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, Color.LightGray),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("google_signin_button")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "G ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4285F4)
                )
                Text(
                    text = "تسجيل الدخول الآمن بحساب Google",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "بالضغط على تسجيل الدخول فإنك تنضم لدائرة طاعة تفاعلية آمنة",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }

    // Google Sign-In Interactive Simulation Dialog
    if (showGoogleAuthDialog) {
        AlertDialog(
            onDismissRequest = { showGoogleAuthDialog = false },
            title = { Text("اختر حسابًا للمتابعة لـ زاد", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "سيقوم تطبيق زاد بالوصول لملفك الشخصي ونظام الغيميات السحابي الآمن لتسجيل الدخول:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Account Option 1 (Default Google Account)
                    Card(
                        onClick = {
                            viewModel.signInWithGoogle(
                                id = "medoo51195",
                                name = "أحمد الدقميري",
                                email = "medoo51195@gmail.com",
                                avatar = "avatar1"
                            )
                            showGoogleAuthDialog = false
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4285F4)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("أ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("أحمد الدقميري (أنت)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text("medoo51195@gmail.com", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showGoogleAuthDialog = false }) {
                    Text("إلغاء", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun LoginFeatureCard(
    title: String,
    description: String,
    isDark: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.8f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) Color.White.copy(alpha = 0.06f) else Color(0xFF047857).copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color(0xFF10B981) else Color(0xFF047857)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.DarkGray
            )
        }
    }
}
