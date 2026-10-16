package com.juxi.cardkeeper

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juxi.cardkeeper.notify.Notifier
import com.juxi.cardkeeper.ui.DetailScreen
import com.juxi.cardkeeper.ui.HomeScreen
import com.juxi.cardkeeper.ui.LogScreen
import com.juxi.cardkeeper.ui.MailSetupScreen
import com.juxi.cardkeeper.ui.RankScreen
import com.juxi.cardkeeper.ui.SettingsScreen
import com.juxi.cardkeeper.ui.theme.CardKeeperTheme

enum class Screen { HOME, DETAIL, RANK, MAIL, LOGS, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannel(this)
        setContent {
            CardKeeperTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val vm: AppViewModel = viewModel()
    val context = LocalContext.current
    var screen by remember { mutableStateOf(Screen.HOME) }
    var detailCardId by remember { mutableStateOf<Long?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val snack by vm.snack.collectAsState()

    LaunchedEffect(Unit) { vm.onAppOpen() }
    LaunchedEffect(snack) {
        snack?.let {
            snackbarHostState.showSnackbar(it)
            vm.snack(null)
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.scanSms()
        else vm.snack("没有短信权限，无法扫描。只用邮箱账单也不影响主要功能")
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            val ok = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!ok) notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    vm = vm,
                    onNavigate = { screen = it },
                    onRequestSms = { smsPermissionLauncher.launch(Manifest.permission.READ_SMS) },
                    onOpenDetail = { detailCardId = it; screen = Screen.DETAIL }
                )
                Screen.DETAIL -> {
                    val id = detailCardId
                    if (id == null) screen = Screen.HOME
                    else DetailScreen(vm = vm, cardId = id, onBack = { screen = Screen.HOME })
                }
                Screen.RANK -> RankScreen(vm) { screen = Screen.HOME }
                Screen.MAIL -> MailSetupScreen(vm) { screen = Screen.HOME }
                Screen.LOGS -> LogScreen(vm) { screen = Screen.HOME }
                Screen.SETTINGS -> SettingsScreen(vm) { screen = Screen.HOME }
            }
        }
    }
}
