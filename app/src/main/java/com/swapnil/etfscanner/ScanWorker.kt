package com.swapnil.etfscanner

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ScanWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return when (val result = MarketRepository.fetchAndScan()) {
            is ScanResult.Success -> {
                val t = SettingsStore.load(ctx)
                notify(result.stable(t), result.falling(t))
                Result.success()
            }
            is ScanResult.Error -> {
                // Retry later in the day if the fetch failed.
                Result.retry()
            }
        }
    }

    private fun notify(stable: List<Etf>, falling: List<Etf>) {
        fun fmtList(list: List<Etf>) =
            if (list.isEmpty()) "  none"
            else list.joinToString("\n") {
                "  ${it.name.removePrefix("NSE:")}  ${fmtPct(it.oneDayChange)}" +
                "  (30d ${fmtPct(it.thirtyDayChange)})  ${it.category}"
            }

        val total = stable.size + falling.size
        val title = if (total == 0) "No ETF qualifies today"
                    else "$total ETF match(es) today"
        val body = buildString {
            append("A — dip, stable 30d (>-2.5%):\n")
            append(fmtList(stable))
            append("\n\nB — dip, falling 30d (<-2.5%):\n")
            append(fmtList(falling))
        }

        val openIntent = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(ctx, App.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentText(body)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        if (ActivityCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(ctx).notify(1001, notif)
        }
    }

    companion object {
        fun ensureChannel(ctx: Context) {
            val mgr = ctx.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                App.CHANNEL_ID,
                "Daily ETF scan",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Fires at 3:15 PM IST with qualifying ETFs" }
            mgr.createNotificationChannel(channel)
        }
    }
}
