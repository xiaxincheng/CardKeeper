package com.juxi.cardkeeper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juxi.cardkeeper.AppViewModel
import com.juxi.cardkeeper.data.CardEntity
import com.juxi.cardkeeper.ui.theme.LedgerColors
import com.juxi.cardkeeper.ui.theme.TabularNumbers
import java.time.LocalDate

/** 卡片详情：账期信息、记还款、还款历史、额度与免息期 */
@Composable
fun DetailScreen(vm: AppViewModel, cardId: Long, onBack: () -> Unit) {
    val cards by vm.cards.collectAsState()
    val card = cards.firstOrNull { it.id == cardId }
    if (card == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val today = remember { LocalDate.now().toEpochDay() }
    val due = card.dueEpochDay?.let(LocalDate::ofEpochDay)
    val left: Int? = due?.let { (it.toEpochDay() - today).toInt() }
    val fullyPaid = card.amountFull != null && card.paidAmount >= card.amountFull!! - 0.005
    val remaining = card.amountFull?.let { maxOf(0.0, it - card.paidAmount) }
    val freeDays = if (card.billEpochDay != null && card.dueEpochDay != null) {
        (card.dueEpochDay - card.billEpochDay + 29L).toInt().takeIf { it in 20..70 }
    } else null

    val repayments by remember(card.bank, card.tail, card.dueEpochDay) {
        vm.observeRepayments(card.bank, card.tail, card.dueEpochDay ?: 0L)
    }.collectAsState(initial = emptyList())
    val history by remember(card.bank, card.tail) {
        vm.observeCardHistory(card.bank, card.tail)
    }.collectAsState(initial = emptyList())
    val transactions by remember(card.bank, card.tail, card.dueEpochDay) {
        vm.observeTransactions(card.bank, card.tail, card.dueEpochDay ?: 0L)
    }.collectAsState(initial = emptyList())

    var showPayDialog by remember { mutableStateOf(false) }
    var payDefault by remember { mutableStateOf<Double?>(null) }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(
            card.bank + if (card.tail.isNotBlank()) "（尾号${card.tail}）" else "",
            onBack
        )
        Column(Modifier.padding(horizontal = 16.dp)) {
            // 账期主卡
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(LedgerColors.Ink)
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("本期账单", color = LedgerColors.OnInkDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    if (card.holder.isNotBlank()) {
                        Text(card.holder, color = LedgerColors.OnInkDim, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (due != null && left != null) {
                    val stateText = if (left < 0) "已逾期${-left}天" else if (left == 0) "今天到期" else "还有${left}天"
                    val stateColor = when {
                        fullyPaid -> androidx.compose.ui.graphics.Color(0xFF9CD2A0)
                        left < 0 -> LedgerColors.OnInkOverdue
                        left <= 3 -> LedgerColors.OnInkSoon
                        else -> LedgerColors.OnInk
                    }
                    Text(
                        "${fmtDue(due)} · $stateText",
                        color = stateColor, fontSize = 20.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "应还 ${fmtAmount(card.amountFull)}",
                        color = LedgerColors.OnInk,
                        style = TabularNumbers.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    )
                    card.amountMin?.let {
                        Text(
                            "最低 ${fmtAmount(it)}",
                            color = LedgerColors.OnInkDim, fontSize = 12.sp,
                            style = TabularNumbers,
                            modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
                        )
                    }
                }
                if (fullyPaid) {
                    Text("✓ 已还清，本期不再提醒", color = androidx.compose.ui.graphics.Color(0xFF9CD2A0), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                } else if (card.paidAmount > 0.005) {
                    Text(
                        "已还 ${fmtAmount(card.paidAmount)}${remaining?.let { "，剩余 ${fmtAmount(it)}" } ?: ""}",
                        color = LedgerColors.OnInkDim, fontSize = 12.sp,
                        style = TabularNumbers,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row {
                Button(
                    onClick = { payDefault = remaining; showPayDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = LedgerColors.Ink)
                ) { Text("记一笔还款") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        val r = remaining
                        if (r != null && r > 0.005) {
                            vm.addRepayment(card, r, "还清")
                            vm.snack("已记录还清 ${fmtAmount(r)}")
                        }
                    },
                    enabled = remaining != null && remaining > 0.005
                ) { Text("还清全额", fontSize = 13.sp) }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        val min = card.amountMin
                        if (min != null) {
                            vm.addRepayment(card, min, "还最低")
                            vm.snack("已记录还最低 ${fmtAmount(min)}")
                        }
                    },
                    enabled = card.amountMin != null && card.paidAmount < (card.amountMin ?: 0.0) - 0.005
                ) { Text("还最低", fontSize = 13.sp) }
            }
            Row(modifier = Modifier.padding(top = 4.dp)) {
                TextButton(onClick = { showEdit = true }) { Text("编辑卡片信息", fontSize = 13.sp) }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = { showDelete = true }) {
                    Text("删除 / 弃用", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("还款记录", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (repayments.isEmpty()) {
                Text(
                    "暂无记录。还款后点「记一笔还款」，还清后本期不再提醒。",
                    fontSize = 12.sp, color = LedgerColors.TextDim,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                repayments.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.note.ifEmpty { "还款" }, fontSize = 13.sp)
                            Text(fmtTime(r.time), fontSize = 11.sp, color = LedgerColors.TextDim)
                        }
                        Text(
                            fmtAmount(r.amount),
                            style = TabularNumbers.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        )
                        TextButton(onClick = { vm.deleteRepayment(card, r) }) {
                            Text("撤销", fontSize = 11.sp, color = LedgerColors.TextDim)
                        }
                    }
                    HorizontalDivider(color = LedgerColors.Hairline, thickness = 0.5.dp)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("卡片信息", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            InfoRow("持卡人", card.holder.ifEmpty { "—" })
            InfoRow("信用额度", card.creditLimit?.let { fmtAmount(it) } ?: "未识别")
            InfoRow("最长免息期", freeDays?.let { "约 $it 天" } ?: "需账单日")
            InfoRow("账单日", card.billEpochDay?.let(LocalDate::ofEpochDay)?.let { "${it.monthValue}月${it.dayOfMonth}日" } ?: "—")
            InfoRow(
                "来源",
                when (card.source) {
                    "EMAIL" -> "邮件"; "SMS" -> "短信"; "BOTH" -> "邮件+短信"; else -> "手动"
                }
            )
            Spacer(Modifier.height(12.dp))

            // 本期交易明细
            Text("本期交易（${transactions.size} 笔）", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (transactions.isEmpty()) {
                Text(
                    "此账单未解析到交易明细",
                    fontSize = 12.sp, color = LedgerColors.TextDim,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            } else {
                transactions.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.memo, fontSize = 13.sp, maxLines = 1)
                            Text(
                                t.txEpochDay?.let(LocalDate::ofEpochDay)
                                    ?.let { "${it.monthValue}月${it.dayOfMonth}日" } ?: "—",
                                fontSize = 11.sp, color = LedgerColors.TextDim
                            )
                        }
                        Text(
                            fmtAmountSigned(t.amount),
                            style = TabularNumbers.copy(fontSize = 13.sp),
                            color = if (t.amount < 0) LedgerColors.Brass else androidx.compose.ui.graphics.Color.Unspecified
                        )
                    }
                    HorizontalDivider(color = LedgerColors.Hairline, thickness = 0.5.dp)
                }
            }
            Spacer(Modifier.height(12.dp))

            // 往期账单
            if (history.size > 1) {
                Text("往期账单", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                history.drop(1).forEach { h ->
                    val hPaid = h.amountFull != null && h.paidAmount >= h.amountFull!! - 0.005
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            h.dueEpochDay?.let(LocalDate::ofEpochDay)
                                ?.let { "${it.year}年${it.monthValue}月${it.dayOfMonth}日到期" } ?: "还款日未知",
                            fontSize = 12.sp, modifier = Modifier.weight(1f)
                        )
                        Text(
                            buildString {
                                append("应还 ${fmtAmount(h.amountFull)}")
                                if (h.paidAmount > 0.005) append("  已还 ${fmtAmount(h.paidAmount)}")
                                if (hPaid) append(" ✓")
                            },
                            fontSize = 12.sp, color = LedgerColors.TextDim,
                            style = TabularNumbers
                        )
                    }
                    HorizontalDivider(color = LedgerColors.Hairline, thickness = 0.5.dp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPayDialog) {
        AddPaymentDialog(
            defaultAmount = payDefault,
            onDismiss = { showPayDialog = false },
            onSave = { amount, note ->
                val v = amount.trim().replace(",", "").toDoubleOrNull()
                if (v == null || v <= 0) "金额不对" else {
                    vm.addRepayment(card, v, note)
                    null
                }
            }
        )
    }
    if (showEdit) {
        EditCardDialog(
            initial = card,
            onDismiss = { showEdit = false },
            onSave = { holder, bank, tail, dueStr, amount, limit ->
                vm.saveCard(card, holder, bank, tail, dueStr, amount, limit)
            }
        )
    }
    if (showDelete) {
        DeleteCardDialog(
            card = card,
            onConfirm = { alsoIgnore ->
                vm.deleteCard(card, alsoIgnore)
                showDelete = false
                onBack()
            },
            onDismiss = { showDelete = false }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontSize = 13.sp, color = LedgerColors.TextDim, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp)
    }
}

private fun fmtAmountSigned(v: Double): String =
    if (v < 0) "-¥%,.2f".format(-v) else "¥%,.2f".format(v)

@Composable
private fun AddPaymentDialog(
    defaultAmount: Double?,
    onDismiss: () -> Unit,
    onSave: (amount: String, note: String) -> String?
) {
    var amount by remember { mutableStateOf(defaultAmount?.let { "%.2f".format(it) } ?: "") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记一笔还款") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(amount, { amount = it }, label = { Text("还款金额") }, singleLine = true)
            OutlinedTextField(note, { note = it }, label = { Text("备注（可不填）") }, singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        } },
        confirmButton = {
            TextButton(onClick = {
                val err = onSave(amount, note)
                if (err == null) onDismiss() else error = err
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
