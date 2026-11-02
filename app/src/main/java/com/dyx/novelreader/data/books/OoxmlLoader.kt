package com.dyx.novelreader.data.books

import com.dyx.novelreader.data.model.BookFormat
import java.io.IOException

/**
 * Office Open XML 文档（docx / xlsx / pptx）文本抽取。
 *
 * 这三种格式本质是 zip + XML，正文文字都在固定标签里：
 *  - docx: word/document.xml 里的 `<w:p>` 段落 / `<w:t>` 文本
 *  - xlsx: xl/sharedStrings.xml 与 xl/worksheets/sheetN.xml
 *  - pptx: ppt/slides/slideN.xml 里的 `<a:t>`
 *
 * 目标只是"能读内容"，不还原样式、表格线、图片。
 */
object OoxmlLoader {

    fun load(
        bytes: ByteArray,
        format: BookFormat,
        fallbackTitle: String,
    ): BookDocument {
        val files = ZipReader.readAll(bytes)
        if (files.isEmpty()) throw IOException("文件不是有效的 Office 文档（zip 解压失败）")

        return when (format) {
            BookFormat.DOCX -> loadDocx(files, fallbackTitle)
            BookFormat.XLSX -> loadXlsx(files, fallbackTitle)
            BookFormat.PPTX -> loadPptx(files, fallbackTitle)
            else -> throw IOException("不支持的格式：${format.label}")
        }
    }

    // ---------------- docx ----------------

    private fun loadDocx(files: Map<String, ByteArray>, fallbackTitle: String): BookDocument {
        val path = ZipReader.find(files, "word/document.xml")
            ?: throw IOException("不是有效的 Word 文档：缺少 word/document.xml")
        val xml = ZipReader.text(files, path).orEmpty()

        val text = StringBuilder()
        Regex("""(?s)<w:p\b[^>]*>(.*?)</w:p>""").findAll(xml).forEach { p ->
            val body = p.groupValues[1]
            val line = StringBuilder()
            Regex("""(?s)<w:(t|tab|br)\b[^>]*?(?:/>|>(.*?)</w:\1>)""").findAll(body).forEach { token ->
                when (token.groupValues[1]) {
                    "t" -> line.append(HtmlText.decodeEntities(token.groupValues[2]))
                    "tab" -> line.append('\t')
                    "br" -> line.append('\n')
                }
            }
            val content = line.toString().trimEnd()
            // 保留空行（段落间距），但连续空行后续统一压缩
            text.append(content).append('\n')
        }

        val normalized = TextDecoder.normalize(text.toString())
        if (normalized.isBlank()) throw IOException("Word 文档里没有可读的文字（可能是纯图片）")

        return BookDocument(
            title = docTitle(files).ifBlank { fallbackTitle },
            format = BookFormat.DOCX,
            text = normalized,
            chapters = ChapterSplitter.split(normalized),
            note = "Word 文本",
        )
    }

    private fun docTitle(files: Map<String, ByteArray>): String {
        val core = ZipReader.find(files, "docProps/core.xml") ?: return ""
        val xml = ZipReader.text(files, core).orEmpty()
        return Regex("""(?s)<dc:title[^>]*>(.*?)</dc:title>""")
            .find(xml)?.groupValues?.get(1)?.let(HtmlText::decodeEntities)?.trim().orEmpty()
    }

    // ---------------- xlsx ----------------

