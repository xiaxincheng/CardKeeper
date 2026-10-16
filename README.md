# 还卡管家 CardKeeper

**本地优先、数据不出设备的 Android 信用卡账单解析与还款提醒应用。**
**A local-first Android app that parses credit-card statements and sends repayment reminders. Your data never leaves your device.**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg)](#)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26-informational)](#)
[![API](https://img.shields.io/badge/Target%20SDK-34-informational)](#)

<p align="center">
  <img src="docs/screenshots/home.png" width="270" alt="首页 / Home" />
  &nbsp;
  <img src="docs/screenshots/detail.png" width="270" alt="卡片详情 / Card detail" />
</p>

## 目录 / Table of contents

- [下载与安装 / Download and install](#下载与安装--download-and-install)
- [项目简介 / Introduction](#项目简介--introduction)
- [功能 / Features](#功能--features)
- [隐私与权限 / Privacy and permissions](#隐私与权限--privacy-and-permissions)
- [使用建议 / Usage notes](#使用建议--usage-notes)
- [技术栈 / Tech stack](#技术栈--tech-stack)
- [从源码构建 / Build from source](#从源码构建--build-from-source)
- [参与贡献 / Contributing](#参与贡献--contributing)
- [许可证 / License](#许可证--license)

## 下载与安装 / Download and install

### 中文

1. 打开 [GitHub Releases](https://github.com/xiaxincheng/CardKeeper/releases/latest)。
2. 下载 `CardKeeper-v1.0.apk`。
3. 在 Android 手机上点击 APK。系统可能提示“允许安装未知应用”，请对浏览器、文件管理器或 GitHub 客户端授予权限。
4. 安装完成后启动 CardKeeper，添加邮箱 IMAP 授权码即可开始同步账单。
5. 建议安装后打开一次 App，让 Android 注册每日后台同步和提醒任务。

当前发布包信息：

| 项目 | 内容 |
|---|---|
| 版本 / Version | `1.0` (`versionCode 1`) |
| 最低系统 / Minimum Android | Android 8.0 / API 26 |
| 目标系统 / Target Android | Android 14 / API 34 |
| 安装包 / Package | `com.juxi.cardkeeper` |
| SHA-256 | `994694bd84ea308a560e004133d3f9deaa68ba127920faba928f27bf708075b1` |

> 提醒：还款提醒依赖系统通知权限；短信补充功能需要单独授权读取短信，未授权时仍可正常使用邮件账单功能。

### English

1. Open the [GitHub Releases page](https://github.com/xiaxincheng/CardKeeper/releases/latest).
2. Download `CardKeeper-v1.0.apk`.
3. Tap the APK on your Android phone. Android may ask you to allow “Install unknown apps” for the browser, file manager, or GitHub client you used.
4. Launch CardKeeper and add an IMAP authorization code to start synchronizing statements.
5. Opening the app once after installation helps Android register the daily background synchronization and reminder work.

Current release:

| Item | Value |
|---|---|
| Version | `1.0` (`versionCode 1`) |
| Minimum Android | Android 8.0 / API 26 |
| Target Android | Android 14 / API 34 |
| Package | `com.juxi.cardkeeper` |
| SHA-256 | `994694bd84ea308a560e004133d3f9deaa68ba127920faba928f27bf708075b1` |

> Notes: repayment reminders require notification permission. SMS supplementation is optional; without SMS permission, mail-based statement tracking remains available.

## 项目简介 / Introduction

### 中文

还卡管家是一个纯本地的信用卡账单管家。它可以直接连接你自己的邮箱 IMAP 服务器，拉取银行账单邮件，解析还款日、账单日、应还金额、最低还款额、持卡人和信用额度，并在还款日前推送提醒，避免逾期。

项目的核心原则是：**账单、邮箱授权码、还款记录和解析结果都只保存在你的设备上，不上传任何开发者或第三方服务器，也不内置统计分析 SDK。**

### English

CardKeeper is a local-first credit-card statement manager. It connects directly to your own IMAP mail server, fetches bank statement emails, extracts repayment dates, statement dates, balances, minimum payments, cardholder names, and credit limits, then sends reminders before repayment is due.

The core principle is: **statements, mail authorization codes, repayment records, and parsing results remain on your device. The app does not upload them to a developer or third-party server and does not bundle an analytics SDK.**

## 功能 / Features

### 中文

- **多邮箱 IMAP 直连**：支持 163、126、QQ、Foxmail、139、sina、sohu、aliyun、outlook 等常见邮箱自动匹配服务器；其他邮箱可手动填写服务器地址和端口，并兼容 163 的 IMAP ID 命令握手。
- **账单解析**：识别银行名称、卡号尾号、持卡人、还款日、账单日、全额应还款、最低还款额、信用额度；支持 HTML 表格、双语对照、列式表格、双币账单、掩码卡号等不同银行版式，双币账单自动取人民币金额。
- **短信补充**：可选读取最近 100 天银行短信，并按「银行 + 卡号尾号 + 账期」与邮件来源做字段级合并。
- **还款管理**：支持记录部分还款、还清全额、还最低；还款记录可撤销；已还清的卡片自动沉底，不再打扰。
- **排行页**：按免息期长度、信用额度等维度查看卡片。
- **历史记录**：查看每张卡的历史账单、本期交易和还款记录。
- **弃用卡片**：删除卡片时可标记为已弃用，以后再收到该卡账单会自动跳过。
- **自动提醒**：还款日前 N 天每天提醒，默认提前 3 天；WorkManager 每日 9 点后台同步，打开 App 时也会立即检查。
- **本地安全存储**：邮箱授权码使用 AndroidKeyStore AES-256-GCM 加密；网络连接强制 TLS 证书校验；应用禁用明文流量和系统备份。
- **透明开源**：源码公开，欢迎审查、复现构建、报告问题和提交改进。

### English

- **Direct multi-account IMAP access**: automatic server presets for 163, 126, QQ, Foxmail, 139, Sina, Sohu, Aliyun, Outlook, and others; custom IMAP servers are also supported. Includes compatibility with 163's IMAP ID handshake.
- **Statement parsing**: recognizes bank names, last-four card numbers, cardholder names, repayment dates, statement dates, full balances, minimum payments, and credit limits. Handles HTML tables, bilingual layouts, columnar tables, dual-currency statements, masked card numbers, and other bank formatting variations. Dual-currency statements use the RMB amount automatically.
- **Optional SMS supplementation**: reads bank SMS from the last 100 days after explicit permission and merges fields with mail data by bank, card suffix, and statement period.
- **Repayment management**: record partial payments, full payments, or minimum payments; undo is supported. Fully paid cards sink to the bottom and stop generating noisy reminders.
- **Rankings**: compare cards by interest-free period, credit limit, and other dimensions.
- **History**: review historical statements, current-period transactions, and repayments for each card.
- **Retired cards**: mark a deleted card as retired so future statements for it are skipped automatically.
- **Automatic reminders**: daily reminders N days before the due date, defaulting to 3 days. WorkManager schedules daily background synchronization around 09:00, and opening the app triggers an immediate check.
- **Secure local storage**: mail authorization codes are encrypted with AndroidKeyStore AES-256-GCM. Network connections use strict TLS certificate verification; cleartext traffic and system backups are disabled.
- **Transparent open source**: the source code is public. Security review, reproducible builds, issue reports, and improvements are welcome.

## 隐私与权限 / Privacy and permissions

### 中文

CardKeeper 不内置广告、统计、埋点或第三方云同步。数据按以下方式处理：

- 邮箱连接信息仅用于登录你自己选择的 IMAP 服务器。
- 授权码加密后保存在本机 AndroidKeyStore 中。
- 账单、卡片、交易、还款和日志保存在本机 Room 数据库。
- 不上传联系人、地理位置、设备标识符或账单内容。
- 删除 App 即删除本机数据；请自行确认是否需要先导出或记录还款信息。

应用声明的权限及用途：

| 权限 | 用途 | 是否必需 |
|---|---|---|
| `INTERNET` / `ACCESS_NETWORK_STATE` | 连接你自己邮箱的 IMAP 服务器、判断网络状态 | 邮件同步必需 |
| `READ_SMS` | 可选读取银行短信，补充邮件账单 | 可选；拒绝后邮件功能仍可用 |
| `POST_NOTIFICATIONS` | 发送还款提醒 | 提醒功能必需，可按系统设置管理 |

### English

CardKeeper contains no advertisements, analytics, event tracking, or third-party cloud synchronization. Data handling is as follows:

- Mail connection information is used only to sign in to the IMAP server you choose.
- The authorization code is encrypted on-device with AndroidKeyStore.
- Statements, cards, transactions, repayments, and logs are stored in the local Room database.
- Contacts, location, device identifiers, and statement contents are not uploaded.
- Uninstalling the app removes its local data. Export or record anything you need before removal.

Declared permissions and purposes:

| Permission | Purpose | Required? |
|---|---|---|
| `INTERNET` / `ACCESS_NETWORK_STATE` | Connect to your own IMAP server and detect network state | Required for mail synchronization |
| `READ_SMS` | Optionally read bank SMS to supplement mail statements | Optional; mail tracking works without it |
| `POST_NOTIFICATIONS` | Send repayment reminders | Required for reminders; manageable in Android settings |

## 使用建议 / Usage notes

### 中文

- 部分邮箱（如 163、QQ）需要生成 IMAP 授权码，不能直接使用登录密码。
- 不同银行的邮件版式可能变化。如果某封账单解析不完整，可在应用“日志”页查看解析结果，并提交 Issue 帮助改进规则。
- 应用会尽量识别账单字段，但银行官方 App、短信和客服仍然是最终还款依据。请在还款日前保留足够余量。
- 如果手机厂商强制限制后台任务，请按系统提示允许 CardKeeper 后台运行或自启动，否则每日同步和提醒可能延迟。
- 明文密码、签名 keystore、真实账单样本和本机路径不会出现在公开仓库中。

### English

- Some providers, such as 163 and QQ, require a dedicated IMAP authorization code rather than the normal login password.
- Bank email layouts can change. If a statement is not parsed completely, check the in-app log page and submit an issue with non-sensitive formatting details to help improve the parser.
- Although CardKeeper extracts statement fields automatically, your bank's official app, SMS, and support channel remain the authoritative source. Leave enough time before due dates.
- On phones with aggressive manufacturer-specific battery restrictions, allow background execution or autostart for CardKeeper; otherwise daily synchronization and reminders may be delayed.
- Plaintext passwords, signing keystores, real statement samples, and machine-specific paths are excluded from the public repository.

## 技术栈 / Tech stack

- Kotlin
- Jetpack Compose / Material 3
- Room schema and migrations
- WorkManager
- Jakarta Mail for IMAP
- pdfbox-android for PDF statements
- JUnit parser tests

核心解析逻辑在 [`app/src/main/java/com/juxi/cardkeeper/parse/BillParser.kt`](app/src/main/java/com/juxi/cardkeeper/parse/BillParser.kt)。它使用「标签 + 带闸门间隔」的正则组和交易行状态机，避免单封邮件中的相似金额互相污染。

The parsing core lives in [`app/src/main/java/com/juxi/cardkeeper/parse/BillParser.kt`](app/src/main/java/com/juxi/cardkeeper/parse/BillParser.kt). It uses label-oriented gated regex groups and a transaction-line state machine to prevent similar amounts in one message from contaminating each other.

## 从源码构建 / Build from source

### 中文

环境要求：

- JDK 17
- Android SDK 34
- Android Studio 或命令行 Gradle

```bash
git clone https://github.com/xiaxincheng/CardKeeper.git
cd CardKeeper
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Debug APK 输出在：

```text
app/build/outputs/apk/debug/app-debug.apk
```

正式签名文件和密码不入库。你可以在项目根目录放置以下两个被 `.gitignore` 排除的文件：

- `cardkeeper-release.jks`
- `signing.properties`

```properties
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

没有这些文件时，`assembleRelease` 仍可构建，但产物是未签名 APK。

### English

Requirements:

- JDK 17
- Android SDK 34
- Android Studio or command-line Gradle

```bash
git clone https://github.com/xiaxincheng/CardKeeper.git
cd CardKeeper
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release signing material is intentionally excluded from Git. For your own signed build, place these two ignored files in the repository root:

- `cardkeeper-release.jks`
- `signing.properties`

```properties
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

Without them, `assembleRelease` still builds, but the output APK is unsigned.

### 真实样本测试 / Testing with private samples

`RealMailParseTest` can consume private `.eml` files from a local `邮件样本/` directory, or from the path specified by `CK_SAMPLE_DIR`. These samples may contain names and card numbers, so they are not distributed with the repository. When no sample directory is present, these tests skip automatically; the rest of the unit tests still run.

## 参与贡献 / Contributing

欢迎通过 Issue 报告解析问题、提出功能建议，或提交 Pull Request。为了保护隐私，请不要在 Issue、截图、日志或提交内容中包含：

- 完整银行卡号、CVV、有效期
- 邮箱密码或 IMAP 授权码
- 真实姓名、身份证号、手机号
- 完整账单原文
- 签名 keystore 或密码

Issues and pull requests are welcome. To protect privacy, never include full card numbers, CVV, expiry dates, mail passwords or IMAP authorization codes, real names, national IDs, phone numbers, complete statement text, signing keystores, or passwords.

## 许可证 / License

[MIT](LICENSE)
