package com.juxi.cardkeeper.parse

import java.time.LocalDate

/**
 * 从账单邮件正文 / 银行短信里提取：银行、卡号尾号、还款日、账单日、应还金额、最低还款额。
 * 各银行格式不一且会改版，解析不到的字段会记进 problems，由日志页展示。
 */
object BillParser {

    data class Parsed(
        var bank: String? = null,
        var holder: String? = null,
        var tail: String? = null,
        var dueDate: LocalDate? = null,
        var billDate: LocalDate? = null,
        var amountFull: Double? = null,
        var amountMin: Double? = null,
        var creditLimit: Double? = null,
        val problems: MutableList<String> = mutableListOf()
    )

    private const val NUM = "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"

    private val FULL_LABELS = listOf(
        "本期应还款金额", "本期应还欠款金额", "本期全部应还款额", "本期应还款总额",
        "本期新增应还款", "本期应还款额", "本期应还金额", "本期应还款", "本期账单金额",
        "本期应还欠款", "本期应还总额", "账单应还总额", "应还款总额", "应还款金额",
        "应还款额", "应还总金额", "应还金额", "应还总额", "账单金额", "本期应还", "应还欠款"
    ).sortedByDescending { it.length }

    private val MIN_LABELS = listOf(
        "最低还款金额", "最低应还款额", "最低还款额", "最低应还金额", "最低应还款", "最低还款"
    ).sortedByDescending { it.length }

    private val DUE_LABELS = listOf(
        "到期还款日期", "最后还款日期", "到期还款日", "最后还款日", "最迟还款日",
        "还款到期日", "本期到期日", "本期还款日", "应还款日", "还款日期",
        "到期日", "还款日"
    ).sortedByDescending { it.length }

    // (?<!上期)(?<!最低)：避免“上期账单金额 / 最低应还款额”里的子串被误当成全额
    // (?!组成|说明|详情|信息)：排除“本期账单金额组成说明”这类正文提及（广发实测踩坑）
    // 闸门间隔：标签到数值最多 42 个非数字字符（光大双语表格+空行实测 34），但中途不许
    // 跨过其他字段标签——防广发式“表头一行、数值一行”的列式表格串列（把卡号 9002 当金额）
    private const val LABEL_GATE =
        "最低还款金额|最低应还款额|最低还款额|最低应还金额|最低应还款|最低还款|" +
            "最后还款日|到期还款日|还款日|卡号末四位|尾号|账单周期|账单日|" +
            "信用额度|个人消费额度|授信额度|取现额度|可用额度"

    private val fullAmountRegex =
        Regex("(?<!上期)(?<!最低)(?:${FULL_LABELS.joinToString("|")})(?!组成|说明|详情|信息)(?:(?!$LABEL_GATE)[^0-9]){0,42}$NUM")
    private val minAmountRegex =
        Regex("(?:${MIN_LABELS.joinToString("|")})(?!组成|说明|详情|信息)(?:(?!$LABEL_GATE)[^0-9]){0,42}$NUM")

    // 双币账单（华夏实测）：美元 ＄0.00 排在人民币 ￥7,520.61 前面——优先取带 ￥/¥ 标记的人民币金额
    private val fullRmbRegex =
        Regex("(?<!上期)(?<!最低)(?:${FULL_LABELS.joinToString("|")})(?!组成|说明|详情|信息)[\\s\\S]{0,30}?[￥¥]\\s*$NUM")
    private val minRmbRegex =
        Regex("(?:${MIN_LABELS.joinToString("|")})(?!组成|说明|详情|信息)[\\s\\S]{0,30}?[￥¥]\\s*$NUM")

    // 信用额度（不用“可用额度”，那个随消费变动不代表总额度）；广发叫“个人消费额度”
    private val limitRegex =
        Regex("(?:个人消费额度|信用卡额度|信用额度|固定额度|授信额度|总额度|永久额度)[^0-9]{0,32}$NUM")

    // 建行列式表格：本期全部应还款额 … 最低还款额 … 人民币（CNY）后跟两个金额（全额、最低）
    private val ccbAmountRegex =
        Regex("本期全部应还款额[\\s\\S]{0,150}?最低还款额[\\s\\S]{0,150}?人民币[^0-9]{0,14}([0-9][0-9,]*\\.[0-9]{2})[^0-9]{0,16}([0-9][0-9,]*\\.[0-9]{2})")

