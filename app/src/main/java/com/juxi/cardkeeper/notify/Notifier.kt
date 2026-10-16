package com.juxi.cardkeeper.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.juxi.cardkeeper.MainActivity
import com.juxi.cardkeeper.data.CardEntity
import java.time.LocalDate

object Notifier {

    private const val CHANNEL_ID = "repay_reminder"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "还款提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "信用卡还款日到期提醒"
        }
        nm.createNotificationChannel(channel)
    }

    fun notifyCard(context: Context, card: CardEntity, daysLeft: Long) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)

        val due = card.dueEpochDay?.let(LocalDate::ofEpochDay)
        val dateText = due?.let { "${it.monthValue}月${it.dayOfMonth}日" } ?: ""
        val leftText = when {
            daysLeft < 0 -> "已逾期${-daysLeft}天"
            daysLeft == 0L -> "今天到期！"
            else -> "还有${daysLeft}天"
        }
        val amount = buildString {
            card.amountFull?.let { append("应还 ¥%,.2f".format(it)) }
            card.amountMin?.let {
                if (isNotEmpty()) append(" · ")
                append("最低 ¥%,.2f".format(it))
            }
        }
        val text = "还款日 $dateText（$leftText）$amount".trim()
        val title = "${card.bank}${if (card.tail.isNotBlank()) " 尾号${card.tail}" else ""} 还款提醒"

        val intent = PendingIntent.getActivity(
            context,
            card.id.toInt(),
            Intent(context, MainActivity::class.java).setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.notify(card.id.toInt() and 0x7fffffff, notification)
        } catch (_: SecurityException) {
        }
    }
}
