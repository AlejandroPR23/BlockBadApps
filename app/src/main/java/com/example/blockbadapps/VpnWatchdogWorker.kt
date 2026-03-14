package com.example.blockbadapps

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Runs every 15 minutes (the minimum WorkManager interval).
 * If the VPN is supposed to be running but isn't, it restarts it.
 *
 * Why WorkManager instead of a plain Service timer?
 * WorkManager survives reboots, app kills, and Doze mode — a Handler.postDelayed doesn't.
 */
class VpnWatchdogWorker(
    private val context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val vpnShouldBeRunning = prefs.getBoolean(PREF_VPN_ENABLED, false)

        if (vpnShouldBeRunning && !BlockingVpnService.isRunning) {
            Log.d("VpnWatchdog", "VPN is down but should be running — restarting")
            val intent = Intent(context, BlockingVpnService::class.java).apply {
                action = BlockingVpnService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        return Result.success()
    }

    companion object {
        const val PREF_VPN_ENABLED = "vpn_should_be_running"
        private const val WORK_NAME = "vpn_watchdog"

        /** Call once when the user activates the VPN. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<VpnWatchdogWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,   // Don't reset timer if already scheduled
                request
            )
        }

        /** Call when the user deliberately deactivates the VPN. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}