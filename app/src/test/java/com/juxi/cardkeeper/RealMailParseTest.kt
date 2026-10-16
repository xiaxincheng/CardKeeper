package com.juxi.cardkeeper

import com.juxi.cardkeeper.mail.MailFetcher
import com.juxi.cardkeeper.parse.BillParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Properties
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeUtility

/**
 * 端到端解析测试：直接用真实邮箱导出的 .eml 样本（本地「邮件样本/」目录，不随仓库分发），
 * 走 App 完全相同的解析路径（MailFetcher.extractText + BillParser.parseEmail）。
 * 样本缺失时这些用例自动跳过，见 README「真实账单样本」。
 */
class RealMailParseTest {

    // 依次尝试环境变量 CK_SAMPLE_DIR、模块同级、工程根下的「邮件样本」目录
    private val dir: File = listOf(
        System.getenv("CK_SAMPLE_DIR"),
        "../邮件样本",
        "邮件样本"
    ).filterNotNull().map(::File).firstOrNull { it.isDirectory } ?: File("../邮件样本")
    private val base0 = LocalDate.of(2026, 8, 1)

    // 持卡人姓名属个人隐私，公开仓库只校验「2-4 位中文姓名」格式
    private fun assertChineseName(holder: String?) {
        assertTrue("持卡人应为2-4位中文，实际: $holder", holder != null && holder.matches(Regex("[\\u4e00-\\u9fa5·]{2,4}")))
    }

    private fun extractBody(name: String): String {
        val msg = MimeMessage(Session.getDefaultInstance(Properties()), FileInputStream(File(dir, name)))
        return MailFetcher.extractText(msg)
    }

    private fun parseFile(name: String): BillParser.Parsed {
        val msg = MimeMessage(Session.getDefaultInstance(Properties()), FileInputStream(File(dir, name)))
        val subject = try {
            MimeUtility.decodeText(msg.subject ?: "")
        } catch (_: Exception) {
            msg.subject ?: ""
        }
        val from = msg.from?.firstOrNull()
        val body = MailFetcher.extractText(msg)
        val sent = Instant.ofEpochMilli(msg.sentDate?.time ?: System.currentTimeMillis())
            .atZone(ZoneId.systemDefault()).toLocalDate()
        return BillParser.parseEmail(
            subject,
            (from as? InternetAddress)?.personal,
            from?.toString(),
            body,
            sent
        )
    }

    private fun has(name: String) = File(dir, name).isFile

    @Test
    fun `北京银行真实账单`() {
        assumeTrue(has("00_北京银行_信用卡2026年08月电子账单.eml"))
        val p = parseFile("00_北京银行_信用卡2026年08月电子账单.eml")
        assertEquals("北京银行", p.bank)
        assertChineseName(p.holder)
        assertEquals(LocalDate.of(2026, 9, 6), p.dueDate)
        assertEquals(3403.70, p.amountFull!!, 0.001)
        assertEquals(170.19, p.amountMin!!, 0.001)
        assertEquals(12000.0, p.creditLimit!!, 0.001)
    }

    @Test
    fun `光大真实账单_HTML隐藏文字版可解析`() {
        assumeTrue(has("01_光大信用卡电子账单.eml"))
        val p = parseFile("01_光大信用卡电子账单.eml")
        assertEquals("光大银行", p.bank)
        assertChineseName(p.holder)
        assertEquals("1282", p.tail)
        assertEquals(LocalDate.of(2026, 9, 10), p.dueDate)
        assertEquals(LocalDate.of(2026, 8, 22), p.billDate)
        // 本期应还款 0.00（无欠款月）
        assertEquals(0.0, p.amountFull!!, 0.001)
        assertEquals(0.0, p.amountMin!!, 0.001)
        assertEquals(8000.0, p.creditLimit!!, 0.001)
    }

    @Test
    fun `光大第二张卡_同银行同账期双卡`() {
        assumeTrue(has("02_光大信用卡电子账单.eml"))
        val p = parseFile("02_光大信用卡电子账单.eml")
        assertEquals("光大银行", p.bank)
        assertChineseName(p.holder)
        assertEquals("6080", p.tail)
        assertEquals(LocalDate.of(2026, 9, 10), p.dueDate)
        assertEquals(LocalDate.of(2026, 8, 22), p.billDate)
        assertEquals(0.0, p.amountFull!!, 0.001)
        assertEquals(82000.0, p.creditLimit!!, 0.001)
    }

    @Test
    fun `同银行两张卡的账单互不混淆`() {
        assumeTrue(has("01_光大信用卡电子账单.eml") && has("02_光大信用卡电子账单.eml"))
        val a = parseFile("01_光大信用卡电子账单.eml")
        val b = parseFile("02_光大信用卡电子账单.eml")
        // 同银行、同账期，靠尾号和持卡人区分成两张独立卡片
        assertEquals(a.dueDate, b.dueDate)
        assertNotEquals(a.tail, b.tail)
        assertNotEquals(a.holder, b.holder)
        assertNotEquals(a.creditLimit, b.creditLimit)
    }

    @Test
    fun `交通银行真实账单`() {
        assumeTrue(has("03_交通银行白金信用卡2026年09月电子账单.eml"))
        val p = parseFile("03_交通银行白金信用卡2026年09月电子账单.eml")
        assertEquals("交通银行", p.bank)
        assertChineseName(p.holder)
        assertEquals("0756", p.tail)
        assertEquals(LocalDate.of(2026, 9, 28), p.dueDate)
        assertEquals(3018.60, p.amountFull!!, 0.001)
        assertEquals(2486.60, p.amountMin!!, 0.001)
        assertEquals(150000.0, p.creditLimit!!, 0.001)
        assertEquals(LocalDate.of(2026, 9, 3), p.billDate)
    }

