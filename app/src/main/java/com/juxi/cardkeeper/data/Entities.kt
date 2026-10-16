package com.juxi.cardkeeper.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一张信用卡的一个账期。同一银行+尾号+还款日 唯一，重复解析会覆盖旧记录。
 */
@Entity(tableName = "cards", indices = [Index(value = ["bank", "tail", "dueEpochDay"], unique = true)])
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bank: String,
    val tail: String,
    /** 持卡人姓名：从邮件称呼解析或手动填写，一个邮箱可能收多人的账单 */
    val holder: String = "",
    /** 到期还款日（epochDay），提醒的核心依据 */
    val dueEpochDay: Long?,
    val billEpochDay: Long?,
    val amountFull: Double?,
    val amountMin: Double?,
    /** 本账期已还金额（还款记录累计） */
    @ColumnInfo(defaultValue = "0.0")
    val paidAmount: Double = 0.0,
    /** 信用额度（从账单“信用额度/固定额度”解析或手动填写） */
    val creditLimit: Double? = null,
    val source: String, // EMAIL / SMS / 手动
    val lastNotifiedDue: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "mailboxes", indices = [Index(value = ["email"], unique = true)])
data class MailboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val email: String,
    /** 邮箱授权码（不是登录密码） */
    val authCode: String,
    val host: String,
    val port: Int = 993,
    val lastSyncAt: Long = 0,
    val lastError: String? = null
)

/** 每封命中邮件/短信的解析结果，方便排查“为什么没识别出来” */
@Entity(tableName = "parse_logs")
data class ParseLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val source: String, // EMAIL / SMS
    val fromInfo: String,
    val subject: String,
    val ok: Boolean,
    val detail: String
)

/** 弃用的卡：命中忽略名单（银行+尾号）的账单不再生成卡片记录 */
@Entity(tableName = "ignored_cards", primaryKeys = ["bank", "tail"])
data class IgnoredCardEntity(
    val bank: String,
    val tail: String,
    val addedAt: Long = System.currentTimeMillis()
)

/** 一笔还款记录；账期归属 = 银行+尾号+还款日 */
@Entity(tableName = "repayments")
data class RepaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val amount: Double,
    val note: String = "",
    val bank: String,
    val tail: String,
    val dueEpochDay: Long
)

/** 账单里的一笔交易明细；同步时按账期全量重建 */
@Entity(
    tableName = "transactions",
    indices = [Index(value = ["bank", "tail", "dueEpochDay"])]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bank: String,
    val tail: String,
    val dueEpochDay: Long,
    val txEpochDay: Long?,
    val memo: String,
    val amount: Double
)
