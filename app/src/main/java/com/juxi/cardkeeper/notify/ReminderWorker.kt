package com.juxi.cardkeeper.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.juxi.cardkeeper.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** 检查所有卡片，对临期且尚未提醒过（本账期）的卡弹通知 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val remindDays = prefs.getInt("remind_days", 3).toLong()

        val db = AppDatabase.get(context)
        val today = LocalDate.now().toEpochDay()
        val cards = db.cardDao().upcoming(today - 10)

        for (card in cards) {
            val due = card.dueEpochDay ?: continue
            // 已还清的账期不再提醒
            val fullyPaid = card.amountFull != null && card.paidAmount >= card.amountFull!! - 0.005
            if (fullyPaid) continue
            if (card.lastNotifiedDue == due) continue
            val daysLeft = due - today
            if (daysLeft in 0..remindDays || daysLeft < 0) {
                Notifier.notifyCard(context, card, daysLeft)
                db.cardDao().markNotified(card.id, due)
            }
        }
        Result.success()
    }
}
