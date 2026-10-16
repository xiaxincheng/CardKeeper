package com.juxi.cardkeeper.data

/**
 * 同一笔账单（同银行 + 尾号 + 还款日）可能同时来自邮件 PDF 和短信：
 * 字段级合并取并集，谁先到都不丢信息；两个来源都出现过时标记为“邮件+短信”。
 */
object CardMerger {

    suspend fun upsertMerged(dao: CardDao, incoming: CardEntity) {
        val due = incoming.dueEpochDay ?: return
        val existing = dao.findSameBill(incoming.bank, incoming.tail, due)
        if (existing == null) {
            dao.upsert(incoming)
            return
        }
        val mergedSource = when {
            existing.source == incoming.source -> existing.source
            existing.source == "手动" || incoming.source == "手动" -> existing.source
            else -> "BOTH"
        }
        dao.update(
            existing.copy(
                billEpochDay = incoming.billEpochDay ?: existing.billEpochDay,
                holder = incoming.holder.ifEmpty { existing.holder },
                amountFull = incoming.amountFull ?: existing.amountFull,
                amountMin = incoming.amountMin ?: existing.amountMin,
                creditLimit = incoming.creditLimit ?: existing.creditLimit,
                source = mergedSource,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
