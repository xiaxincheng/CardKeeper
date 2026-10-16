package com.juxi.cardkeeper.mail

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.juxi.cardkeeper.data.AppDatabase
import com.juxi.cardkeeper.data.CardEntity
import com.juxi.cardkeeper.data.CardMerger
import com.juxi.cardkeeper.data.ParseLogEntity
import com.juxi.cardkeeper.parse.BillParser
import com.juxi.cardkeeper.security.CryptoBox
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 遍历所有已配置邮箱，拉取近两个月账单邮件（含 PDF 附件）并解析入库 */
class MailSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try { PDFBoxResourceLoader.init(applicationContext) } catch (_: Exception) {}
        val prefs = applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val pdfPasswords = (prefs.getString("pdf_passwords", "") ?: "")
            .split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }

        val db = AppDatabase.get(applicationContext)
        val logDao = db.parseLogDao()
        val boxes = db.mailboxDao().getAll()
        if (boxes.isEmpty()) return@withContext Result.success()

        val today = LocalDate.now()
        for (box in boxes) {
            try {
                val mails = MailFetcher.fetch(
                    box.copy(authCode = CryptoBox.decrypt(box.authCode)),
                    daysBack = 62, pdfPasswords
                )
                var ok = 0
                for (mail in mails) {
                    val sent = Instant.ofEpochMilli(mail.sentDate).atZone(ZoneId.systemDefault()).toLocalDate()
                    val parsed = BillParser.parseEmail(
                        mail.subject, mail.fromPersonal, mail.fromAddress, mail.bodyText, sent
                    )
                    val bank = parsed.bank
                    val due = parsed.dueDate
                    val accepted = bank != null && due != null
                    val missing = buildString {
                        if (parsed.holder == null) append(" [持卡人未识别]")
                        if (parsed.creditLimit == null) append(" [额度未识别]")
                    }
                    // 失败或缺字段时附上正文片段，方便远程定位各银行的写法
                    val excerpt = if (!accepted || missing.isNotEmpty()) {
                        "\n正文片段: ${mail.bodyText.take(150).replace("\n", " ")}"
                    } else ""
                    logDao.insert(
                        ParseLogEntity(
                            time = System.currentTimeMillis(),
                            source = "EMAIL",
                            fromInfo = "${box.email} ← ${mail.fromAddress ?: "?"}",
                            subject = mail.subject.take(120),
                            ok = accepted,
                            detail = if (accepted)
                                "银行=$bank 尾号=${parsed.tail ?: "-"} 持卡人=${parsed.holder ?: "-"} 还款日=$due 应还=${parsed.amountFull ?: "-"} 最低=${parsed.amountMin ?: "-"} 额度=${parsed.creditLimit ?: "-"}$missing${if (mail.hadPdf) "（PDF账单）" else ""}$excerpt"
                            else
                                "未识别（${parsed.problems.joinToString("、")}）$excerpt"
                        )
                    )
                    if (bank != null && due != null) {
                        if (db.ignoredCardDao().count(bank, parsed.tail ?: "") > 0) {
                            logDao.insert(
                                ParseLogEntity(
                                    time = System.currentTimeMillis(), source = "EMAIL",
                                    fromInfo = mail.fromAddress ?: "?", subject = mail.subject.take(120),
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
                                source = "EMAIL"
                            )
                        )
                        // 交易明细：按账期全量重建（幂等，重复同步不产生重复行）
                        val txCtx = parsed.billDate ?: due ?: sent
                        val txs = BillParser.parseTransactions(mail.bodyText, txCtx)
                        if (txs.isNotEmpty()) {
                            val txDao = db.transactionDao()
                            txDao.deleteForBill(bank, parsed.tail ?: "", due.toEpochDay())
                            txDao.insertAll(txs.map { t ->
                                com.juxi.cardkeeper.data.TransactionEntity(
                                    bank = bank, tail = parsed.tail ?: "",
                                    dueEpochDay = due.toEpochDay(),
                                    txEpochDay = t.date?.toEpochDay(),
                                    memo = t.memo, amount = t.amount
                                )
                            })
                        }
                        ok++
                        }
                    }
                }
                db.mailboxDao().upsert(box.copy(lastSyncAt = System.currentTimeMillis(), lastError = null))
                logDao.insert(
                    ParseLogEntity(
                        time = System.currentTimeMillis(), source = "EMAIL",
                        fromInfo = box.email, subject = "同步完成", ok = true,
                        detail = "命中邮件 ${mails.size} 封，识别账单 $ok 封"
                    )
                )
            } catch (e: Exception) {
                db.mailboxDao().upsert(box.copy(lastError = e.message?.take(200)))
                logDao.insert(
                    ParseLogEntity(
                        time = System.currentTimeMillis(), source = "EMAIL",
                        fromInfo = box.email, subject = "同步失败", ok = false,
                        detail = e.message ?: e.javaClass.simpleName
                    )
                )
            }
        }
        db.cardDao().deleteOlderThan(today.minusDays(45).toEpochDay())
        Result.success()
    }
}
