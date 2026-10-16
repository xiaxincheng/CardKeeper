package com.juxi.cardkeeper.mail

import com.juxi.cardkeeper.data.MailboxEntity
import com.sun.mail.imap.IMAPFolder
import java.io.IOException
import java.util.Date
import java.util.Properties
import javax.mail.AuthenticationFailedException
import javax.mail.FetchProfile
import javax.mail.Folder
import javax.mail.Part
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeUtility
import javax.mail.search.ComparisonTerm
import javax.mail.search.ReceivedDateTerm

object MailFetcher {

    private val KEYWORDS = listOf("账单", "对账单", "还款", "信用卡", "月结单", "statement", "bill")

    data class RawMail(
        val fromAddress: String?,
        val fromPersonal: String?,
        val subject: String,
        val sentDate: Long,
        val bodyText: String,
        val hadPdf: Boolean = false
    )

    class MailAuthException(message: String) : IOException(message)

    fun fetch(config: MailboxEntity, daysBack: Int, pdfPasswords: List<String> = emptyList()): List<RawMail> {
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", config.host)
            put("mail.imaps.port", config.port.toString())
            // 不设置 ssl.trust 豁免：严格校验服务器证书，防中间人窃听授权码
            put("mail.imaps.connectiontimeout", "15000")
            put("mail.imaps.timeout", "60000")
        }
        val session = Session.getInstance(props, null)
        val store = session.getStore("imaps")
        try {
            store.connect(config.host, config.port, config.email, config.authCode)
        } catch (e: AuthenticationFailedException) {
            throw MailAuthException("登录失败：邮箱地址或授权码不正确")
        } catch (e: Exception) {
            throw IOException("无法连接 ${config.host}:${config.port}（${e.message ?: e.javaClass.simpleName}）", e)
        }

        val inbox = store.getFolder("INBOX") as IMAPFolder
        try {
            // 网易系邮箱（163/126）要求客户端先发 ID 命令，否则报 Unsafe Login 拒绝读取
            try {
                inbox.doCommand { p ->
                    p.simpleCommand("ID (\"name\" \"CardKeeper\" \"version\" \"1.0.0\")", null)
                    null
                }
            } catch (_: Exception) {
            }

            inbox.open(Folder.READ_ONLY)
            val sinceMillis = System.currentTimeMillis() - daysBack * 24L * 3600L * 1000L
            var messages = inbox.search(ReceivedDateTerm(ComparisonTerm.GE, Date(sinceMillis)))
            if (messages.size > 800) messages = messages.copyOfRange(messages.size - 800, messages.size)

            val profile = FetchProfile()
            profile.add(FetchProfile.Item.ENVELOPE)
            inbox.fetch(messages, profile)

            val result = mutableListOf<RawMail>()
            for (m in messages) {
                val rawSubject = m.subject ?: ""
                val subject = try { MimeUtility.decodeText(rawSubject) } catch (_: Exception) { rawSubject }
                val from = m.from?.firstOrNull() as? InternetAddress
                val hay = subject + " " + (from?.personal ?: "") + " " + (from?.address ?: "")
                if (KEYWORDS.none { hay.contains(it, ignoreCase = true) }) continue
                val body = try { extractText(m) } catch (_: Exception) { "" }
                val pdfTexts = try {
                    collectPdfBytes(m).mapNotNull { PdfExtractor.extract(it, pdfPasswords) }
                } catch (_: Exception) { emptyList() }
                val combined = (listOf(body) + pdfTexts)
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                result.add(
                    RawMail(
                        fromAddress = from?.address,
                        fromPersonal = from?.personal,
                        subject = subject,
                        sentDate = m.sentDate?.time ?: System.currentTimeMillis(),
                        bodyText = combined.take(12000),
                        hadPdf = pdfTexts.isNotEmpty()
                    )
                )
            }
            return result
        } finally {
            try { if (inbox.isOpen) inbox.close(false) } catch (_: Exception) {}
            try { store.close() } catch (_: Exception) {}
        }
    }

    /** 提取正文：优先纯文本，其次 HTML 去标签；换行统一为 \n（邮件常见 \r\n，会撑大标签到数值的间距） */
    fun extractText(part: Part): String {
        val raw = extractRaw(part)
        return raw.replace("\r\n", "\n").replace("\r", "\n")
    }

    private fun extractRaw(part: Part): String {
        if (part.isMimeType("text/plain")) return part.content?.toString() ?: ""
        if (part.isMimeType("text/html")) return stripHtml(part.content?.toString() ?: "")
        if (part.isMimeType("multipart/*")) {
            val mp = part.content as javax.mail.Multipart
            val children = (0 until mp.count).map { mp.getBodyPart(it) }
            children.firstOrNull { it.isMimeType("text/plain") }?.let { return extractText(it) }
            for (child in children) {
                if (child.isMimeType("multipart/*")) {
                    val t = extractText(child)
                    if (t.isNotBlank()) return t
                }
            }
            children.firstOrNull { it.isMimeType("text/html") }?.let { return extractText(it) }
        }
        return ""
    }

    /** 收集邮件里的 PDF 附件（含内联） */
    private fun collectPdfBytes(part: Part): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        fun walk(p: Part) {
            if (p.isMimeType("multipart/*")) {
                val mp = p.content as javax.mail.Multipart
                for (i in 0 until mp.count) walk(mp.getBodyPart(i))
            } else {
                val rawName = try { p.fileName } catch (_: Exception) { null }
                val name = rawName?.let { runCatching { MimeUtility.decodeText(it) }.getOrDefault(it) }
                val isPdf = p.isMimeType("application/pdf") ||
                    name?.lowercase()?.endsWith(".pdf") == true
                if (isPdf) {
                    try { out.add(p.inputStream.readBytes()) } catch (_: Exception) {}
                }
            }
        }
        walk(part)
        return out
    }

    fun stripHtml(html: String): String {
        return html
            .replace(Regex("(?is)<(style|script)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            // td/th 也转换行：单元格是字段边界，交易明细的“一行一字段”依赖它
            .replace(Regex("(?i)</(p|div|tr|td|th|table|h[1-6]|li)>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
            .replace("&gt;", ">").replace("&yen;", "¥").replace("&#165;", "¥").replace("&quot;", "\"")
            .replace(Regex("[ \\t\\x0B\\f]+"), " ")
            .replace(Regex("\\n[ \\t]*"), "\n")
            .replace(Regex("\\n{2,}"), "\n")
            .trim()
    }
}
