package com.example.esyu

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar
import android.graphics.drawable.Drawable
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

const val USAGE_CHANNEL_ID = "usage_limit_channel"
const val USAGE_NOTIFICATION_ID = 1001

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Start the foreground service in a safe way to avoid multiple restarts
        ContextCompat.startForegroundService(this, Intent(this, UsageMonitorService::class.java))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val customColors = darkColorScheme(
                primary = Color(0xFF82B1FF),           // Muted light blue
                onPrimary = Color.Black,
                secondary = Color(0xFF4DD0E1),         // Cyan tone
                onSecondary = Color.Black,
                background = Color(0xFF0E0E0E),        // Near-black background
                onBackground = Color(0xFFEEEEEE),      // Soft light gray
                surface = Color(0xFF1A1A1A),           // Dark gray surface
                onSurface = Color(0xFFE0E0E0),         // Slightly softer white
                error = Color(0xFFEF5350),
                onError = Color.Black
            )

            MaterialTheme(
                colorScheme = customColors,
                typography = Typography(), // Optional: customize if needed
                shapes = Shapes()          // Optional: customize if needed
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = customColors.background
                ) {
                    AppNavigator()
                }
            }
        }
    }
}

fun createUsageNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = "Kullanım Sınırı"
        val descriptionText = "Uygulama kullanım süresi sınırına ulaşıldı."
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(USAGE_CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

fun sendUsageLimitNotification(
    context: Context,
    pkg: String,
    appName: String,
    minutesUsed: String
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        )
        if (permission != PackageManager.PERMISSION_GRANTED) return
    }

    val pendingIntent: PendingIntent =
        PendingIntent.getActivity(
            context,
            context.packageName.hashCode(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    val builder = NotificationCompat.Builder(context, USAGE_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher)
        .setContentTitle("⏰ $appName Sınırı Aşıldı")
        .setContentText("$minutesUsed dakika kullanım sınırını aştınız.")
        .setStyle(
            NotificationCompat.BigTextStyle().bigText(
                "$appName uygulamasını $minutesUsed dakika boyunca kullandınız.\n" +
                        "Belirlediğiniz sınırı aştınız. Bildirimi Kapatmak için İlgili Uygulamanın Detay Sayfasından Sınırı Kaldırın."
            )
        )
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)   // ⬅️ Artık tek aksiyon bu

    NotificationManagerCompat.from(context)
        .notify(USAGE_NOTIFICATION_ID + appName.hashCode(), builder.build())
}

// 2️⃣ Limit kaydetme – “bildirim bastırma” satırları silindi
@RequiresApi(Build.VERSION_CODES.O)
suspend fun saveLimit(
    context: Context,
    packageName: String,
    limitMinutes: Int
) {
    val limitKey = intPreferencesKey(packageName)
    context.dataStore.edit { prefs ->
        prefs[limitKey] = limitMinutes
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppNavigator() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "root") {
        composable("root") { RootScreen(navController) }
        composable("detail/{packageName}",
            arguments = listOf(navArgument("packageName") { type = NavType.StringType })
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName") ?: ""
            AppDetailScreen(packageName, navController)
        }
    }
}

@Composable
fun RootScreen(navController : NavHostController) {
    val context = LocalContext.current
    var hasNotificationPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasUsagePermission by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hasUsagePermission = hasUsageStatsPermission(context)
    }

    if (hasNotificationPermission && hasUsagePermission) {
        HomeScreen(navController)
    } else {
        PermissionScreen(
            onNotificationPermissionGranted = { hasNotificationPermission = true },
            onUsagePermissionGranted = {
                hasUsagePermission = hasUsageStatsPermission(context)
            }
        )
    }
}

