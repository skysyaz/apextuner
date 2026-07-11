package com.apextuner.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.apextuner.app.service.TunerForegroundService
import com.apextuner.data.datastore.SettingsDataStore
import com.apextuner.data.model.TunerLog
import com.apextuner.data.repository.LogRepository
import com.apextuner.engine.profile.ProfileApplier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Worker that restores the last safe profile after a process death or boot.
 * Triggered by:
 *  - [com.apextuner.app.service.BootReceiver] (BOOT_COMPLETED)
 *  - WorkManager's own restart-on-crash semantics (the worker is enqueued
 *    once at app start by [com.apextuner.app.ApexTunerApp] in a real build)
 *
 * If "apply on boot" is disabled, the worker only starts the foreground
 * service (so the thermal watchdog runs) without touching sysfs.
 */
@HiltWorker
class RestoreProfileWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsDataStore,
    private val profileApplier: ProfileApplier,
    private val logs: LogRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val snap = settings.snapshot.first()
        return try {
            if (snap.applyOnBoot) {
                val id = snap.bootProfileId.takeIf { it != 0L } ?: snap.lastSafeProfileId
                if (id != 0L) {
                    logs.log(
                        level = TunerLog.Level.INFO,
                        category = TunerLog.Category.SAFETY,
                        message = "RestoreProfileWorker: re-applying profile $id after boot/crash"
                    )
                    profileApplier.applyById(id)
                }
            }
            // Always restart the foreground service so the watchdog survives.
            if (snap.watchdogEnabled || snap.activeProfileId != 0L) {
                TunerForegroundService.start(applicationContext)
            }
            Result.success()
        } catch (t: Throwable) {
            logs.log(
                level = TunerLog.Level.ERROR,
                category = TunerLog.Category.SAFETY,
                message = "RestoreProfileWorker failed: ${t.message}"
            )
            Result.retry()
        }
    }
}
