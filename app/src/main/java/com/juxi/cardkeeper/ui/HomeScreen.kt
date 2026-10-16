package com.juxi.cardkeeper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juxi.cardkeeper.AppViewModel
import com.juxi.cardkeeper.Screen
import com.juxi.cardkeeper.data.CardEntity
import com.juxi.cardkeeper.sms.SmsScanner
import com.juxi.cardkeeper.ui.theme.LedgerColors

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onNavigate: (Screen) -> Unit,
    onRequestSms: () -> Unit,
    onOpenDetail: (Long) -> Unit
) {
    val cards by vm.cards.collectAsState()
    val mailboxes by vm.mailboxes.collectAsState()
    val busy by vm.busy.collectAsState()
    val context = LocalContext.current
    var manualAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<CardEntity?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 标题行：安静，只是路标
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "还卡管家",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = LedgerColors.Ink,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { onNavigate(Screen.RANK) }) { Text("排行", fontSize = 13.sp) }
            TextButton(onClick = { onNavigate(Screen.MAIL) }) { Text("邮箱", fontSize = 13.sp) }
            TextButton(onClick = { onNavigate(Screen.LOGS) }) { Text("日志", fontSize = 13.sp) }
            TextButton(onClick = { onNavigate(Screen.SETTINGS) }) { Text("设置", fontSize = 13.sp) }
        }

        // 邮箱出错信息：放在正文前，说清是什么问题
        mailboxes.filter { it.lastError != null }.forEach {
            Text(
                "⚠ ${it.email} 上次出错：${it.lastError}",
                Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.error
            )
        }

        if (cards.isNotEmpty()) {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
            ) {
                item {
                    HeroCard(
                        cards.first(),
                        onClick = { onOpenDetail(cards.first().id) },
                        onDelete = { deleteTarget = cards.first() }
                    )
                }
                item {
                    val latest = mailboxes.maxOfOrNull { it.lastSyncAt } ?: 0L
                    val syncNote = if (latest > 0) "，最近同步 ${fmtTime(latest)}" else ""
                    Text(
                        "其余 ${cards.size - 1} 张卡在排期$syncNote",
                        Modifier.padding(start = 4.dp, top = 16.dp, bottom = 2.dp),
                        fontSize = 11.sp, color = LedgerColors.TextDim
                    )
                }
                items(cards.drop(1), key = { it.id }) { card ->
                    Column {
                        LedgerRow(
                            card,
                            onClick = { onOpenDetail(card.id) },
                            onDelete = { deleteTarget = card }
                        )
                        HorizontalDivider(color = LedgerColors.Hairline, thickness = 0.5.dp)
                    }
                }
            }
        } else {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (mailboxes.isEmpty()) {
                    SetupGuide { onNavigate(Screen.MAIL) }
                } else {
                    Text(
                        "还没有识别到账单\n点下方「同步邮件账单」试一次\n没结果就去「日志」看原因",
                        modifier = Modifier.padding(top = 48.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = LedgerColors.TextDim
                    )
                }
            }
        }

        // 底部固定操作区：拇指够得着，保持安静
        Column(Modifier.fillMaxWidth()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            HorizontalDivider(color = LedgerColors.Hairline)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { vm.syncNow() },
                    colors = ButtonDefaults.buttonColors(containerColor = LedgerColors.Ink)
                ) { Text("同步邮件账单") }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = {
                    if (SmsScanner.hasPermission(context)) vm.scanSms() else onRequestSms()
                }) { Text("扫描短信", fontSize = 13.sp) }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = { manualAdd = true }) {
                    Text("手动记一笔", fontSize = 13.sp, color = LedgerColors.Brass)
                }
            }
        }
    }

    if (manualAdd) {
        EditCardDialog(
            initial = null,
            onDismiss = { manualAdd = false },
            onSave = { holder, bank, tail, due, amount, limit ->
                vm.saveCard(null, holder, bank, tail, due, amount, limit)
            }
        )
    }
    deleteTarget?.let { target ->
        DeleteCardDialog(
            card = target,
            onConfirm = { alsoIgnore ->
                vm.deleteCard(target, alsoIgnore)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun SetupGuide(onGo: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("三步开始", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LedgerColors.Brass)
        Text(
            "1  在电脑上登录邮箱网页版，开启 IMAP 服务并生成「授权码」\n" +
                "2  回到「邮箱」页，填入邮箱地址和授权码\n" +
                "3  App 自动拉取近两个月的账单邮件，解析还款日并提醒",
            fontSize = 14.sp, lineHeight = 24.sp
        )
        Button(
            onClick = onGo,
            colors = ButtonDefaults.buttonColors(containerColor = LedgerColors.Ink),
            modifier = Modifier.padding(top = 4.dp)
        ) { Text("去配置邮箱") }
    }
}