@Composable
fun HomeScreen(navController: NavHostController) {
    val context = LocalContext.current
    var usageStats by remember { mutableStateOf<List<Triple<String, String, Long>>>(emptyList()) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        createUsageNotificationChannel(context)
        while (true) {
            usageStats = getUsagesForDay(context)
            kotlinx.coroutines.delay(50_000)
        }
    }

    val totalUsageMs = usageStats.sumOf { it.third }
    val averageUsageMs = if (usageStats.isNotEmpty()) totalUsageMs / usageStats.size else 0L

    val totalUsageText = formatMillisecondsToReadableTime(totalUsageMs)
    val averageUsageText = formatMillisecondsToReadableTime(averageUsageMs)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.systemBars.asPaddingValues())
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Uygulama Kullanım Süreleri",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Toplam Kullanım: $totalUsageText",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ortalama Uygulama Süresi: $averageUsageText",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }


        Spacer(modifier = Modifier.height(16.dp))

        /*Button(
            onClick = { refreshTrigger++ },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Verileri Yenile", color = MaterialTheme.colorScheme.onPrimary)
        }*/

        //Spacer(modifier = Modifier.height(16.dp))

        if (usageStats.isEmpty()) {
            val message = if (refreshTrigger == 0) "Kullanım verileri yükleniyor..." else "Görüntülenecek uygulama verisi bulunamadı."
            Text(
                text = message,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }

        usageStats.forEach { (packageName, appName, timeMs) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { navController.navigate("detail/$packageName") },
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OtherAppIcon(packageName, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = appName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Bugün: ${formatMillisecondsToReadableTime(timeMs)}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
        /*Column {
        Text("Uygulama kullanım süreleri, cihazınızın kullanım istatistiklerine dayanmaktadır. \n" +
            "1 dakikadan az süreler dikkate alınmaz. \n" + "Uygulamaların Üzerine tıklayarak detaylarına ulaşabilirsiniz.\n" + "Bu Uygulama Muhammed Ali Kandemir ve Bilai İzzettin Tarafından geliştirilmiştir. Tüm hakları saklıdır.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(16.dp))
        }*/
    }
}

@Composable
fun OtherAppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var iconBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        val pm = context.packageManager
        val drawable: Drawable? = try {
            pm.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }
        drawable?.let {
            val bitmap = it.toBitmap()
            iconBitmap = bitmap.asImageBitmap()
        }
    }

    iconBitmap?.let {
        Box(
            modifier = modifier
                .size(54.dp) // Slightly larger than before
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF121212)), // Dark background
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = it,
                contentDescription = "App Icon",
                modifier = Modifier.size(34.dp) // Balanced icon size
            )
        }
    }
}

fun getUsagesForDay(context: Context): List<Triple<String, String, Long>> {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    // Start of today's time (00:00)
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startTime = calendar.timeInMillis
    val endTime = System.currentTimeMillis()

    val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
    val event = UsageEvents.Event()

    val usageMap = mutableMapOf<String, Long>()
    val foregroundTimes = mutableMapOf<String, Long>()

    while (usageEvents.hasNextEvent()) {
        usageEvents.getNextEvent(event)
        val packageName = event.packageName ?: continue

        when (event.eventType) {
            UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                foregroundTimes[packageName] = event.timeStamp
            }
            UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                val start = foregroundTimes[packageName]
                // Only count usages that both started and ended today
                if (start != null && start >= startTime && event.timeStamp <= endTime) {
                    val duration = event.timeStamp - start
                    usageMap[packageName] = usageMap.getOrDefault(packageName, 0L) + duration
                    foregroundTimes.remove(packageName)
                }
            }
        }
    }

    val filteredUsage = usageMap
        .filter { it.value > 60_000 && it.key != context.packageName && isUserApp(context, it.key) }
        .map { (packageName, totalTime) ->
            var appName = getAppNameFromPackage(context, packageName)
            if (appName.startsWith("com.") && appName.length < 8) {
                appName = packageName.substringAfterLast('.')
                if (appName.isBlank()) appName = packageName
            }
            if (appName.isBlank()) appName = "Unknown Application"
            Triple(packageName, appName, totalTime)
        }
        .sortedByDescending { it.third }

    Log.d("USAGE_DEBUG", "getUsagesForDay returning: ${filteredUsage.map { it.first }}")
    return filteredUsage
}