    private fun loadXlsx(files: Map<String, ByteArray>, fallbackTitle: String): BookDocument {
        // 共享字符串表：单元格里存的是索引
        val shared = ArrayList<String>()
        ZipReader.find(files, "xl/sharedStrings.xml")?.let { path ->
            val xml = ZipReader.text(files, path).orEmpty()
            Regex("""(?s)<si\b[^>]*>(.*?)</si>""").findAll(xml).forEach { si ->
                val joined = StringBuilder()
                Regex("""(?s)<t\b[^>]*>(.*?)</t>""").findAll(si.groupValues[1]).forEach {
                    joined.append(HtmlText.decodeEntities(it.groupValues[1]))
                }
                shared.add(joined.toString())
            }
        }

        // 逐个 sheet 抽取
        val sheets = files.keys
            .filter { it.startsWith("xl/worksheets/sheet", true) && it.endsWith(".xml", true) }
            .sortedBy { naturalSheetIndex(it) }
        if (sheets.isEmpty()) throw IOException("Excel 文件里没有工作表")

        val text = StringBuilder()
        val chapters = ArrayList<Chapter>()
        sheets.forEachIndexed { sheetIdx, sheetPath ->
            val xml = ZipReader.text(files, sheetPath).orEmpty()
            val title = "工作表 ${sheetIdx + 1}"
            val start = text.length
            text.append(title).append('\n')

            Regex("""(?s)<row\b[^>]*>(.*?)</row>""").findAll(xml).forEach { row ->
                val cells = ArrayList<String>()
                Regex("""(?s)<c\b([^>]*)>(.*?)</c>""").findAll(row.groupValues[1]).forEach { c ->
                    val attrs = c.groupValues[1]
                    val body = c.groupValues[2]
                    val type = Regex("""t\s*=\s*"([^"]*)"""").find(attrs)?.groupValues?.get(1)
                    val raw = Regex("""(?s)<v\b[^>]*>(.*?)</v>""").find(body)?.groupValues?.get(1)
                    val inline = Regex("""(?s)<is\b[^>]*>(.*?)</is>""").find(body)?.groupValues?.get(1)
                    val value = when {
                        type == "s" && raw != null -> shared.getOrNull(raw.trim().toIntOrNull() ?: -1).orEmpty()
                        inline != null -> Regex("""(?s)<t\b[^>]*>(.*?)</t>""")
                            .findAll(inline).joinToString("") { HtmlText.decodeEntities(it.groupValues[1]) }
                        raw != null -> HtmlText.decodeEntities(raw)
                        else -> ""
                    }
                    cells.add(value.trim())
                }
                if (cells.any { it.isNotEmpty() }) {
                    text.append(cells.joinToString("\t")).append('\n')
                }
            }
            text.append('\n')
            if (text.length > start) chapters.add(Chapter(title, start, text.length))
        }

        val normalized = TextDecoder.normalize(text.toString())
        if (normalized.isBlank()) throw IOException("Excel 里没有可读的文字")

        return BookDocument(
            title = docTitle(files).ifBlank { fallbackTitle },
            format = BookFormat.XLSX,
            text = normalized,
            chapters = chapters.ifEmpty { ChapterSplitter.split(normalized) },
            note = "${sheets.size} 个工作表",
        )
    }

    private fun naturalSlideIndex(path: String): Int =
        Regex("""slide(\d+)""").find(path)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private fun naturalSheetIndex(path: String): Int =
        Regex("""sheet(\d+)""").find(path)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    // ---------------- pptx ----------------

    private fun loadPptx(files: Map<String, ByteArray>, fallbackTitle: String): BookDocument {
        val slides = files.keys
            .filter { it.startsWith("ppt/slides/slide", true) && it.endsWith(".xml", true) }
            .sortedBy { naturalSlideIndex(it) }
        if (slides.isEmpty()) throw IOException("PPT 里没有幻灯片")

        val text = StringBuilder()
        val chapters = ArrayList<Chapter>()
        slides.forEachIndexed { idx, path ->
            val xml = ZipReader.text(files, path).orEmpty()
            val lines = Regex("""(?s)<a:t\b[^>]*>(.*?)</a:t>""").findAll(xml)
                .map { HtmlText.decodeEntities(it.groupValues[1]).trim() }
                .filter { it.isNotEmpty() }
                .toList()
            if (lines.isEmpty()) return@forEachIndexed

            val title = lines.first().take(40)
            val start = text.length
            text.append(title).append('\n')
            lines.drop(1).forEach { text.append(it).append('\n') }
            text.append('\n')
            chapters.add(Chapter("第 ${idx + 1} 页 · $title", start, text.length))
        }

        val normalized = TextDecoder.normalize(text.toString())
        if (normalized.isBlank()) throw IOException("PPT 里没有可读的文字（可能是纯图片）")

        return BookDocument(
            title = docTitle(files).ifBlank { fallbackTitle },
            format = BookFormat.PPTX,
            text = normalized,
            chapters = chapters.ifEmpty { listOf(Chapter("正文", 0, normalized.length)) },
            note = "${slides.size} 页",
        )
    }
}

/** RTF：只是去掉控制字，能看正文即可 */
object RtfLoader {

    fun toPlainText(raw: String): String {
        var s = raw
        // 跳过字体表/样式表等二进制段
        s = s.replace(Regex("""(?s)\{\\(?:fonttbl|colortbl|stylesheet|info|pict).*?\}"""), "")
        // \'xx 十六进制字节 → 交给后续按 GBK 处理太复杂，这里直接丢弃
        s = s.replace(Regex("""\\'[0-9a-fA-F]{2}"""), "")
        // \uNNNN? Unicode 转义
        s = s.replace(Regex("""\\u(-?\d+)\?]?""")) { m ->
            val code = m.groupValues[1].toIntOrNull() ?: 0
            val fixed = if (code < 0) code + 65536 else code
            runCatching { String(Character.toChars(fixed)) }.getOrDefault("")
        }
        // 段落/换行
        s = s.replace("\\par", "\n").replace("\\line", "\n")
        // 其它控制字
        s = s.replace(Regex("""\\[a-zA-Z]+-?\d*\s?"""), "")
        s = s.replace(Regex("""[{}]"""), "")
        return TextDecoder.normalize(s).trim()
    }
}
