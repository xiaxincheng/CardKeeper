package com.juxi.cardkeeper.mail

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/** 从 PDF 账单附件提取文字；加密 PDF 用候选密码逐个尝试 */
object PdfExtractor {

    /**
     * @return 提取出的文本；打不开（含密码不对）、提取不到文字（如扫描件）返回 null
     */
    fun extract(bytes: ByteArray, passwords: List<String>): String? {
        val doc = openDoc(bytes, passwords) ?: return null
        return try {
            PDFTextStripper().getText(doc).trim().ifEmpty { null }
        } catch (_: Exception) {
            null
        } finally {
            try { doc.close() } catch (_: Exception) {}
        }
    }

    private fun openDoc(bytes: ByteArray, passwords: List<String>): PDDocument? {
        // 部分银行 PDF 用空密码加密，先试无密码/空密码，再试用户填的密码
        val candidates = listOf("") + passwords
        for (pw in candidates) {
            try {
                return if (pw.isEmpty()) PDDocument.load(bytes) else PDDocument.load(bytes, pw)
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }
}
