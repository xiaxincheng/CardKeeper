package com.juxi.cardkeeper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juxi.cardkeeper.AppViewModel
import com.juxi.cardkeeper.data.MailboxEntity

private fun suggestedHost(email: String): String = when {
    email.endsWith("@163.com", true) -> "imap.163.com"
    email.endsWith("@126.com", true) -> "imap.126.com"
    email.endsWith("@qq.com", true) || email.endsWith("@foxmail.com", true) -> "imap.qq.com"
    email.endsWith("@139.com", true) -> "imap.139.com"
    email.endsWith("@sina.com", true) || email.endsWith("@sina.cn", true) -> "imap.sina.com"
    email.endsWith("@sohu.com", true) -> "imap.sohu.com"
    email.endsWith("@aliyun.com", true) -> "imap.aliyun.com"
    email.contains("@outlook.", true) || email.contains("@hotmail.", true) ||
        email.contains("@live.", true) -> "outlook.office365.com"
    else -> ""
}

@Composable
fun MailSetupScreen(vm: AppViewModel, onBack: () -> Unit) {
    val mailboxes by vm.mailboxes.collectAsState()
    var editing by remember { mutableStateOf<MailboxEntity?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("邮箱账单来源", onBack)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "App 直连邮箱服务器（IMAP）拉取账单邮件，与手机上的网易邮箱大师等客户端互不影响。" +
                    "每个邮箱需要在网页版开启 IMAP 并生成「授权码」填到下面。",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            for (box in mailboxes) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(box.email, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { editing = box }) { Text("编辑", fontSize = 12.sp) }
                            TextButton(onClick = { vm.deleteMailbox(box.id) }) {
                                Text("删除", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Text(
                            "服务器 ${box.host}:${box.port}",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (box.lastSyncAt > 0) "最近同步：${fmtTime(box.lastSyncAt)}" else "尚未同步",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        box.lastError?.let {
                            Text("上次出错：$it", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("+ 添加邮箱") }
            Spacer(Modifier.height(8.dp))
            Text("如何获取授权码", fontWeight = FontWeight.Bold)
            Text(
                "· 163 / 126 邮箱：电脑登录 mail.163.com → 设置 → POP3/SMTP/IMAP → 开启 IMAP 服务 → 新增授权密码\n\n" +
                    "· QQ / Foxmail 邮箱：网页版 设置 → 账户 → 开启 IMAP/SMTP 服务 → 生成授权码\n\n" +
                    "· 企业邮箱：找管理员开启 IMAP 并获取专用密码",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showAdd || editing != null) {
        MailboxDialog(
            initial = editing,
            onDismiss = { showAdd = false; editing = null },
            onSave = { id, email, code, host, port -> vm.saveMailbox(id, email, code, host, port) }
        )
    }
}

@Composable
private fun MailboxDialog(
    initial: MailboxEntity?,
    onDismiss: () -> Unit,
    onSave: (id: Long?, email: String, authCode: String, host: String, port: Int) -> Unit
) {
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var code by remember { mutableStateOf("") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var portStr by remember { mutableStateOf((initial?.port ?: 993).toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastAuto by remember { mutableStateOf("") }

    LaunchedEffect(email) {
        val s = suggestedHost(email)
        if (s.isNotEmpty() && (host.isEmpty() || host == lastAuto)) {
            host = s
            lastAuto = s
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加邮箱" else "编辑邮箱") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(email, { email = it }, label = { Text("邮箱地址") }, singleLine = true)
            OutlinedTextField(
                code, { code = it },
                label = { Text(if (initial == null) "授权码（不是登录密码）" else "授权码（留空保持原码不变）") },
                singleLine = true
            )
            OutlinedTextField(host, { host = it }, label = { Text("IMAP 服务器（自动带出，可改）") }, singleLine = true)
            OutlinedTextField(
                portStr,
                { portStr = it.filter { c -> c.isDigit() } },
                label = { Text("端口") },
                singleLine = true
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        } },
        confirmButton = {
            TextButton(onClick = {
                val port = portStr.toIntOrNull()
                when {
                    !email.contains("@") -> error = "邮箱地址格式不对"
                    initial == null && code.isBlank() -> error = "请填授权码"
                    host.isBlank() -> error = "请填 IMAP 服务器地址"
                    port == null || port !in 1..65535 -> error = "端口不对"
                    else -> {
                        onSave(initial?.id, email, code, host, port)
                        onDismiss()
                    }
                }
            }) { Text("保存并同步") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
