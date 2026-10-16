package com.juxi.cardkeeper.parse

/**
 * 中国信用卡发卡行识别库。
 * 覆盖：国有六大行、12 家股份行、主要城商行/农商行、在华外资行，约 50 家。
 * 匹配时长名优先（“上海农商银行”先于“上海银行”）。
 */
object Banks {

    val NAMES = listOf(
        // 国有六大行
        "工商银行", "建设银行", "农业银行", "中国银行", "交通银行", "邮储银行",
        // 股份制
        "招商银行", "中信银行", "浦发银行", "民生银行", "光大银行", "兴业银行",
        "华夏银行", "平安银行", "广发银行", "浙商银行", "渤海银行", "恒丰银行",
        // 农商行（长名在前）
        "北京农商银行", "上海农商银行", "广州农商银行", "重庆农商银行", "成都农商银行",
        "东莞农商银行", "常熟农商银行", "江南农商银行",
        // 城商行
        "北京银行", "上海银行", "宁波银行", "南京银行", "江苏银行", "杭州银行",
        "天津银行", "重庆银行", "成都银行", "长沙银行", "汉口银行", "徽商银行",
        "哈尔滨银行", "台州银行", "青岛银行", "中原银行", "郑州银行", "东莞银行",
        "珠海华润银行", "盛京银行", "广州银行",
        // 外资行
        "汇丰银行", "渣打银行", "花旗银行", "东亚银行", "恒生银行", "星展银行",
        "富邦华一银行"
    )

    /** 发件域名 → 银行名（key 用带点的形式避免误命中） */
    private val DOMAINS = mapOf(
        // 国有大行
        "icbc" to "工商银行", "ccb." to "建设银行", "abchina" to "农业银行",
        "boc." to "中国银行", "bankcomm" to "交通银行", "bocomcc" to "交通银行", "psbc" to "邮储银行",
        // 股份行
        "cmbchina" to "招商银行", "citicbank" to "中信银行", "citiccard" to "中信银行", "ecitic" to "中信银行",
        "spdb" to "浦发银行", "cmbc." to "民生银行", "cebbank" to "光大银行",
        "cib.com" to "兴业银行", "cibmb" to "兴业银行", "hxb." to "华夏银行",
        "hxbank" to "华夏银行", "pingan" to "平安银行", "gdb." to "广发银行",
        "gfcb" to "广发银行", "cgbchina" to "广发银行", "czbank" to "浙商银行",
        "cbhb" to "渤海银行", "hfbank" to "恒丰银行",
        // 城商行
        "bankofbeijing" to "北京银行", "bosc" to "上海银行", "nbcb" to "宁波银行",
        "njcb" to "南京银行", "jsbchina" to "江苏银行", "hzbank" to "杭州银行",
        "bankoftianjin" to "天津银行", "cqcbank" to "重庆银行", "cdbank" to "成都银行",
        "bankofchangsha" to "长沙银行", "hkbchina" to "汉口银行", "hsbank" to "徽商银行",
        "hrbcb" to "哈尔滨银行", "tzbank" to "台州银行", "qdccb" to "青岛银行",
        "zybank" to "中原银行", "zzbank" to "郑州银行", "dongguanbank" to "东莞银行",
        "crcbank" to "珠海华润银行", "shengjingbank" to "盛京银行", "gzbank" to "广州银行",
        // 农商行
        "bjrcb" to "北京农商银行", "srcb" to "上海农商银行", "grcbank" to "广州农商银行",
        "cqrcb" to "重庆农商银行", "cdrcb" to "成都农商银行", "drcbank" to "东莞农商银行",
        "csrcbank" to "常熟农商银行", "jnbank" to "江南农商银行",
        // 外资行
        "hsbc" to "汇丰银行", "standardchartered" to "渣打银行", "citibank" to "花旗银行",
        "hkbea" to "东亚银行", "hangseng" to "恒生银行", "dbs." to "星展银行",
        "fubon" to "富邦华一银行"
    )

    /** 银行短信端口号 → 银行名（兜底用，短信一般以【银行名】开头为主） */
    private val SMS_NUMBERS = mapOf(
        "95588" to "工商银行", "95533" to "建设银行", "95599" to "农业银行",
        "95566" to "中国银行", "95559" to "交通银行", "95580" to "邮储银行",
        "95555" to "招商银行", "95558" to "中信银行", "95568" to "民生银行",
        "95595" to "光大银行", "95561" to "兴业银行", "95577" to "华夏银行",
        "95511" to "平安银行", "95508" to "广发银行", "95527" to "浙商银行",
        "95526" to "北京银行", "95594" to "上海银行", "95528" to "宁波银行"
    )

    fun fromAddress(address: String?): String? {
        val a = address?.lowercase() ?: return null
        for ((domain, name) in DOMAINS) if (a.contains(domain)) return name
        return null
    }

    fun fromSmsAddress(address: String?): String? {
        val a = address ?: return null
        for ((num, name) in SMS_NUMBERS) if (a.contains(num)) return name
        return null
    }

    fun fromText(text: String?): String? {
        if (text.isNullOrEmpty()) return null
        return NAMES.sortedByDescending { it.length }.firstOrNull { text.contains(it) }
    }

    /** 【招商银行信用卡中心】 → 招商银行 */
    fun cleanBracketName(raw: String): String {
        val cleaned = raw.substringBefore("信用卡").substringBefore("卡中心").trim()
        return fromText(cleaned) ?: cleaned.take(12)
    }
}
