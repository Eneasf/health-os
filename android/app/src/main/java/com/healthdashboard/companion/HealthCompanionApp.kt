package com.healthdashboard.companion

import android.app.Application
import com.healthdashboard.companion.worker.HealthSyncWorker

class HealthCompanionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Schedule periodic background health sync
        HealthSyncWorker.schedulePeriodicSync(this)
    }
}
