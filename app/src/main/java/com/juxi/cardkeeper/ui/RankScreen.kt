package com.juxi.cardkeeper.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juxi.cardkeeper.AppViewModel
import com.juxi.cardkeeper.data.CardEntity
import com.juxi.cardkeeper.ui.theme.LedgerColors
import com.juxi.cardkeeper.ui.theme.TabularNumbers

private data class RankRow(val card: CardEntity, val freeDays: Int?)

/** 卡片排行：按最长免息期降序，同时展示信用额度 */
@Composable
fun RankScreen(vm: AppViewModel, onBack: () -> Unit) {
    val cards by vm.cards.collectAsState()
    val rows = cards.map { c ->
        val fd = if (c.billEpochDay != null && c.dueEpochDay != null) {
            (c.dueEpochDay - c.billEpochDay + 29L).toInt().takeIf { it in 20..70 }
        } else null
        RankRow(c, fd)
    }.sortedWith(
        compareByDescending<RankRow> { it.freeDays ?: -1 }
            .thenByDescending { it.card.creditLimit ?: -1.0 }
    )

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("卡排行", onBack)
        Text(
            "按最长免息期从长到短；免息期 = 账单日次日消费到还款日",
            Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            fontSize = 11.sp, color = LedgerColors.TextDim
        )
        if (rows.isEmpty()) {
            Text(
                "还没有卡片",
                Modifier.padding(16.dp),
                fontSize = 13.sp, color = LedgerColors.TextDim
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                itemsIndexed(rows, key = { _, r -> r.card.id }) { index, r ->
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${index + 1}",
                                style = TabularNumbers.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                                color = if (index == 0) LedgerColors.Brass else LedgerColors.TextDim
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (r.card.holder.isNotBlank()) {
                                        Text(r.card.holder, fontSize = 12.sp, color = LedgerColors.Brass, fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(
                                        r.card.bank + if (r.card.tail.isNotBlank()) "  ${r.card.tail}" else "",
                                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    "额度 ${r.card.creditLimit?.let { fmtAmount(it) } ?: "未识别"}",
                                    fontSize = 11.sp, color = LedgerColors.TextDim,
                                    style = TabularNumbers
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    r.freeDays?.let { "$it 天" } ?: "—",
                                    style = TabularNumbers.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                                    color = if (r.freeDays != null) MaterialTheme.colorScheme.primary else LedgerColors.TextDim
                                )
                                Text("最长免息", fontSize = 10.sp, color = LedgerColors.TextDim)
                            }
                        }
                        HorizontalDivider(color = LedgerColors.Hairline, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}
