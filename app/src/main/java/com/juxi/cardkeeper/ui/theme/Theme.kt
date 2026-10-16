package com.juxi.cardkeeper.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * 「账本」视觉体系 —— 取自银行对账单的物料气质：账簿墨蓝、纸面灰白、卡面黄铜。
 * 红色与琥珀只表达紧急度，永远不做装饰用。
 */
object LedgerColors {
    val Ink = Color(0xFF1B2A4A)            // 账簿墨蓝：主色 / 英雄卡底色
    val Paper = Color(0xFFF6F7F9)          // 纸面：页面背景
    val Face = Color(0xFFFFFFFF)           // 卡面：表层
    val Brass = Color(0xFF8C6D2F)          // 黄铜：次级强调
    val Overdue = Color(0xFFC62828)        // 朱砂：仅用于逾期
    val Soon = Color(0xFFB26A00)           // 琥珀：仅用于 3 天内
    val Hairline = Color(0xFFE3E6EB)       // 发丝线
    val TextDim = Color(0xFF6B7385)        // 次级文字
    val TextMain = Color(0xFF1A1D26)       // 主文字

    // 英雄卡（墨蓝底）上的文字
    val OnInk = Color(0xFFF4F6FA)
    val OnInkDim = Color(0xB3F4F6FA)       // 70% 白
    val OnInkOk = Color(0xFFF4F6FA)
    val OnInkSoon = Color(0xFFFFC978)
    val OnInkOverdue = Color(0xFFFF9E93)
}

private val LightScheme = lightColorScheme(
    primary = LedgerColors.Ink,
    onPrimary = Color(0xFFFFFFFF),
    secondary = LedgerColors.Brass,
    onSecondary = Color(0xFFFFFFFF),
    error = LedgerColors.Overdue,
    background = LedgerColors.Paper,
    onBackground = LedgerColors.TextMain,
    surface = LedgerColors.Face,
    onSurface = LedgerColors.TextMain,
    surfaceVariant = Color(0xFFEEF0F4),
    onSurfaceVariant = LedgerColors.TextDim,
    outline = LedgerColors.Hairline
)

@Composable
fun CardKeeperTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightScheme, content = content)
}

/** 等宽数字：金额、日期、倒计时统一对齐，账本的底线 */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")
