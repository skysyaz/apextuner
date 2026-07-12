package com.apextuner.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.apextuner.app.MainActivity
import com.apextuner.app.R
import com.apextuner.data.datastore.SettingsDataStore
import com.apextuner.data.model.TunerLog
import com.apextuner.data.repository.LogRepository
import com.apextuner.data.repository.ProfileRepository
import com.apextuner.engine.cpu.CpuMonitor
import com.apextuner.engine.gaming.GamingModeController
import com.apextuner.engine.profile.ProfileApplier
import com.apextuner.engine.thermal.ThermalMonitor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The persistent tuner foreground service. Started whenever a profile is
 * active (Max Performance, Balanced, Power Save, or any user profile) OR
 * Gaming Mode is on. Responsibilities:
 *
 *  1. Keep the thermal watchdog alive so the auto-revert path works even
 *     when the app is backgrounded.
 *  2. Keep the Gaming Mode detector polling.
 *  3. Surface a sticky notification disclosing the active profile (Play
 *     policy requirement for FGS_SPECIAL_USE).
 *
 * The service stops itself when the user applies the "off" state or revokes
 * the foreground service. WorkManager's [RestoreProfileWorker] restarts it
 * after a process death.
 */
@AndroidEntryPoint
class TunerForegroundService : LifecycleService() {

    @Inject lateinit var settings: SettingsDataStore
    @Inject lateinit var logs: LogRepository
    @Inject lateinit var profileRepo: ProfileRepository
    @Inject lateinit var profileApplier: ProfileApplier
    @Inject lateinit var thermalMonitor: ThermalMonitor
    @Inject lateinit var cpuMonitor: CpuMonitor
    @Inject lateinit var gamingMode: GamingModeController

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification("ApexTuner active"))
        lifecycleScope.launch {
            thermalMonitor.start()
            cpuMonitor.start()
            gamingMode.start()
            val snap = settings.snapshot.first()
            val profile = profileRepo.getById(snap.activeProfileId)
            val name = profile?.name ?: "Balanced"
            startForeground(NOTIFICATION_ID, buildNotification("Profile: $name"))
            logs.log(
                level = TunerLog.Level.INFO,
                category = TunerLog.Category.SYSTEM,
                message = "Tuner foreground service started",
                detail = "activeProfile=${snap.activeProfileId}"
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        gamingMode.stop()
        thermalMonitor.stop()
        cpuMonitor.stop()
        lifecycleScope.launch {
            logs.log(TunerLog.Level.INFO, TunerLog.Category.SYSTEM, "Tuner foreground service stopped")
        }
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ApexTuner")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pi)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "ApexTuner tuner service",
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "Persistent notification while a tuning profile is active"
                setShowBadge(false)
            }
        )
    }

    companion object {
        const val NOTIFICATION_ID = 0xA001
        const val CHANNEL_ID = "apextuner.tuner"

        fun start(context: Context) {
            val intent = Intent(context, TunerForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TunerForegroundService::class.java))
        }
    }
}