fun isUserApp(context: Context, packageName: String): Boolean {
    val whitelist = mutableListOf(
        "com.android.chrome",
        "com.google.android.youtube",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.dialer",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms"
    )

    Telephony.Sms.getDefaultSmsPackage(context)?.let { defaultSmsPackageName ->
        if (defaultSmsPackageName.isNotBlank()) {
            whitelist.add(defaultSmsPackageName)
        }
    }
    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    telecomManager?.defaultDialerPackage?.let { defaultDialerPackageName ->
        if (defaultDialerPackageName.isNotBlank()) {
            whitelist.add(defaultDialerPackageName)
        }
    }

    if (packageName in whitelist) {
        return true
    }

    return try {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(packageName, 0)

        val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

        !(isSystemApp || isUpdatedSystemApp)
    } catch (e: PackageManager.NameNotFoundException) {
        Log.w("AppFilter", "Package not found: $packageName", e)
        false
    } catch (e: Exception) {
        Log.e("AppFilter", "Error checking app type for $packageName", e)
        false
    }
}

fun getAppNameFromPackage(context: Context, packageName: String): String {
    return try {
        val pm = context.packageManager
        val ai = pm.getApplicationInfo(packageName, 0)
        val label = pm.getApplicationLabel(ai).toString()

        if (label.isBlank() || label == packageName) {
            val resId = ai.labelRes
            if (resId != 0) {
                try {
                    pm.getResourcesForApplication(ai).getString(resId)
                } catch (e: Exception) {
                    packageName
                }
            } else {
                packageName
            }
        } else {
            label
        }
    } catch (e: Exception) {
        Log.w("AppName", "Fallback to package: $packageName", e)
        packageName
    }
}

fun formatMillisecondsToReadableTime(milliseconds: Long): String {
    val totalMinutes = milliseconds / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return buildString {
        if (hours > 0) append("${hours}saat ")
        append("${minutes}dk")
    }.trim()
}

val Context.dataStore by preferencesDataStore(name = "app_limits")