    // 账单周期 “2026/08/11-2026/09/10” → 账单日取周期末日
    private val billPeriodRegex =
        Regex("账单周期[^0-9]{0,32}(\\d{4})[/.\\-](\\d{1,2})[/.\\-](\\d{1,2})\\s*[-—~至]\\s*(?:\\d{4}[/.\\-])?(\\d{1,2})[/.\\-](\\d{1,2})")

    // 广发式表头：卡号末四位 … 值行 … 9002（60 字符窗口，排除日期片段）
    private val tailLenientRegex =
        Regex("(?:卡号末四位|尾号|尾数)[\\s\\S]{0,60}?(?<!\\d)(\\d{4})(?![\\d/])")

    // 交行/建行等双语表格：“到期还款日\nPayment Due Date\n2026-09-28”，间隔放宽到 26
    private val dueWithYearRegex =
        Regex("(?:${DUE_LABELS.joinToString("|")})[^0-9]{0,32}(\\d{4})\\s*[年./\\-]\\s*(\\d{1,2})\\s*[月./\\-]\\s*(\\d{1,2})\\s*日?")
    private val dueNoYearRegex =
        Regex("(?:${DUE_LABELS.joinToString("|")})[^0-9]{0,32}(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日?")
    // “请于01月08日前还清” 这类没有还款日标签的写法
    private val dueBeforeRegex =
        Regex("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日?前还(?:款|清)")
    // 广发式“表头一行、数值一行”：标签后 60 字符窗口内找完整日期（年/月/日结构自校验）
    private val dueLenientRegex =
        Regex("(?:到期还款日|最后还款日|还款日)[\\s\\S]{0,60}?(?<!\\d)(\\d{4})[/.\\-](\\d{1,2})[/.\\-](\\d{1,2})(?!\\d)")

    // 中信等银行：金额不带标签，直接跟在还款日后面（“到期还款日：2026年01月17日 RMB12,345.67”）
    private val dueWithAmountRegex =
        Regex("(?:${DUE_LABELS.joinToString("|")})[^0-9]{0,10}(\\d{4})\\s*[年./\\-]\\s*(\\d{1,2})\\s*[月./\\-]\\s*(\\d{1,2})\\s*日?[^0-9]{0,8}([0-9][0-9,]*\\.[0-9]{2})")

    private val billDateWithYearRegex =
        Regex("(?:账单日|结账日|记账日|出账日)[^0-9]{0,32}(\\d{4})\\s*[年./\\-]\\s*(\\d{1,2})\\s*[月./\\-]\\s*(\\d{1,2})\\s*日?")

    // “账单日：每月17日” 只有几号没有年月，结合还款日反推所在账期
    private val billDayOnlyRegex =
        Regex("(?:账单日|结账日|记账日|出账日)[^0-9]{0,10}(\\d{1,2})\\s*日")

    private val tailLabeledRegex =
        Regex("(?:尾号|尾数|卡号末四位|卡尾号后四位|末四位|账户末四位|卡号后四位)[^0-9]{0,6}(\\d{4})")
    // 中信掩码卡号带分隔线：6229-18**-****-5918
    private val tailMaskedAfterRegex = Regex("[*xX＊]{2,}[\\s\\-]*(\\d{4})")
    private val tailMaskedBeforeRegex = Regex("(\\d{4})[\\s\\-]*[*xX＊]{2,}")

    // “尊敬的张伟先生 / 王芳小姐 / 持卡人张伟，” → 姓名；排除“尊敬的客户”这类无姓名称呼
    private val holderRegex =
        Regex("(?:尊敬的|亲爱的|持卡人[：:]?)\\s*([\\u4e00-\\u9fa5·]{2,4}?)(?=\\s*(?:先生|女士|小姐|同学|老师|博士|教授|您好|：|，|,))")
    private val HOLDER_BLACKLIST =
        setOf("客户", "用户", "会员", "持卡人", "贵宾", "您", "先生", "女士", "卡友", "阁下", "姓名", "名字", "亲")

    fun parseEmail(
        subject: String?,
        fromPersonal: String?,
        fromAddress: String?,
        body: String,
        sentDate: LocalDate
    ): Parsed {
        val text = (subject?.let { it + "\n" } ?: "") + body
        val p = Parsed()
        p.bank = Banks.fromAddress(fromAddress)
            ?: Banks.fromText(subject)
            ?: Banks.fromText(fromPersonal)
            ?: Banks.fromText(body.take(400))
        parseInto(p, text, sentDate)
        return p
    }

