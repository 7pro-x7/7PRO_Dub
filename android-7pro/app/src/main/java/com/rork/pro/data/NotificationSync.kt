package com.rork.pro.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Keeps notifications arriving while 7PRO is in the background or closed.
 *
 * The work is a plain periodic check against the account's own notification history, so it needs
 * no third-party messaging service and stops the moment the person signs out.
 */
class NotificationSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { PushCenter.sync(applicationContext) }
        return Result.success()
    }
}

object NotificationSync {

    private const val WORK_NAME = "7pro.notifications"

    /** Starts the background check. Calling it again keeps the existing schedule. */
    fun start(context: Context) {
        val request = PeriodicWorkRequestBuilder<NotificationSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()
        runCatching {
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }

    fun stop(context: Context) {
        runCatching { WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME) }
        PushCenter.reset(context)
    }
}
