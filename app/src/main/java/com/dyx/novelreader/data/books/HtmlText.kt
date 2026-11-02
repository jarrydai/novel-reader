package com.dyx.novelreader.data.books

/**
 * 极简 HTML/XHTML → 纯文本。
 *
 * 阅读器只需要段落文字，不需要样式、图片、脚注链接，
 * 因此用标签剥离 + 实体解码即可，避免引入体积巨大的 HTML 解析库。
 */
object HtmlText {

    private val scriptStyle = Regex("""(?is)<(script|style)[^>]*>.*?</\1>""")
    private val comment = Regex("""(?s)<!--.*?-->""")
    private val blockBreak = Regex(
        """(?i)<(?:br|/p|/div|/h[1-6]|/li|/tr|/section|/article|/blockquote)[^>]*>"""
    )
    private val headingTag = Regex("""(?is)<h[1-3][^>]*>(.*?)</h[1-3]>""")
    private val titleTag = Regex("""(?is)<title[^>]*>(.*?)</title>""")
    private val anyTag = Regex("""(?s)<[^>]+>""")
    private val entity = Regex("""&(#x?[0-9A-Fa-f]{1,6}|[a-zA-Z]{2,8});""")

    /** 转为段落文本：块级标签转换行，行内标签直接去掉 */
    fun toPlainText(html: String): String {
        var s = html
        s = comment.replace(s, "")
        s = scriptStyle.replace(s, "")
        s = blockBreak.replace(s, "\n")
        s = anyTag.replace(s, "")
        s = decodeEntities(s)
        // 每行去首尾空白，连续空行压成一个
        s = s.split('\n').joinToString("\n") { it.trim() }
        s = s.replace(Regex("\n{3,}"), "\n\n")
        return s.trim()
    }

    /** 抽取文档标题：<title> 优先，其次第一个 h1~h3 */
    fun extractTitle(html: String, fallback: String): String {
        titleTag.find(html)?.groupValues?.get(1)?.let { raw ->
            val t = decodeEntities(anyTag.replace(raw, "")).trim()
            if (t.isNotEmpty()) return t
        }
        headingTag.find(html)?.groupValues?.get(1)?.let { raw ->
            val t = decodeEntities(anyTag.replace(raw, "")).trim()
            if (t.isNotEmpty()) return t
        }
        return fallback
    }

    fun decodeEntities(input: String): String {
        if (!input.contains('&')) return input
        return entity.replace(input) { m ->
            val token = m.groupValues[1]
            when {
                token.startsWith("#x", true) ->
                    token.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
                token.startsWith("#") ->
                    token.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
                else -> named[token.lowercase()] ?: m.value
            }
        }
    }

    private val named = mapOf(
        "nbsp" to " ",
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "hellip" to "…",
        "mdash" to "—",
        "ndash" to "–",
        "ldquo" to "“",
        "rdquo" to "”",
        "lsquo" to "‘",
        "rsquo" to "’",
        "middot" to "·",
        "bull" to "•",
        "copy" to "©",
        "reg" to "®",
        "deg" to "°",
        "times" to "×",
        "divide" to "÷",
        "emsp" to "　",
        "ensp" to " ",
        "thinsp" to " ",
        "shy" to "",
    )
}
