package com.example.esyu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

const val SERVICE_CHANNEL_ID = "usage_monitor_service_channel"
const val SERVICE_NOTIFICATION_ID = 1002
const val CHECK_INTERVAL_MS = 5 * 1000L

class UsageMonitorService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var handler: Handler
    private lateinit var runnable: Runnable

    override fun onCreate() {
        super.onCreate()
        createUsageNotificationChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(SERVICE_CHANNEL_ID) == null) {
                val serviceChannel = NotificationChannel(
                    SERVICE_CHANNEL_ID,
                    "Usage Monitor Service",
                    NotificationManager.IMPORTANCE_LOW
                )
                manager.createNotificationChannel(serviceChannel)
            }
        }
        handler = Handler(Looper.getMainLooper())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("UsageMonitorService", "Service starting...")
        val notification = createServiceNotification()
        startForeground(SERVICE_NOTIFICATION_ID, notification)
        setupPeriodicChecks()
        return START_STICKY
    }

    private fun createServiceNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setContentTitle("EYSU Arka Plan Servisi")
            .setContentText("Uygulama kullanım süreleri takip ediliyor.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun setupPeriodicChecks() {
        runnable = Runnable {
            Log.d("UsageMonitorService", "Performing periodic usage check...")
            checkUsageAndNotify()
            handler.postDelayed(runnable, CHECK_INTERVAL_MS)
        }
        handler.post(runnable)
    }

    private fun checkUsageAndNotify() {
        scope.launch {
            try {
                val oneHourAgo = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
                val hourlyUsages = getUsagesBetween(applicationContext, oneHourAgo, System.currentTimeMillis())

                hourlyUsages.forEach { (pkg, name, durationMs) ->
                    val hourlyKey = intPreferencesKey("${pkg}_hourly")
                    val hourlyLimitMinutes = applicationContext.dataStore.data.map { it[hourlyKey] }.firstOrNull()
                    val usedMinutesInHour = TimeUnit.MILLISECONDS.toMinutes(durationMs).toInt()

                    if (hourlyLimitMinutes != null && usedMinutesInHour > hourlyLimitMinutes) {
                        sendUsageLimitNotification(applicationContext, pkg, name, usedMinutesInHour.toString())
                    }

                    val dailyKey = intPreferencesKey(pkg)
                    val dailyLimitMinutes = applicationContext.dataStore.data.map { it[dailyKey] }.firstOrNull()

                    if (dailyLimitMinutes != null) {
                        val totalUsageTodayMs = getTotalUsageForPackageToday(applicationContext, pkg)
                        val totalUsedMinutesToday = TimeUnit.MILLISECONDS.toMinutes(totalUsageTodayMs).toInt()
                        if (totalUsedMinutesToday > dailyLimitMinutes) {
                            sendUsageLimitNotification(applicationContext, pkg, name, totalUsedMinutesToday.toString())
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("UsageMonitorService", "Error during usage check: ${e.localizedMessage}", e)
            }
        }
    }

    private fun getTotalUsageForPackageToday(context: Context, packageName: String): Long {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
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

        var packageUsageTime = 0L
        val foregroundTimes = mutableMapOf<String, Long>()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val currentPackageName = event.packageName ?: continue

            if (currentPackageName == packageName) {
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        foregroundTimes[currentPackageName] = event.timeStamp
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val start = foregroundTimes[currentPackageName]
                        if (start != null) {
                            packageUsageTime += (event.timeStamp - start)
                            foregroundTimes.remove(currentPackageName)
                        }
                    }
                }
            }
        }
        foregroundTimes[packageName]?.let {
            packageUsageTime += (System.currentTimeMillis() - it)
            foregroundTimes.remove(packageName)
        }
        return packageUsageTime
    }

    private fun getTodayDateKey(): String {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return "$year$month$day"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d("UsageMonitorService", "Service destroying...")
        handler.removeCallbacks(runnable)
        job.cancel()
    }
}