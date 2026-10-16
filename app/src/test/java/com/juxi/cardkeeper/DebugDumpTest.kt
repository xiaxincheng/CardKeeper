package com.juxi.cardkeeper

import org.junit.Test
import java.io.File

/** 诊断辅助：把六封真实邮件的解析结果落盘，便于人工核对 */
class DebugDumpTest {
    @Test
    fun dumpParseResults() {
        val f = File("/tmp/parse_all.txt")
        if (f.exists()) {
            val content = f.readText(Charsets.UTF_8)
            // 换行归一化后再验一次（与 App 行为一致）
            val normalized = content.replace("\r\n", "\n")
            File("/tmp/parse_all_normalized.txt").writeText(normalized, Charsets.UTF_8)
        }
    }
}
