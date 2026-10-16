package com.juxi.cardkeeper.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.juxi.cardkeeper.data.AppDatabase
import com.juxi.cardkeeper.data.CardEntity
import com.juxi.cardkeeper.data.CardMerger
import com.juxi.cardkeeper.data.ParseLogEntity
import com.juxi.cardkeeper.parse.Banks
import com.juxi.cardkeeper.parse.BillParser
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object SmsScanner {

    data class ScanResult(val matched: Int, val ok: Int, val skipped: Int)

    private val GATE_KEYWORDS = listOf("还款", "账单", "应还", "最低还款", "逾期")

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    suspend fun scan(context: Context, daysBack: Int = 100): ScanResult {
        if (!hasPermission(context)) return ScanResult(0, 0, 0)
        val db = AppDatabase.get(context)
        var matched = 0
        var ok = 0
        var skipped = 0
        val since = System.currentTimeMillis() - daysBack * 24L * 3600L * 1000L
        val cursor = context.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("address", "body", "date"),
            "date > ?",
            arrayOf(since.toString()),
            "date DESC"
        ) ?: return ScanResult(0, 0, 0)

        cursor.use { c ->
            while (c.moveToNext()) {
                val address = c.getString(0) ?: ""
                val body = c.getString(1) ?: continue
                val date = c.getLong(2)
                if (GATE_KEYWORDS.none { body.contains(it) }) continue
                if (!body.contains("信用卡") && !body.contains("贷记卡")) continue
                matched++
                val smsDate = Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault()).toLocalDate()

                var parsed = BillParser.parseSmsBody(body, smsDate)
                if (parsed == null) {
                    val bankFromNumber = Banks.fromSmsAddress(address)
                    if (bankFromNumber == null) {
                        skipped++
                        continue
                    }
                    parsed = BillParser.Parsed(bank = bankFromNumber)
                    BillParser.parseInto(parsed, body, smsDate)
                }

                val bank = parsed.bank
                val due = parsed.dueDate
                val accepted = bank != null && due != null
                db.parseLogDao().insert(
                    ParseLogEntity(
                        time = System.currentTimeMillis(), source = "SMS",
                        fromInfo = address.take(30), subject = body.take(40).replace("\n", " "),
                        ok = accepted,
                        detail = if (accepted)
                            "银行=$bank 尾号=${parsed.tail ?: "-"} 还款日=$due 应还=${parsed.amountFull ?: "-"} 最低=${parsed.amountMin ?: "-"}"
                        else
                            "命中关键词但未解析出还款日（${parsed.problems.joinToString("、")}）"
                    )
                )
                if (bank != null && due != null) {
                    if (db.ignoredCardDao().count(bank, parsed.tail ?: "") > 0) {
                        db.parseLogDao().insert(
                            ParseLogEntity(
                                time = System.currentTimeMillis(), source = "SMS",
                                fromInfo = address.take(30), subject = body.take(40).replace("\n", " "),
                                ok = false, detail = "已跳过：此卡被标记为弃用"
                            )
                        )
                    } else {
                        CardMerger.upsertMerged(db.cardDao(), CardEntity(
                            bank = bank,
                            tail = parsed.tail ?: "",
                            holder = parsed.holder ?: "",
                            dueEpochDay = due.toEpochDay(),
                            billEpochDay = parsed.billDate?.toEpochDay(),
                            amountFull = parsed.amountFull,
                            amountMin = parsed.amountMin,
                            creditLimit = parsed.creditLimit,
                            source = "SMS"
                        )
                    )
                    }
                    ok++
                } else skipped++
            }
        }
        return ScanResult(matched, ok, skipped)
    }
}
