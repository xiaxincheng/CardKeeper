package com.juxi.cardkeeper.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juxi.cardkeeper.AppViewModel

@Composable
fun LogScreen(vm: AppViewModel, onBack: () -> Unit) {
    val logs by vm.logs.collectAsState()
    Column(Modifier.fillMaxSize()) {
        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ScreenHeader("解析日志", onBack, Modifier.weight(1f))
            TextButton(onClick = {
                val text = logs.take(50).joinToString("\n") {
                    "${fmtTimeSec(it.time)} [${it.source}] ${if (it.ok) "OK" else "FAIL"} ${it.fromInfo} | ${it.subject} | ${it.detail}"
                }
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
                vm.snack("已复制 ${minOf(50, logs.size)} 条诊断，发给开发者即可定位解析问题")
            }) { Text("复制诊断", fontSize = 12.sp) }
            TextButton(onClick = { vm.clearLogs() }) { Text("清空", fontSize = 12.sp) }
            Spacer(Modifier.width(8.dp))
        }
        if (logs.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "暂无日志\n同步邮箱或扫描短信后，这里会显示每封账单的识别情况",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                fmtTimeSec(log.time), fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (log.source == "EMAIL") "邮件" else "短信",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (log.ok) "✓ 识别" else "✗ 未识别",
                                fontSize = 11.sp,
                                color = if (log.ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                            )
                        }
                        Text(log.subject, fontSize = 13.sp, maxLines = 1)
                        Text(
                            log.detail, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3
                        )
                        HorizontalDivider(Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}
