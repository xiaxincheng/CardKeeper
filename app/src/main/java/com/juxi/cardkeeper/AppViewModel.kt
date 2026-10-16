package com.juxi.cardkeeper

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.juxi.cardkeeper.data.AppDatabase
import com.juxi.cardkeeper.data.CardEntity
import com.juxi.cardkeeper.data.IgnoredCardEntity
import com.juxi.cardkeeper.data.MailboxEntity
import com.juxi.cardkeeper.data.ParseLogEntity
import com.juxi.cardkeeper.data.RepaymentEntity
import com.juxi.cardkeeper.notify.Notifier
import com.juxi.cardkeeper.notify.WorkScheduler
import com.juxi.cardkeeper.security.CryptoBox
import com.juxi.cardkeeper.sms.SmsScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 每张卡（银行+尾号）只显示最新一期账单；未还的按还款日升序在前，已还清的沉底 */
    val cards: StateFlow<List<CardEntity>> =
        db.cardDao().observeAll()
            .map { list ->
                val latest = list.groupBy { it.bank to it.tail }
                    .map { group -> group.value.maxByOrNull { it.dueEpochDay ?: Long.MIN_VALUE }!! }
                fun paid(c: CardEntity) =
                    c.amountFull != null && c.paidAmount >= c.amountFull!! - 0.005
                val byDue = compareBy<CardEntity>({ it.dueEpochDay == null }, { it.dueEpochDay ?: 0L })
                latest.filterNot(::paid).sortedWith(byDue) + latest.filter(::paid).sortedWith(byDue)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val logs: StateFlow<List<ParseLogEntity>> =
        db.parseLogDao().observeRecent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val mailboxes: StateFlow<List<MailboxEntity>> =
        db.mailboxDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val ignoredCards: StateFlow<List<IgnoredCardEntity>> =
        db.ignoredCardDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val remindDays = MutableStateFlow(prefs.getInt("remind_days", 3))
    val pdfPasswords = MutableStateFlow(prefs.getString("pdf_passwords", "") ?: "")
    val snack = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    fun setPdfPasswords(value: String) {
        pdfPasswords.value = value
        prefs.edit().putString("pdf_passwords", value).apply()
    }

    fun snack(message: String?) {
        snack.value = message
    }

    fun setRemindDays(days: Int) {
        remindDays.value = days
        prefs.edit().putInt("remind_days", days).apply()
    }

    fun onAppOpen() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            WorkScheduler.ensurePeriodic(context)
            WorkScheduler.remindNow(context)
            val boxes = db.mailboxDao().getAll()
            if (boxes.isNotEmpty()) {
                val latest = boxes.maxOf { it.lastSyncAt }
                if (System.currentTimeMillis() - latest > 6 * 3600_000L) {
                    WorkScheduler.syncAndRemind(context)
                }
            }
        }
    }

    fun saveMailbox(existingId: Long?, email: String, authCode: String, host: String, port: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = existingId?.let { db.mailboxDao().get(it) }
            val codeToStore = when {
                existingId != null && authCode.isBlank() -> existing?.authCode ?: ""
                else -> CryptoBox.encrypt(authCode.trim())
            }
            db.mailboxDao().upsert(
                MailboxEntity(
                    id = existingId ?: 0,
                    email = email.trim(),
                    authCode = codeToStore,
                    host = host.trim(),
                    port = port
                )
            )
            WorkScheduler.syncAndRemind(getApplication())
            snack.value = "邮箱已保存，正在后台同步，稍后看「日志」"
        }
    }

    fun deleteMailbox(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { db.mailboxDao().delete(id) }
    }

    fun syncNow() {
        viewModelScope.launch {
            if (db.mailboxDao().getAll().isEmpty()) {
                snack.value = "请先在「邮箱」页面添加邮箱"
                return@launch
            }
            WorkScheduler.syncAndRemind(getApplication())
            snack.value = "已开始同步，结果稍后在「日志」查看"
        }
    }

    fun scanSms() {
        val context = getApplication<Application>()
        if (!SmsScanner.hasPermission(context)) {
            snack.value = "未授予短信权限"
            return
        }
        busy.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { SmsScanner.scan(context) }
            busy.value = false
            snack.value = "短信扫描：命中 ${result.matched} 条，识别 ${result.ok} 条，未识别 ${result.skipped} 条"
        }
    }

    /** 添加（initial = null）或编辑一张卡片。@return 错误信息；null 表示成功 */
    fun saveCard(
        initial: CardEntity?,
        holder: String,
        bank: String,
        tail: String,
        dueDate: String,
        amount: String,
        limit: String
    ): String? {
        val bankTrim = bank.trim()
        if (bankTrim.isEmpty()) return "请填写银行名称"
        val due = runCatching {
            LocalDate.parse(dueDate.trim(), DateTimeFormatter.ofPattern("uuuu-M-d"))
        }.getOrNull() ?: return "还款日格式不对，请按 2026-9-17 这样填"
        val amountVal = amount.trim().replace(",", "").replace("¥", "").replace("￥", "").toDoubleOrNull()
        if (amount.isNotBlank() && amountVal == null) return "金额格式不对"
        val limitVal = limit.trim().replace(",", "").replace("¥", "").replace("￥", "").toDoubleOrNull()
        if (limit.isNotBlank() && limitVal == null) return "额度格式不对"
        viewModelScope.launch(Dispatchers.IO) {
            if (initial == null) {
                db.cardDao().upsert(
                    CardEntity(
                        bank = bankTrim,
                        tail = tail.trim(),
                        holder = holder.trim(),
                        dueEpochDay = due.toEpochDay(),
                        billEpochDay = null,
                        amountFull = amountVal,
                        amountMin = null,
                        creditLimit = limitVal,
                        source = "手动"
                    )
                )
            } else {
                db.cardDao().update(
                    initial.copy(
                        bank = bankTrim,
                        tail = tail.trim(),
                        holder = holder.trim(),
                        dueEpochDay = due.toEpochDay(),
                        amountFull = amountVal,
                        creditLimit = limitVal
                    )
                )
            }
        }
        return null
    }

    /** 删除卡片；弃用卡可一并加入忽略名单，防止旧账单下次同步又回来 */
    fun deleteCard(card: CardEntity, alsoIgnore: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            db.cardDao().delete(card.id)
            db.cardDao().deleteByBankTail(card.bank, card.tail)
            if (alsoIgnore) {
                db.ignoredCardDao().insert(IgnoredCardEntity(bank = card.bank, tail = card.tail))
            }
        }
    }

    fun unignore(bank: String, tail: String) {
        viewModelScope.launch(Dispatchers.IO) { db.ignoredCardDao().delete(bank, tail) }
    }

    /** 某个账期的还款记录流（详情页用） */
    fun observeRepayments(bank: String, tail: String, dueEpochDay: Long): kotlinx.coroutines.flow.Flow<List<RepaymentEntity>> =
        db.repaymentDao().observeFor(bank, tail, dueEpochDay)

    /** 某张卡的全部账期（往期账单，含当前期） */
    fun observeCardHistory(bank: String, tail: String): kotlinx.coroutines.flow.Flow<List<CardEntity>> =
        db.cardDao().observeFor(bank, tail)

    /** 某个账期的交易明细 */
    fun observeTransactions(bank: String, tail: String, dueEpochDay: Long): kotlinx.coroutines.flow.Flow<List<com.juxi.cardkeeper.data.TransactionEntity>> =
        db.transactionDao().observeFor(bank, tail, dueEpochDay)

    fun addRepayment(card: CardEntity, amount: Double, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.repaymentDao().insert(
                RepaymentEntity(
                    time = System.currentTimeMillis(), amount = amount, note = note.trim(),
                    bank = card.bank, tail = card.tail, dueEpochDay = card.dueEpochDay ?: 0L
                )
            )
            db.cardDao().update(card.copy(paidAmount = card.paidAmount + amount))
        }
    }

    fun deleteRepayment(card: CardEntity, repayment: RepaymentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.repaymentDao().delete(repayment.id)
            db.cardDao().update(card.copy(paidAmount = maxOf(0.0, card.paidAmount - repayment.amount)))
        }
    }

    fun clearLogs() {
        viewModelScope.launch(Dispatchers.IO) { db.parseLogDao().clear() }
    }

    fun testNotify() {
        val context = getApplication<Application>()
        val card = CardEntity(
            bank = "测试银行", tail = "0000",
            dueEpochDay = LocalDate.now().plusDays(1).toEpochDay(),
            billEpochDay = null, amountFull = 1234.56, amountMin = 123.45, source = "手动"
        )
        Notifier.notifyCard(context, card, 1)
        snack.value = "已发送测试提醒，请看通知栏"
    }
}
