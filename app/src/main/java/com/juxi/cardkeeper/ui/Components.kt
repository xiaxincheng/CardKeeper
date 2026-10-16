package com.juxi.cardkeeper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.juxi.cardkeeper.data.CardEntity
import com.juxi.cardkeeper.ui.theme.LedgerColors
import com.juxi.cardkeeper.ui.theme.TabularNumbers
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

private val WEEK_CN = "一二三四五六日"

fun fmtDue(d: LocalDate): String {
    val wd = WEEK_CN[d.dayOfWeek.value - 1]
    return "${d.monthValue}月${d.dayOfMonth}日 周$wd"
}

fun fmtTime(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(millis))

fun fmtTimeSec(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date(millis))

fun fmtAmount(v: Double?): String = v?.let { "¥%,.2f".format(it) } ?: "—"

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) { Text("← 返回", fontSize = 13.sp) }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

/** 首页唯一的视觉重音：墨蓝底大字倒计时，颜色即紧急度 */
@Composable
fun HeroCard(card: CardEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val today = remember { LocalDate.now().toEpochDay() }
    val due = card.dueEpochDay?.let(LocalDate::ofEpochDay)
    val left: Int? = due?.let { (it.toEpochDay() - today).toInt() }
    val fullyPaid = card.amountFull != null && card.paidAmount >= card.amountFull!! - 0.005

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LedgerColors.Ink)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("下一笔还款", color = LedgerColors.OnInkDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton(
                onClick = onDelete,
                contentPadding = PaddingValues(0.dp)
            ) { Text("删除", fontSize = 11.sp, color = LedgerColors.OnInkDim) }
        }
        if (due != null && left != null) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    "${if (left < 0) -left else left}",
                    color = when {
                        left < 0 -> LedgerColors.OnInkOverdue
                        left <= 3 -> LedgerColors.OnInkSoon
                        else -> LedgerColors.OnInkOk
                    },
                    style = TabularNumbers.copy(fontSize = 46.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    if (left < 0) "天，已逾期" else if (left == 0) "天，就是今天到期" else "天后到期",
                    color = LedgerColors.OnInkDim, fontSize = 13.sp,
                    modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            card.bank + if (card.tail.isNotBlank()) "（尾号${card.tail}）" else "",
            color = LedgerColors.OnInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
        )
        if (card.holder.isNotBlank()) {
            Text("持卡人 ${card.holder}", color = LedgerColors.OnInkDim, fontSize = 12.sp)
        }
        due?.let {
            Text(
                "${it.monthValue}月${it.dayOfMonth}日（周${WEEK_CN[it.dayOfWeek.value - 1]}）",
                color = LedgerColors.OnInkDim, fontSize = 13.sp,
                style = TabularNumbers
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
            Text(
                "✓ 已还清，本期不再提醒",
                color = androidx.compose.ui.graphics.Color(0xFF9CD2A0), fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        } else if (card.paidAmount > 0.005) {
            val rem = card.amountFull?.let { it - card.paidAmount }
            Text(
                "已还 ${fmtAmount(card.paidAmount)}${rem?.let { "，剩余 ${fmtAmount(it)}" } ?: ""}",
                color = LedgerColors.OnInkDim, fontSize = 12.sp,
                style = TabularNumbers,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (card.billEpochDay != null && card.dueEpochDay != null) {
            val freeDays = (card.dueEpochDay - card.billEpochDay + 29L).toInt()
            if (freeDays in 40..60) {
                Text(
                    "最长免息期约 $freeDays 天",
                    color = LedgerColors.OnInkDim, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** 账本行：左侧紧急度细色条 + 发丝线分隔，金额右对齐；持卡人用黄铜色标出 */
@Composable
fun LedgerRow(card: CardEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val today = remember { LocalDate.now().toEpochDay() }
    val due = card.dueEpochDay?.let(LocalDate::ofEpochDay)
    val left: Int? = due?.let { (it.toEpochDay() - today).toInt() }
    val urgency = if (left == null || left > 7) LedgerColors.Brass
    else if (left < 0) LedgerColors.Overdue
    else if (left <= 3) LedgerColors.Soon
    else LedgerColors.Ink
    val fullyPaid = card.amountFull != null && card.paidAmount >= card.amountFull!! - 0.005

    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(urgency)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (card.holder.isNotBlank()) {
                        Text(card.holder, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LedgerColors.Brass)
                        Spacer(Modifier.width(6.dp))
                    }
                    val tag = when (card.source) {
                        "BOTH" -> " · 邮件+短信"
                        "手动" -> " · 手动"
                        else -> ""
                    }
                    Text(
                        card.bank + (if (card.tail.isNotBlank()) "  ${card.tail}" else "") + tag,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                if (due != null) {
                    val state = if (left == null) "" else if (left < 0) "，已逾期${-left}天"
                    else if (left == 0) "，今天到期" else "，还有${left}天"
                    Text(
                        "${fmtDue(due)}到期$state${if (fullyPaid) "，已还清 ✓" else ""}",
                        fontSize = 12.sp,
                        color = if (fullyPaid) androidx.compose.ui.graphics.Color(0xFF2E7D32) else urgency
                    )
                } else {
                    Text("还款日未知", fontSize = 12.sp, color = LedgerColors.TextDim)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    fmtAmount(card.amountFull),
                    style = TabularNumbers.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                )
                if (card.amountMin != null) {
                    Text(
                        "最低 ${fmtAmount(card.amountMin)}",
                        fontSize = 11.sp, color = LedgerColors.TextDim,
                        style = TabularNumbers
                    )
                }
                if (card.paidAmount > 0.005 && !fullyPaid) {
                    Text(
                        "已还 ${fmtAmount(card.paidAmount)}",
                        fontSize = 11.sp, color = LedgerColors.Brass,
                        style = TabularNumbers
                    )
                }
            }
            TextButton(
                onClick = onDelete,
                contentPadding = PaddingValues(start = 6.dp, top = 4.dp, bottom = 4.dp, end = 0.dp)
            ) {
                Text("删除", fontSize = 11.sp, color = LedgerColors.TextDim)
            }
        }
        if (card.billEpochDay != null && card.dueEpochDay != null) {
            val billDay = LocalDate.ofEpochDay(card.billEpochDay)
            val freeDays = (card.dueEpochDay - card.billEpochDay + 29L).toInt()
            if (freeDays in 40..60) {
                Text(
                    "账单日 ${billDay.monthValue}月${billDay.dayOfMonth}日，最长免息约 $freeDays 天",
                    fontSize = 11.sp, color = LedgerColors.TextDim,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}

/** 添加（initial = null）/ 编辑卡片，含持卡人姓名与信用额度 */
@Composable
fun EditCardDialog(
    initial: CardEntity?,
    onDismiss: () -> Unit,
    onSave: (holder: String, bank: String, tail: String, due: String, amount: String, limit: String) -> String?
) {
    var holder by remember { mutableStateOf(initial?.holder ?: "") }
    var bank by remember { mutableStateOf(initial?.bank ?: "") }
    var tail by remember { mutableStateOf(initial?.tail ?: "") }
    var due by remember {
        mutableStateOf(initial?.dueEpochDay?.let(LocalDate::ofEpochDay)?.toString() ?: "")
    }
    var amount by remember { mutableStateOf(initial?.amountFull?.let { "%.2f".format(it) } ?: "") }
    var limit by remember { mutableStateOf(initial?.creditLimit?.let { "%.0f".format(it) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "手动记一笔" else "修改卡片信息") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(holder, { holder = it }, label = { Text("持卡人（可不填，邮件可自动识别）") }, singleLine = true)
            OutlinedTextField(bank, { bank = it }, label = { Text("银行（如 招商银行）") }, singleLine = true)
            OutlinedTextField(tail, { tail = it }, label = { Text("卡号尾号4位（可不填）") }, singleLine = true)
            OutlinedTextField(due, { due = it }, label = { Text("还款日（如 2026-9-17）") }, singleLine = true)
            OutlinedTextField(amount, { amount = it }, label = { Text("应还金额（可不填）") }, singleLine = true)
            OutlinedTextField(limit, { limit = it }, label = { Text("信用额度（可不填）") }, singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        } },
        confirmButton = {
            TextButton(onClick = {
                val err = onSave(holder, bank, tail, due, amount, limit)
                if (err == null) onDismiss() else error = err
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 删除确认：弃用卡可一并加入忽略名单，防止旧账单邮件下次同步又把它带回来 */
@Composable
fun DeleteCardDialog(
    card: CardEntity,
    onConfirm: (alsoIgnore: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var alsoIgnore by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除 ${card.bank}${if (card.tail.isNotBlank()) "（尾号${card.tail}）" else ""}？") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(alsoIgnore, { alsoIgnore = it })
                Text("这张卡已弃用，以后不再显示它的账单", fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(alsoIgnore) }) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
