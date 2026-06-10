package com.dellvantix.kidwatch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dellvantix.kidwatch.R
import com.dellvantix.kidwatch.repository.FirebaseRepository
import com.dellvantix.kidwatch.repository.PrefsRepository
import com.dellvantix.kidwatch.util.CallLogSyncer
import com.dellvantix.kidwatch.util.SmsSyncer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class MonitorService : Service() {

    @Inject lateinit var firebaseRepo: FirebaseRepository
    @Inject lateinit var prefsRepo: PrefsRepository
    @Inject lateinit var smsSyncer: SmsSyncer
    @Inject lateinit var callLogSyncer: CallLogSyncer

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var screenshotJob: Job? = null
    private var syncJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        startSyncLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY  // Restart if killed
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─── Periodic Sync Loop ───────────────────────────────────────
    private fun startSyncLoop() {
        syncJob = serviceScope.launch {
            while (isActive) {
                try {
                    val interval = prefsRepo.getSyncIntervalMs()  // default 60s
                    syncCallLogs()
                    syncSmsLogs()
                    delay(interval)
                } catch (e: Exception) {
                    // Log but don't crash the service
                    delay(60_000L)
                }
            }
        }
    }

    // ─── SMS Sync ─────────────────────────────────────────────────
    private suspend fun syncSmsLogs() {
        val messages = smsSyncer.getNewMessages()
        if (messages.isNotEmpty()) {
            firebaseRepo.uploadSmsMessages(messages)
        }
    }

    // ─── Call Log Sync ────────────────────────────────────────────
    private suspend fun syncCallLogs() {
        val calls = callLogSyncer.getNewCalls()
        if (calls.isNotEmpty()) {
            firebaseRepo.uploadCallLogs(calls)
        }
    }

    // ─── Screenshot Capture ───────────────────────────────────────
    // Note: Screenshot capture via MediaProjection requires the user
    // to grant a one-time permission dialog each time the app starts.
    // This is an Android security requirement (cannot be bypassed).
    // The ScreenshotWorker handles scheduling via WorkManager.
    fun startScreenshotCapture(resultCode: Int, data: Intent) {
        screenshotJob = serviceScope.launch {
            val intervalMs = prefsRepo.getScreenshotIntervalMs()  // default 60s
            val worker = ScreenCaptureManager(applicationContext, resultCode, data, firebaseRepo)
            while (isActive) {
                try {
                    worker.captureAndUpload()
                    delay(intervalMs)
                } catch (e: Exception) {
                    delay(intervalMs)
                }
            }
        }
    }

    // ─── Notification ─────────────────────────────────────────────
    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("KidWatch Active")
            .setContentText("This device is being monitored by a parent.")
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "KidWatch Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Parental monitoring service"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "kidwatch_monitor"
    }
}
