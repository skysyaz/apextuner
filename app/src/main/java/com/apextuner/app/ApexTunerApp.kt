package com.apextuner.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.apextuner.data.repository.ProfileRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application root. Bootstraps:
 *  - Hilt DI graph
 *  - WorkManager with [HiltWorkerFactory] (so [RestoreProfileWorker] can inject)
 *  - built-in profile seeding (Max Perf / Balanced / Power Save)
 *  - log retention trim
 *
 * The thermal watchdog and CPU/GPU monitors are started lazily by the
 * [TunerForegroundService] — we do NOT poll sysfs from the Application
 * context, because that would burn battery on devices where the user has
 * only opened the app to glance at the dashboard.
 */
@HiltAndroidApp
class ApexTunerApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var profileRepo: ProfileRepository
    @Inject lateinit var logs: com.apextuner.data.repository.LogRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            profileRepo.seedBuiltIns()
            // Trim logs older than 14 days on every cold start.
            val cutoff = System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000
            runCatching { logs.trimOlderThan(cutoff) }
        }
    }
}
