package com.swapnil.etfscanner

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class App : Application() {

    companion object {
        const val CHANNEL_ID = "etf_daily_scan"
    }

    override fun onCreate() {
        super.onCreate()
        ScanWorker.ensureChannel(this)
        scheduleDailyScan()
    }

    private fun scheduleDailyScan() {
        val initialDelay = millisUntilNextAlert()
        val request = PeriodicWorkRequestBuilder<ScanWorker>(
            Duration.ofHours(24)
        ).setInitialDelay(Duration.ofMillis(initialDelay))
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_etf_scan",
            // KEEP so we don't reset the schedule on every app open.
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Milliseconds from now until the next 3:15 PM IST, regardless of device timezone. */
    private fun millisUntilNextAlert(): Long {
        val ist = ZoneId.of(StrategyConfig.IST_ZONE)
        val now = ZonedDateTime.now(ist)
        var next = now
            .withHour(StrategyConfig.ALERT_HOUR_IST)
            .withMinute(StrategyConfig.ALERT_MINUTE_IST)
            .withSecond(0)
            .withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }
}