    /** @return 银行都识别不出来时返回 null（多半不是银行短信） */
    fun parseSmsBody(body: String, smsDate: LocalDate): Parsed? {
        val bracket = Regex("【([^】]{2,20})】").find(body)?.groupValues?.get(1)
        val bank = when {
            bracket != null -> Banks.fromText(bracket) ?: Banks.cleanBracketName(bracket)
            else -> Banks.fromText(body)
        } ?: return null
        val p = Parsed(bank = bank)
        parseInto(p, body, smsDate)
        return p
    }

    fun parseInto(p: Parsed, text: String, base: LocalDate) {
        // 建行的列式表格优先：一次配对拿到全额和最低，避免串列
        ccbAmountRegex.find(text)?.let { m ->
            p.amountFull = toAmount(m.groupValues[1])
            p.amountMin = toAmount(m.groupValues[2])
        }
        if (p.amountFull == null) {
            p.amountFull = fullRmbRegex.find(text)?.let { toAmount(it.groupValues[1]) }
        }
        if (p.amountFull == null) {
            p.amountFull = fullAmountRegex.find(text)?.let { toAmount(it.groupValues[1]) }
            if (p.amountFull == null) {
                p.amountFull = dueWithAmountRegex.find(text)?.let { toAmount(it.groupValues[4]) }
            }
        }
        if (p.amountMin == null) {
            p.amountMin = minRmbRegex.find(text)?.let { toAmount(it.groupValues[1]) }
        }
        if (p.amountMin == null) {
            p.amountMin = minAmountRegex.find(text)?.let { toAmount(it.groupValues[1]) }
        }
        p.creditLimit = limitRegex.find(text)?.let { toAmount(it.groupValues[1]) }
        if (p.amountFull == null) p.problems += "未找到应还金额"
        if (p.amountMin == null) p.problems += "未找到最低还款额"

        dueWithYearRegex.find(text)?.let { m ->
            p.dueDate = safeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        if (p.dueDate == null) {
            dueNoYearRegex.find(text)?.let { m ->
                p.dueDate = resolveYear(m.groupValues[1].toInt(), m.groupValues[2].toInt(), base)
            }
        }
        if (p.dueDate == null) {
            dueLenientRegex.find(text)?.let { m ->
                p.dueDate = safeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            }
        }
        if (p.dueDate == null) {
            dueBeforeRegex.find(text)?.let { m ->
                p.dueDate = resolveYear(m.groupValues[1].toInt(), m.groupValues[2].toInt(), base)
            }
        }
        if (p.dueDate == null) p.problems += "未找到还款日"

        billDateWithYearRegex.find(text)?.let { m ->
            p.billDate = safeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        if (p.billDate == null) {
            billPeriodRegex.find(text)?.let { m ->
                val y = m.groupValues[1].toInt()
                p.billDate = safeDate(y, m.groupValues[4].toInt(), m.groupValues[5].toInt())
            }
        }
        if (p.billDate == null) {
            billDayOnlyRegex.find(text)?.let { m ->
                val day = m.groupValues[1].toInt()
                val due = p.dueDate
                if (due != null && day in 1..31) {
                    val base0 = try { due.withDayOfMonth(day) } catch (_: Exception) { null }
                    if (base0 != null) {
                        p.billDate = if (base0.isAfter(due)) base0.minusMonths(1) else base0
                    }
                }
            }
        }

        p.tail = tailLabeledRegex.find(text)?.groupValues?.get(1)
            ?: tailMaskedAfterRegex.find(text)?.groupValues?.get(1)
            ?: tailMaskedBeforeRegex.find(text)?.groupValues?.get(1)
            ?: tailLenientRegex.find(text)?.groupValues?.get(1)
        if (p.tail == null) p.problems += "未找到卡号尾号"

        p.holder = holderRegex.findAll(text)
            .mapNotNull { m -> m.groupValues[1].takeIf { it !in HOLDER_BLACKLIST } }
            .firstOrNull()
    }

    /** 诊断用：暴露金额正则的 pattern 和匹配结果 */
    fun debugAmountMatches(text: String): String =
        "FULL_PATTERN=" + fullAmountRegex.pattern + "\n" +
            "MIN_PATTERN=" + minAmountRegex.pattern + "\n" +
            "FULL_MATCH=" + fullAmountRegex.find(text)?.groupValues + "\n" +
            "MIN_MATCH=" + minAmountRegex.find(text)?.groupValues

    private fun toAmount(raw: String): Double? = raw.replace(",", "").toDoubleOrNull()

    data class Tx(val date: LocalDate?, val memo: String, val amount: Double)

    /**
     * 从账单文本提取交易明细。真实账单（HTML 提取后）每个字段独占一行：
     *   日期 / 日期(记账日) / [尾号] / 摘要… / [CNY] / 金额 / [尾号]
     * 北京、建行、华夏实测版式均覆盖；负数金额表示还款/退款。
     */
    fun parseTransactions(text: String, ctx: LocalDate): List<Tx> {
        val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        val dateFull = Regex("^(\\d{4})[/.\\-](\\d{1,2})[/.\\-](\\d{1,2})$")
        // 短日期只认 斜杠/横杠 分隔——不能含点，否则金额 "2.00" 会被当成日期
        val dateShort = Regex("^(\\d{1,2})[/\\-](\\d{1,2})$")
        val amountLine = Regex("^(?:RMB:|CNY|RMB|USD|￥|¥|＄|\\$|人民币)?\\s*(-?[0-9][0-9,]*\\.[0-9]{2})$")
        val currencyOnly = Regex("^(?:CNY|RMB|USD|￥|¥|＄|\\$)$")
        val tailLine = Regex("^\\d{4}$")
        val commaInt = Regex("^(?:CNY|RMB|USD|￥|¥|＄|\\$|人民币)?\\s*-?\\d{1,3}(,\\d{3})+$")
        // 描述里出现其他字段标签 → 是汇总表头不是交易
        val labelAbort = Regex("应还款|最低还款|账单金额|账户币种|还款日|账单日|额度|Balance|Payment|Currency|Dispute|Statement")
        val out = mutableListOf<Tx>()
        fun isDate(s: String) = dateFull.matches(s) || dateShort.matches(s)
        var i = 0
        while (i < lines.size) {
            val mf = dateFull.find(lines[i])
            val ms = if (mf == null) dateShort.find(lines[i]) else null
            if (mf == null && ms == null) { i++; continue }
            var j = i + 1
            if (j < lines.size && isDate(lines[j])) j++                       // 记账日
            if (j < lines.size && tailLine.matches(lines[j])) j++             // 前置尾号（建行）
            val memo = StringBuilder()
            var amount: Double? = null
            var invalid = false
            while (j < lines.size && j < i + 14) {
                val s = lines[j]
                // 金额判断必须在日期判断之前（防御 "2.00" 之类歧义）
                val am = amountLine.find(s)
                if (am != null) {
                    if (memo.isEmpty()) break
                    amount = toAmount(am.groupValues[1])
                    break
                }
                if (isDate(s)) break
                if (currencyOnly.matches(s)) { j++; continue }
                if (commaInt.matches(s)) { invalid = true; break }
                if (labelAbort.containsMatchIn(s)) { invalid = true; break }
                memo.append(if (memo.isEmpty()) s else " $s")
                j++
            }
            if (!invalid && amount != null && memo.isNotBlank()) {
                val cleaned = memo.toString().replace(Regex("\\s+\\d{4}$"), "").take(60).trim()
                val date = when {
                    mf != null -> safeDate(mf.groupValues[1].toInt(), mf.groupValues[2].toInt(), mf.groupValues[3].toInt())
                    ms != null -> {
                        val base0 = safeDate(ctx.year, ms.groupValues[1].toInt(), ms.groupValues[2].toInt())
                        if (base0 != null && base0.isAfter(ctx.plusDays(10))) base0.minusYears(1) else base0
                    }
                    else -> null
                }
                out += Tx(date, cleaned, amount)
                i = j + 1
            } else {
                i++
            }
        }
        return out
    }

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? = try {
        LocalDate.of(y, m, d)
    } catch (_: Exception) {
        null
    }

    /** 只有月日没有年份时，参照邮件/短信的发送日期推年份（含跨年） */
    private fun resolveYear(month: Int, day: Int, base: LocalDate): LocalDate? {
        val candidate = safeDate(base.year, month, day) ?: return null
        return if (candidate.isBefore(base.minusDays(15))) candidate.plusYears(1) else candidate
    }
}