fun getLimit(context: Context, packageName: String): Flow<Int?> {
    val key = intPreferencesKey(packageName)
    return context.dataStore.data.map { preferences ->
        preferences[key]
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppDetailScreen(packageName: String, navController: NavHostController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val limitFlow = remember { getLimit(context, packageName) }
    val storedLimit by limitFlow.collectAsState(initial = null)
    // Insert: collect hourly limit from DataStore
    val hourlyLimitFlow = remember { context.dataStore.data.map { it[intPreferencesKey("${packageName}_hourly")] } }
    val storedHourlyLimit by hourlyLimitFlow.collectAsState(initial = null)
    var limit by remember { mutableStateOf(storedLimit?.toString() ?: "180") }
    var message by remember { mutableStateOf("") }

    Column (
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.systemBars.asPaddingValues())
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val appName = getAppNameFromPackage(context, packageName)
        // Top section: App icon, title, and today's usage
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OtherAppIcon(packageName = packageName, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "$appName Detayları",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                val todayUsageMs = getUsagesForDay(context).firstOrNull { it.first == packageName }?.third ?: 0L
                Text(
                    text = "Bugün: ${formatMillisecondsToReadableTime(todayUsageMs)}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { navController.popBackStack() },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Anasayfaya Dön", color = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(modifier = Modifier.height(24.dp))

        // Show currently active limits
        if (storedLimit != null || storedHourlyLimit != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = buildString {
                    if (storedLimit != null) append("Günlük sınır: ${storedLimit}dk\n")
                    if (storedHourlyLimit != null) append("Saatlik sınır: ${storedHourlyLimit}dk")
                },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Daily Limit Input
        Text("Günlük Kullanım Sınırı (dakika, max 10080):", fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = limit,
            onValueChange = { limit = it },
            label = { Text("Süre limiti", color = MaterialTheme.colorScheme.onBackground) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val parsed = limit.toIntOrNull()
                if (parsed != null && parsed > 0 && parsed <= 1440 * 7) {
                    coroutineScope.launch {
                        saveLimit(context, packageName, parsed)
                        message = "$parsed dk kullanım sınırı ayarlandı."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Sınırı Kaydet", color = MaterialTheme.colorScheme.onPrimary)
        }

        /*// --- BEGIN Hourly Limit Section ---
        Spacer(modifier = Modifier.height(24.dp))
        Text("Saatlik Kullanım Sınırı (dakika, max 60):", fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(8.dp))

        var hourlyLimit by remember { mutableStateOf("60") }

        OutlinedTextField(
            value = hourlyLimit,
            onValueChange = { hourlyLimit = it },
            label = { Text("Saatlik süre limiti", color = MaterialTheme.colorScheme.onBackground) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val parsed = hourlyLimit.toIntOrNull()
                if (parsed != null && parsed in 1..60) {
                    coroutineScope.launch {
                        context.dataStore.edit {
                            it[intPreferencesKey("${packageName}_hourly")] = parsed
                        }
                        message = "$parsed dk saatlik sınır ayarlandı."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Saatlik Sınırı Kaydet", color = MaterialTheme.colorScheme.onPrimary)
        }
        // --- END Hourly Limit Section ---*/

        if (message.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                coroutineScope.launch {
                    val key = intPreferencesKey(packageName)
                    val hourlyKey = intPreferencesKey("${packageName}_hourly")
                    context.dataStore.edit { preferences ->
                        preferences.remove(key)
                        preferences.remove(hourlyKey)
                    }
                    message = "Günlük ve saatlik kullanım sınırları kaldırıldı."
                    limit = "180"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Sınırı Kaldır", color = MaterialTheme.colorScheme.onError)
        }

        Spacer(modifier = Modifier.height(12.dp))

        WeeklyUsageList(packageName, navController)
    }
}

@Composable
fun WeeklyUsageList(packageName: String, navController: NavController) {
    val context = LocalContext.current
    val usageList = remember { mutableStateListOf<Pair<String, Int>>() }

    LaunchedEffect(Unit) {
        val weeklyData = getWeeklyUsageStats(context, packageName)
        usageList.clear()
        usageList.addAll(weeklyData)
    }

    val maxMinutes = usageList.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Haftalık Kullanım Grafiği",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            usageList.forEach { (day, minutes) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = "$minutes dk",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height((minutes * 1.5f).dp.coerceAtMost(140.dp))
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    )
                                )
                            )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

fun getWeeklyUsageStats(context: Context, packageName: String): List<Pair<String, Int>> {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val endTime = System.currentTimeMillis()

    val calendar = Calendar.getInstance()
    calendar.timeInMillis = endTime
    calendar.add(Calendar.DAY_OF_YEAR, -6)
    val startTime = calendar.timeInMillis

    val stats = usageStatsManager.queryUsageStats(
        UsageStatsManager.INTERVAL_DAILY,
        startTime,
        endTime
    )

    val dayLabels = mutableListOf<String>()
    val usageMap = mutableMapOf<String, Int>()

    for (i in 0..6) {
        val dayCal = Calendar.getInstance()
        dayCal.timeInMillis = startTime
        dayCal.add(Calendar.DAY_OF_YEAR, i)

        val dayName = when (dayCal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Pzt"
            Calendar.TUESDAY -> "Sal"
            Calendar.WEDNESDAY -> "Çar"
            Calendar.THURSDAY -> "Per"
            Calendar.FRIDAY -> "Cum"
            Calendar.SATURDAY -> "Cmt"
            Calendar.SUNDAY -> "Paz"
            else -> "?"
        }

        dayLabels.add(dayName)
        usageMap[dayName] = 0
    }

    stats?.filter { it.packageName == packageName }?.forEach { stat ->
        val cal = Calendar.getInstance()
        cal.timeInMillis = stat.firstTimeStamp

        val dayName = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Pzt"
            Calendar.TUESDAY -> "Sal"
            Calendar.WEDNESDAY -> "Çar"
            Calendar.THURSDAY -> "Per"
            Calendar.FRIDAY -> "Cum"
            Calendar.SATURDAY -> "Cmt"
            Calendar.SUNDAY -> "Paz"
            else -> "?"
        }

        val minutes = (stat.totalTimeInForeground / 1000 / 60).toInt()
        usageMap[dayName] = usageMap[dayName]?.plus(minutes) ?: minutes
    }

    return dayLabels.map { it to (usageMap[it] ?: 0) }
}

@Composable
fun PermissionScreen(
    onNotificationPermissionGranted: () -> Unit,
    onUsagePermissionGranted: () -> Unit
) {
    val context = LocalContext.current
    var notificationPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    var usageStatsPermissionGranted by remember { mutableStateOf(false) }
    val insets = WindowInsets.systemBars.asPaddingValues()

    LaunchedEffect(Unit) {
        usageStatsPermissionGranted = hasUsageStatsPermission(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(insets)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "EYSU İzinleri",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(32.dp))

        NotificationPermissionRequester(
            isGranted = notificationPermissionGranted,
            onPermissionResult = { granted ->
                notificationPermissionGranted = granted
                if (granted) onNotificationPermissionGranted()
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        UsageStatsPermissionRequester(
            isGranted = usageStatsPermissionGranted,
            onPermissionResult = {
                usageStatsPermissionGranted = hasUsageStatsPermission(context)
                if (usageStatsPermissionGranted) onUsagePermissionGranted()
            }
        )

        Spacer(modifier = Modifier.height(36.dp))

        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = if (notificationPermissionGranted) "✔️ Bildirim İzni Verildi" else "❌ Bildirim İzni Alınmadı",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (usageStatsPermissionGranted) "✔️ Kullanım İzinleri Verildi" else "❌ Kullanım İzinleri Alınmadı",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
fun NotificationPermissionRequester(
    isGranted: Boolean,
    onPermissionResult: (Boolean) -> Unit
) {
    val launcher = usePermissionLauncher(onPermissionResult)
    val context = LocalContext.current

    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    onPermissionResult(true)
                } else {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else onPermissionResult(true)
        },
        enabled = !isGranted,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Text(text = if (isGranted) "Bildirim İzni Verildi" else "Bildirim İzni Al", color = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
fun UsageStatsPermissionRequester(
    isGranted: Boolean,
    onPermissionResult: () -> Unit
) {
    val context = LocalContext.current
    val launcher = useSettingsLauncher(onPermissionResult)

    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            if (!hasUsageStatsPermission(context)) {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                launcher.launch(intent)
            } else onPermissionResult()
        },
        enabled = !isGranted,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Text(text = if (isGranted) "Kullanım İzinleri Verildi" else "Kullanım İzinleri Al", color = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
fun usePermissionLauncher(onResult: (Boolean) -> Unit): ActivityResultLauncher<String> {
    return rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onResult(it)
    }
}

@Composable
fun useSettingsLauncher(onResult: () -> Unit): ActivityResultLauncher<Intent> {
    return rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onResult()
    }
}

fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

fun getUsagesBetween(context: Context, startTime: Long, endTime: Long): List<Triple<String, String, Long>> {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
    val event = UsageEvents.Event()

    val usageMap = mutableMapOf<String, Long>()
    val foregroundTimes = mutableMapOf<String, Long>()

    while (usageEvents.hasNextEvent()) {
        usageEvents.getNextEvent(event)
        val pkg = event.packageName ?: continue

        when (event.eventType) {
            UsageEvents.Event.MOVE_TO_FOREGROUND -> foregroundTimes[pkg] = event.timeStamp
            UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                val start = foregroundTimes[pkg]
                if (start != null) {
                    usageMap[pkg] = usageMap.getOrDefault(pkg, 0L) + (event.timeStamp - start)
                    foregroundTimes.remove(pkg)
                }
            }
        }
    }

    return usageMap.map { (pkg, time) ->
        val appName = getAppNameFromPackage(context, pkg)
        Triple(pkg, appName, time)
    }
}

fun getAppName(context: Context, packageName: String): String {
    return try {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(appInfo).toString()
    } catch (e: Exception) {
        // Hata olursa son kısmı göster (örneğin com.google.android.youtube -> youtube)
        packageName.substringAfterLast(".")
    }
}
