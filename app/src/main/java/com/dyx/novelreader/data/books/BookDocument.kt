package com.dyx.novelreader.data.books

import com.dyx.novelreader.data.model.BookFormat

/**
 * 一章：标题 + 在全文中的字符区间 `[start, end)`。
 *
 * 用区间而不是独立字符串，好处是整本书只有一份 text，
 * 分页、跳转、进度换算都不必做拼接。
 */
data class Chapter(
    val title: String,
    val start: Int,
    val end: Int,
) {
    val length: Int get() = end - start
}

/**
 * 解析后的文档。
 *
 * @param text     全文（PDF 为空的虚拟文本，仅用作进度刻度）
 * @param chapters 章节列表，至少一项
 * @param note     解析说明（编码、页数、"已提取文本"等），展示在阅读页顶部
 * @param pdfPath  PDF 临时文件路径（仅 PDF 有值，按页渲染）
 * @param pageCount PDF 页数
 */
class BookDocument(
    val title: String,
    val format: BookFormat,
    val text: String,
    val chapters: List<Chapter>,
    val note: String = "",
    val pdfPath: String? = null,
    val pageCount: Int = 0,
) {
    val length: Int get() = text.length

    val isPdf: Boolean get() = pdfPath != null && pageCount > 0

    /** 偏移落在第几章 */
    fun chapterIndexAt(offset: Int): Int {
        if (chapters.isEmpty()) return 0
        val safe = offset.coerceIn(0, maxOf(length - 1, 0))
        var lo = 0
        var hi = chapters.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (chapters[mid].start <= safe) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun chapterAt(offset: Int): Chapter? = chapters.getOrNull(chapterIndexAt(offset))

    /** 0f..1f */
    fun progressOf(offset: Int): Float {
        if (length <= 0) return 0f
        return (offset.toFloat() / length).coerceIn(0f, 1f)
    }
}

/** 章节识别：中文"第 N 章/节/回/卷"、英文 Chapter N、Markdown 标题等 */
object ChapterSplitter {

    private val patterns = listOf(
        // 第123章 / 第十二回 / 第三节 / 第一卷 —— 允许前后空白与常见分隔符
        Regex("""^\s*第[0-9０-９一二三四五六七八九十百千万零两]{1,10}[章节回卷篇集](?:\s|$|[：:、.．·　]).{0,40}$"""),
        // 第123章 标题（无分隔符，整体较短）
        Regex("""^\s*第[0-9０-９一二三四五六七八九十百千万零两]{1,10}[章节回卷篇集][^\n]{0,40}$"""),
        // Chapter 12 / CHAPTER XII
        Regex("""^\s*(?:Chapter|CHAPTER|chapter)\s+[0-9IVXLCDMivxlcdm]{1,8}[^\n]{0,40}$"""),
        // Markdown 标题
        Regex("""^\s*#{1,3}\s+\S.{0,40}$"""),
        // 002.txt 之类的整行书名号
        Regex("""^\s*[【\[（(]{1}[^】\]）)\n]{1,24}[】\]）)]{1}\s*$"""),
    )

    private const val MAX_TITLE_LEN = 60

    /**
     * 切分章节。找不到任何章节标记时，按 [fallbackChunk] 个字符等分，
     * 保证目录始终可用（进度条、跳转都需要粒度）。
     */
    fun split(text: String, fallbackChunk: Int = 6000): List<Chapter> {
        if (text.isBlank()) return listOf(Chapter("正文", 0, text.length))

        val starts = ArrayList<Int>(256)
        val titles = ArrayList<String>(256)
        var lineStart = 0
        while (lineStart <= text.length) {
            var lineEnd = text.indexOf('\n', lineStart)
            if (lineEnd < 0) lineEnd = text.length
            val lineLen = lineEnd - lineStart
            if (lineLen in 1..MAX_TITLE_LEN) {
                val line = text.substring(lineStart, lineEnd)
                if (isChapterTitle(line)) {
                    starts.add(lineStart)
                    titles.add(line.trim())
                }
            }
            if (lineEnd >= text.length) break
            lineStart = lineEnd + 1
        }

        // 章节标记太少（< 2）视为无章节，走等分
        if (starts.size < 2) return chunk(text, fallbackChunk)

        // 第一处标记之前若有大段正文（书名/简介），单独作为"前言"
        val result = ArrayList<Chapter>(starts.size + 1)
        if (starts.first() > 200) {
            result.add(Chapter("前言", 0, starts.first()))
        }
        for (i in starts.indices) {
            val start = starts[i]
            val end = if (i + 1 < starts.size) starts[i + 1] else text.length
            if (end > start) result.add(Chapter(titles[i], start, end))
        }
        return result.ifEmpty { chunk(text, fallbackChunk) }
    }

    private fun isChapterTitle(line: String): Boolean =
        patterns.any { it.matches(line) }

    private fun chunk(text: String, size: Int): List<Chapter> {
        val chapters = ArrayList<Chapter>()
        var start = 0
        var index = 1
        while (start < text.length) {
            // 尽量在换行处断开，避免把句子劈成两半
            var end = minOf(start + size, text.length)
            if (end < text.length) {
                val nl = text.indexOf('\n', end)
                if (nl in end until minOf(end + 400, text.length)) end = nl + 1
            }
            chapters.add(Chapter("第 $index 节", start, end))
            start = end
            index++
        }
        return chapters.ifEmpty { listOf(Chapter("正文", 0, text.length)) }
    }
}
