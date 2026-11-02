package com.dyx.novelreader.ui.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints

/**
 * 按可用宽高把文本切成页。
 *
 * 做法：从页首开始二分查找"这一页最多能放到第几个字"，
 * 用 [TextMeasurer] 实测排版高度判断是否溢出，因此字号、行距、
 * 标点挤压、中英混排都是真实排版结果，不会出现半行被截断。
 *
 * 复杂度：每页约 10 次测量，一章通常几十页，整体在几十毫秒级。
 */
class Paginator(
    private val measurer: TextMeasurer,
    private val style: TextStyle,
    private val maxWidth: Int,
    private val maxHeight: Int,
) {

    /**
     * 把 `[from, to)` 切成若干页，返回每页的字符区间。
     * 页区间右端开区间，取文本用 `text.substring(range.first, range.last + 1)`。
     */
    fun paginate(text: String, from: Int, to: Int): List<IntRange> {
        if (to <= from || maxWidth <= 0 || maxHeight <= 0) return emptyList()

        val pages = ArrayList<IntRange>((to - from) / 400 + 2)
        var start = from
        while (start < to) {
            val end = findPageEnd(text, start, to)
            pages.add(start until end)
            start = end
        }
        return pages
    }

    private fun findPageEnd(text: String, start: Int, limit: Int): Int {
        var lo = start + 1
        var hi = minOf(start + estimateCharsPerPage() * 2, limit)
        var best = start + 1

        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val height = measureHeight(text, start, mid)
            if (height <= maxHeight) {
                best = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return best.coerceIn(start + 1, limit)
    }

    private fun measureHeight(text: String, start: Int, end: Int): Int {
        val slice = text.substring(start, end)
        val result = measurer.measure(
            text = AnnotatedString(slice),
            style = style,
            constraints = Constraints(maxWidth = maxWidth),
            softWrap = true,
            maxLines = Int.MAX_VALUE,
        )
        return result.size.height
    }

    /** 粗略估算一页字符数，用来给二分一个合理上界，减少测量次数 */
    private fun estimateCharsPerPage(): Int {
        val lineHeightPx = (style.lineHeight.value * densityScale()).toInt().coerceAtLeast(1)
        val charsPerLine = (maxWidth / (style.fontSize.value * densityScale() * 0.95f)).toInt()
        val lines = (maxHeight / lineHeightPx).coerceAtLeast(1)
        return (charsPerLine * lines).coerceIn(50, 4000)
    }

    /** Math：估算时用比例即可，真实高度由 measure 决定 */
    private fun densityScale(): Float = 1f
}
