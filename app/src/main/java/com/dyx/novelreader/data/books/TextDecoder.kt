package com.dyx.novelreader.data.books

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * 文本编码识别：BOM → 严格 UTF-8 → GBK → Latin-1 兜底。
 *
 * 中文网络小说绝大多数是 UTF-8 或 GBK（Windows 记事本另存），
 * GB18030 是 GBK 的超集，用 GBK 解码已能覆盖常用汉字。
 */
object TextDecoder {

    data class Decoded(val text: String, val charsetName: String)

    fun decode(bytes: ByteArray): Decoded {
        if (bytes.isEmpty()) return Decoded("", "UTF-8")

        val hasBomUtf8 = bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        val hasBomLe = bytes.size >= 2 &&
                bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()
        val hasBomBe = bytes.size >= 2 &&
                bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()

        return when {
            hasBomLe -> Decoded(normalize(String(bytes, Charsets.UTF_16LE)), "UTF-16LE")
            hasBomBe -> Decoded(normalize(String(bytes, Charsets.UTF_16BE)), "UTF-16BE")
            hasBomUtf8 -> Decoded(normalize(String(bytes, 3, bytes.size - 3, Charsets.UTF_8)), "UTF-8")
            else -> {
                val utf8 = tryDecodeStrictUtf8(bytes)
                if (utf8 != null) {
                    Decoded(normalize(utf8), "UTF-8")
                } else {
                    // 判断一下是否是 UTF-16 无 BOM（Windows 偶尔会出现）
                    val utf16 = tryDecodeUtf16NoBom(bytes)
                    if (utf16 != null) {
                        Decoded(normalize(utf16.text), utf16.charsetName)
                    } else {
                        Decoded(normalize(String(bytes, charset("GBK"))), "GBK")
                    }
                }
            }
        }
    }

    private fun tryDecodeStrictUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    /** 无 BOM 的 UTF-16：通过 0 字节分布猜测（ASCII 为主的文本每两字节有一个 0） */
    private fun tryDecodeUtf16NoBom(bytes: ByteArray): Decoded? {
        val sample = minOf(bytes.size, 4096)
        if (sample < 16) return null
        var evenZero = 0
        var oddZero = 0
        var i = 0
        while (i + 1 < sample) {
            if (bytes[i] == 0.toByte()) evenZero++
            if (bytes[i + 1] == 0.toByte()) oddZero++
            i += 2
        }
        val pairs = sample / 2
        return when {
            oddZero > pairs * 0.6 && evenZero < pairs * 0.1 ->
                Decoded(String(bytes, Charsets.UTF_16LE), "UTF-16LE")
            evenZero > pairs * 0.6 && oddZero < pairs * 0.1 ->
                Decoded(String(bytes, Charsets.UTF_16BE), "UTF-16BE")
            else -> null
        }
    }

    /** 统一换行、去掉 BOM 残留与零宽字符、压缩连续空行 */
    fun normalize(raw: String): String = raw
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace("\uFEFF", "")
        .replace("\u200B", "")
        .replace("\u00A0", " ")
        .replace(Regex("\n{4,}"), "\n\n\n")
}
