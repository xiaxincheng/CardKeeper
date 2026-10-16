package com.juxi.cardkeeper

import com.juxi.cardkeeper.mail.MailFetcher
import com.juxi.cardkeeper.parse.BillParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BillParserTest {

    private val base = LocalDate.of(2025, 12, 28)

    @Test
    fun `招商银行完整账单`() {
        val body = """
            【招商银行】您尾号1234的信用卡12月账单已出，请查收电子账单。
            本期应还金额：人民币8,000.00元
            最低还款额：800.00元
            到期还款日：2026年01月10日
        """.trimIndent()
        val p = BillParser.parseEmail("您12月账单已出", "招商银行", "ccmail@cmbchina.com", body, base)
        assertEquals("招商银行", p.bank)
        assertEquals("1234", p.tail)
        assertEquals(8000.0, p.amountFull!!, 0.001)
        assertEquals(800.0, p.amountMin!!, 0.001)
        assertEquals(LocalDate.of(2026, 1, 10), p.dueDate)
    }

    @Test
    fun `上期金额不干扰本期金额`() {
        val body = """
            上期账单金额：1,000.00元
            本期账单金额：2,000.00元
            最低还款额：200.00元
            到期还款日：2026-01-20
        """.trimIndent()
        val p = BillParser.parseEmail("【中国工商银行】信用卡电子账单", "工商银行", "noreply@icbc.com.cn", body, base)
        assertEquals("工商银行", p.bank)
        assertEquals(2000.0, p.amountFull!!, 0.001)
        assertEquals(200.0, p.amountMin!!, 0.001)
        assertEquals(LocalDate.of(2026, 1, 20), p.dueDate)
    }

    @Test
    fun `无年份日期跨年推断`() {
        val body = "尊敬的客户：您尾号8888的卡片本期应还款额12,345.67元，最低还款额1,234.56元，到期还款日：01月15日，请按时还款。"
        val p = BillParser.parseEmail("账单提醒", "中信银行信用卡中心", "bill@citicbank.com", body, base)
        assertEquals("中信银行", p.bank)
        assertEquals("8888", p.tail)
        assertEquals(12345.67, p.amountFull!!, 0.001)
        assertEquals(1234.56, p.amountMin!!, 0.001)
        assertEquals(LocalDate.of(2026, 1, 15), p.dueDate)
    }

    @Test
    fun `最低应还款额不被误判为全额`() {
        val body = "最低应还款额：500.00元。本期应还金额3,000.00元。"
        val p = BillParser.parseEmail(null, null, null, body, base)
        assertEquals(3000.0, p.amountFull!!, 0.001)
        assertEquals(500.0, p.amountMin!!, 0.001)
    }

    @Test
    fun `短信招商银行格式`() {
        val sms = "【招商银行】您尾号1234的信用卡12月账单已出，应还金额人民币8000元，还款日1月10日，请按时还款。"
        val p = BillParser.parseSmsBody(sms, base)!!
        assertEquals("招商银行", p.bank)
        assertEquals("1234", p.tail)
        assertEquals(8000.0, p.amountFull!!, 0.001)
        assertNull(p.amountMin)
        assertEquals(LocalDate.of(2026, 1, 10), p.dueDate)
    }

    @Test
    fun `短信请于日前还清格式`() {
        val sms = "【浦发银行】提醒：您尾号6666的信用卡人民币账户当期账单应还总额￥2,400.00元，请于01月08日前还清，以免影响信用。"
        val p = BillParser.parseSmsBody(sms, LocalDate.of(2025, 12, 30))!!
        assertEquals("浦发银行", p.bank)
        assertEquals("6666", p.tail)
        assertEquals(2400.0, p.amountFull!!, 0.001)
        assertEquals(LocalDate.of(2026, 1, 8), p.dueDate)
    }

    @Test
    fun `HTML正文先剥离标签再解析`() {
        val html = "<html><body><p>本期账单金额：1,234.56元</p><p>最低还款额：123.45元</p><p>到期还款日：2026年02月08日</p></body></html>"
        val text = MailFetcher.stripHtml(html)
        val p = BillParser.parseEmail("电子账单", "广发银行", "ccsvc@gdb.com.cn", text, base)
        assertEquals(1234.56, p.amountFull!!, 0.001)
        assertEquals(123.45, p.amountMin!!, 0.001)
        assertEquals(LocalDate.of(2026, 2, 8), p.dueDate)
    }

    @Test
    fun `仅日期的账单日结合还款日推算账期`() {
        val body = "您尾号2222的信用卡本期账单金额1,000.00元，账单日：每月17日，到期还款日：2026年1月6日"
        val p = BillParser.parseEmail(null, "交通银行", "creditcard@bankcomm.com", body, base)
        assertEquals("交通银行", p.bank)
        assertEquals(LocalDate.of(2026, 1, 6), p.dueDate)
        assertEquals(LocalDate.of(2025, 12, 17), p.billDate)
    }

    @Test
    fun `中信金额紧跟还款日无标签`() {
        val body = "中信银行信用卡电子账单 已出，详见如下摘要：\n到期还款日：2026年01月17日 RMB12,345.67 RMB1,234.56 RMB66,000.00\n尾号8888 交易日 交易摘要 人民币金额"
        val p = BillParser.parseEmail(null, "中信银行", "bill@citicbank.com", body, base)
        assertEquals(LocalDate.of(2026, 1, 17), p.dueDate)
        assertEquals(12345.67, p.amountFull!!, 0.001)
        assertEquals("8888", p.tail)
    }

    @Test
    fun `识别持卡人姓名`() {
        val body = "尊敬的张伟先生：\n您尾号1234的信用卡本期应还金额：1,000.00元，到期还款日：2026-02-10"
        val p = BillParser.parseEmail(null, null, null, body, base)
        assertEquals("张伟", p.holder)
        assertEquals(LocalDate.of(2026, 2, 10), p.dueDate)

        val noName = "尊敬的客户您好！您尾号5678的信用卡本期应还金额：500.00元，到期还款日：2026-02-15"
        assertNull(BillParser.parseEmail(null, null, null, noName, base).holder)
    }

    @Test
    fun `识别信用额度`() {
        val body = "尊敬的平安银行信用卡客户：\n本期还款日2026-02-08信用额度¥50,000.00\n本期应还金额：8,000.00元 最低还款额：800.00元"
        val p = BillParser.parseEmail(null, "平安银行", "creditcard@pingan.com.cn", body, base)
        assertEquals(50000.0, p.creditLimit!!, 0.001)
        assertEquals(LocalDate.of(2026, 2, 8), p.dueDate)
        assertEquals(8000.0, p.amountFull!!, 0.001)
    }

    @Test
    fun `额度与持卡人的常见变体`() {
        val body = "尊敬的王芳小姐：\n您的永久额度：￥50,000.00，本期应还金额（人民币元）：¥2,000.00元\n最低还款额（人民币元）：200.00元 到期还款日2026-03-05"
        val p = BillParser.parseEmail(null, "华夏银行", "cc@hxb.com.cn", body, base)
        assertEquals("王芳", p.holder)
        assertEquals(50000.0, p.creditLimit!!, 0.001)
        assertEquals(2000.0, p.amountFull!!, 0.001)
        assertEquals(200.0, p.amountMin!!, 0.001)
        assertEquals(LocalDate.of(2026, 3, 5), p.dueDate)
    }

    @Test
    fun `持卡人无冒号写法`() {
        val body = "持卡人张伟，您尾号1234的信用卡本期应还金额1,000.00元，到期还款日：2026-02-10"
        val p = BillParser.parseEmail(null, null, null, body, base)
        assertEquals("张伟", p.holder)
        assertEquals(1000.0, p.amountFull!!, 0.001)
    }

    @Test
    fun `掩码卡号识别尾号`() {
        val body = "尊敬的客户，您的卡号****5678本期应还金额：500.00元，到期还款日：2026-03-01"
        val p = BillParser.parseEmail(null, null, null, body, base)
        assertEquals("5678", p.tail)
        assertEquals(500.0, p.amountFull!!, 0.001)
        assertEquals(LocalDate.of(2026, 3, 1), p.dueDate)
        assertNull(p.bank)
    }
}