    @Test
    fun `广发真实账单_列式表格与无欠款月`() {
        assumeTrue(has("04_广发信用卡_2026年09月电子账单.eml"))
        val p = parseFile("04_广发信用卡_2026年09月电子账单.eml")
        assertEquals("广发银行", p.bank)
        assertChineseName(p.holder)
        assertEquals("9002", p.tail)
        assertEquals(LocalDate.of(2026, 9, 26), p.dueDate)
        assertEquals(LocalDate.of(2026, 9, 6), p.billDate)
        assertEquals(20000.0, p.creditLimit!!, 0.001)
        // 本期“无欠款”，金额允许为空
        assertNull(p.amountFull)
    }

    @Test
    fun `建设银行真实账单_列式表格不串列`() {
        assumeTrue(has("05_中国建设银行信用卡电子账单.eml"))
        val p = parseFile("05_中国建设银行信用卡电子账单.eml")
        assertEquals("建设银行", p.bank)
        assertChineseName(p.holder)
        assertEquals(LocalDate.of(2026, 10, 1), p.dueDate)
        // 上期 360.51 不得被误当成本期
        assertEquals(593.69, p.amountFull!!, 0.001)
        assertEquals(200.00, p.amountMin!!, 0.001)
        assertEquals(23000.0, p.creditLimit!!, 0.001)
        assertEquals(LocalDate.of(2026, 9, 10), p.billDate)
    }

    @Test
    fun `中信真实版式_横杠掩码卡号`() {
        assumeTrue(has("06_中信银行_2026年08月.eml"))
        val p = parseFile("06_中信银行_2026年08月.eml")
        assertEquals("中信银行", p.bank)
        assertEquals(LocalDate.of(2026, 9, 10), p.dueDate)
        assertEquals(LocalDate.of(2026, 8, 22), p.billDate)
        assertEquals(263.47, p.amountFull!!, 0.001)
        assertEquals(13.17, p.amountMin!!, 0.001)
        // 6229-18**-****-5918 → 5918
        assertEquals("5918", p.tail)
    }

    @Test
    fun `华夏真实版式_双币账单取人民币`() {
        assumeTrue(has("07_华夏银行_2026年08月.eml"))
        val p = parseFile("07_华夏银行_2026年08月.eml")
        assertEquals("华夏银行", p.bank)
        assertChineseName(p.holder)
        assertEquals(LocalDate.of(2026, 9, 7), p.dueDate)
        assertEquals(LocalDate.of(2026, 8, 18), p.billDate)
        // ＄0.00 在前，必须取 ￥7,520.61 / ￥5,903.69
        assertEquals(7520.61, p.amountFull!!, 0.001)
        assertEquals(5903.69, p.amountMin!!, 0.001)
        assertEquals(5600.0, p.creditLimit!!, 0.001)
        assertEquals("3531", p.tail)
    }

    @Test
    fun `北京真实账单交易明细`() {
        assumeTrue(has("00_北京银行_信用卡2026年08月电子账单.eml"))
        val p = parseFile("00_北京银行_信用卡2026年08月电子账单.eml")
        val txs = BillParser.parseTransactions(extractBody("00_北京银行_信用卡2026年08月电子账单.eml"), p.billDate ?: base0)
        assertTrue("交易笔数应≥6，实际 ${txs.size}", txs.size >= 6)
        val first = txs.first()
        assertTrue("首笔摘要应含支付宝: ${first.memo}", first.memo.contains("支付宝"))
        assertEquals(2000.00, first.amount, 0.001)
        assertEquals(java.time.LocalDate.of(2026, 7, 18), first.date)
        // 还款是负数金额
        val repay = txs.first { it.memo.contains("自行转入") }
        assertEquals(-1291.90, repay.amount, 0.001)
    }

    @Test
    fun `建行与华夏真实账单交易明细`() {
        assumeTrue(has("05_中国建设银行信用卡电子账单.eml"))
        val ccb = parseFile("05_中国建设银行信用卡电子账单.eml")
        val ccbTxs = BillParser.parseTransactions(extractBody("05_中国建设银行信用卡电子账单.eml"), ccb.billDate ?: base0)
        assertTrue(ccbTxs.isNotEmpty())
        assertEquals("ETC停车扣款", ccbTxs.first().memo)
        assertEquals(2.00, ccbTxs.first().amount, 0.001)
        assertTrue(ccbTxs.any { it.memo.contains("转账还款") && it.amount < 0 })

        assumeTrue(has("07_华夏银行_2026年08月.eml"))
        val hxBill = parseFile("07_华夏银行_2026年08月.eml")
        val hxTxs = BillParser.parseTransactions(extractBody("07_华夏银行_2026年08月.eml"), hxBill.billDate ?: base0)
        assertTrue(hxTxs.isNotEmpty())
        assertEquals("利息", hxTxs.first().memo)
        assertEquals(99.25, hxTxs.first().amount, 0.001)
    }

    @Test
    fun `全部样本解析不崩溃且主体识别正确`() {
        assumeTrue(dir.isDirectory)
        val files = dir.listFiles { f -> f.name.endsWith(".eml") } ?: emptyArray()
        assertTrue(files.isNotEmpty())
        files.forEach { f ->
            val p = parseFile(f.name)
            assertNotNull("银行识别失败: ${f.name} -> ${p.bank}", p.bank)
        }
    }
}
