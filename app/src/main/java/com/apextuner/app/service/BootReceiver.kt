package com.apextuner.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.apextuner.app.work.RestoreProfileWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Boot receiver. Schedules a one-shot [RestoreProfileWorker] that re-applies
 * the user's chosen boot profile (if "apply on boot" is enabled) and
 * restarts the [TunerForegroundService]. We do the actual work in WorkManager
 * because BOOT_COMPLETED has a 10-second foreground limit — too short for
 * the profile-apply sequence.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var workManager: WorkManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        val req = OneTimeWorkRequestBuilder<RestoreProfileWorker>().build()
        workManager.enqueue(req)
    }
}
