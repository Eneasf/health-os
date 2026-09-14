package com.healthdashboard.companion.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.healthdashboard.companion.collector.CollectionResult
import com.healthdashboard.companion.collector.HealthDataCollector
import com.healthdashboard.companion.data.export.PayloadExporter
import com.healthdashboard.companion.data.token.TokenRepository
import com.healthdashboard.companion.sdk.SamsungHealthClient
import com.healthdashboard.companion.sdk.SamsungHealthPermissionManager
import java.util.concurrent.TimeUnit

class HealthSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val client = SamsungHealthClient(applicationContext)
        val permissionManager = SamsungHealthPermissionManager(applicationContext)
        val tokenRepository = TokenRepository(applicationContext)
        val exporter = PayloadExporter(applicationContext)

        val collector = HealthDataCollector(
            context = applicationContext,
            client = client,
            permissionManager = permissionManager,
            tokenRepository = tokenRepository,
            payloadExporter = exporter
        )

        return when (val result = collector.runCollectionPass(forceFullSync = false)) {
            is CollectionResult.Success -> {
                // Auto-prune old payloads to keep storage clean if enabled
                if (tokenRepository.getAutoPrunePayloads()) {
                    exporter.deleteOldestPayloads(keepCount = 3)
                }
                Result.success()
            }
            is CollectionResult.Error -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "PeriodicHealthSyncWorker"

        /**
         * Schedules recurring background sync with network connectivity and battery gating.
         * Default interval is 6 hours (customizable 1h..24h, or 0 for Manual).
         */
        fun schedulePeriodicSync(context: Context, intervalHours: Int = 6) {
            if (intervalHours <= 0) {
                cancelPeriodicSync(context)
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<HealthSyncWorker>(
                intervalHours.toLong(), TimeUnit.HOURS,
                15, TimeUnit.MINUTES // 15-minute flex interval
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                syncRequest
            )
        }

        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
