package com.juxi.cardkeeper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juxi.cardkeeper.AppViewModel

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val days by vm.remindDays.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("设置", onBack)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("提前提醒天数", fontSize = 15.sp)
                    Text(
                        "还款日前 N 天开始每天弹提醒",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        TextButton(onClick = { if (days > 1) vm.setRemindDays(days - 1) }) {
                            Text("−", fontSize = 20.sp)
                        }
                        Text("$days 天", fontSize = 16.sp, modifier = Modifier.padding(horizontal = 12.dp))
                        TextButton(onClick = { if (days < 10) vm.setRemindDays(days + 1) }) {
                            Text("＋", fontSize = 20.sp)
                        }
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("账单 PDF 密码", fontSize = 15.sp)
                    Text(
                        "有些银行把账单做成加密 PDF 附件，密码常见为身份证号或预留手机号。填在这里，多个用逗号分隔；没收到加密账单就留空。",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val pw by vm.pdfPasswords.collectAsState()
                    OutlinedTextField(
                        pw,
                        { vm.setPdfPasswords(it) },
                        label = { Text("PDF 密码（可不填）") },
                        singleLine = true
                    )
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("已弃用的卡片", fontSize = 15.sp)
                    Text(
                        "删除时勾选「已弃用」的卡会进这里，邮件和短信再来账单也不再生成记录",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val ignored by vm.ignoredCards.collectAsState()
                    if (ignored.isEmpty()) {
                        Text("（空）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ignored.forEach { ig ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                ig.bank + if (ig.tail.isNotBlank()) "（尾号${ig.tail}）" else "",
                                fontSize = 13.sp, modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { vm.unignore(ig.bank, ig.tail) }) {
                                Text("恢复", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("通知", fontSize = 15.sp)
                    TextButton(onClick = { vm.testNotify() }) { Text("发一条测试提醒") }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("说明", fontSize = 15.sp)
                    Text(
                        "· 所有数据（授权码、账单）只存在这台手机上，不上传任何服务器\n" +
                            "· 每天早上 9 点自动同步邮箱并检查提醒，打开 App 也会检查一次\n" +
                            "· 收不到提醒时：系统设置 → 应用 → 还卡管家 → 通知开启、电池不受限制\n" +
                            "· 解析不到的账单会在「日志」里写明原因",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                "还卡管家 v1.0（自用版）",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
