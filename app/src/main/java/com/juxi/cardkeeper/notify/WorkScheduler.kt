package com.juxi.cardkeeper.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.juxi.cardkeeper.mail.MailSyncWorker
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object WorkScheduler {

    private fun delayUntil(time: LocalTime): Duration {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    /** 每天早上固定时间后台同步 + 检查提醒（KEEP：重复调用不会重置已排任务） */
    fun ensurePeriodic(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            "mail_sync_daily", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MailSyncWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayUntil(LocalTime.of(9, 0)))
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
        )
        wm.enqueueUniquePeriodicWork(
            "remind_daily", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayUntil(LocalTime.of(9, 30)))
                .build()
        )
    }

    /** 手动刷新：先同步邮件，完成后立即检查一遍提醒 */
    fun syncAndRemind(context: Context) {
        val wm = WorkManager.getInstance(context)
        val sync = OneTimeWorkRequestBuilder<MailSyncWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        val remind = OneTimeWorkRequestBuilder<ReminderWorker>().build()
        wm.beginUniqueWork("manual_refresh", ExistingWorkPolicy.REPLACE, sync)
            .then(remind)
            .enqueue()
    }

    fun remindNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "remind_now", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>().build()
        )
    }
}
