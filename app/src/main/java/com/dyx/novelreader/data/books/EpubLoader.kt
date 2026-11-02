package com.dyx.novelreader.data.books

import com.dyx.novelreader.data.model.BookFormat
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URLDecoder
import java.util.zip.ZipInputStream

/**
 * 通用 zip 内文件读取（EPUB / DOCX / XLSX / PPTX 都是 zip 容器）。
 *
 * 书中资源通常几 MB，一次性解到内存最省事，也避免持有远端流。
 */
internal object ZipReader {

    private const val MAX_TOTAL_BYTES = 256 * 1024 * 1024

    /** 解出全部条目：entry 名（保留原始大小写）-> 内容 */
    fun readAll(bytes: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                if (entry.isDirectory) continue
                val data = zin.readBytes()
                total += data.size
                if (total > MAX_TOTAL_BYTES) throw IOException("压缩包解压后超过 256MB，已中止")
                out[entry.name] = data
            }
        }
        return out
    }

    fun text(files: Map<String, ByteArray>, path: String): String? =
        files[path]?.let { String(it, Charsets.UTF_8) }

    /** 大小写不敏感查找（不同打包工具目录名大小写不一致） */
    fun find(files: Map<String, ByteArray>, path: String): String? {
        files[path]?.let { return path }
        val lower = path.lowercase()
        return files.keys.firstOrNull { it.lowercase() == lower }
    }

    /** 相对路径解析：base 目录 + href，处理 `../` 与百分号编码 */
    fun resolve(baseDir: String, href: String): String {
        val cleanHref = runCatching {
            URLDecoder.decode(href.replace("+", "%2B"), "UTF-8")
        }.getOrDefault(href).substringBefore('#')

        val segments = ArrayDeque<String>()
        baseDir.split('/').filter { it.isNotEmpty() }.forEach { segments.addLast(it) }
        cleanHref.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeLast()
                else -> segments.addLast(part)
            }
        }
        return segments.joinToString("/")
    }

    fun dirOf(path: String): String = path.substringBeforeLast('/', "")
}

/** EPUB 解析：container.xml → OPF → spine 顺序 → 每个 xhtml 作为一章 */
object EpubLoader {

    fun load(bytes: ByteArray, fallbackTitle: String, limitChars: Int = 12_000_000): BookDocument {
        val files = ZipReader.readAll(bytes)
        if (files.isEmpty()) throw IOException("EPUB 内容为空或不是有效的 zip 包")

        // 1) META-INF/container.xml 找 OPF 路径
        val containerPath = ZipReader.find(files, "META-INF/container.xml")
            ?: throw IOException("不是有效的 EPUB：缺少 META-INF/container.xml")
        val containerXml = ZipReader.text(files, containerPath).orEmpty()
        val opfPath = Regex("""(?i)full-path\s*=\s*["']([^"']+)["']""")
            .find(containerXml)?.groupValues?.get(1)
            ?: throw IOException("EPUB 缺少 OPF 描述文件")

        val opf = ZipReader.text(files, opfPath) ?: run {
            val actual = ZipReader.find(files, opfPath) ?: throw IOException("EPUB 缺少 OPF 描述文件")
            ZipReader.text(files, actual)
        } ?: throw IOException("无法读取 EPUB 的 OPF 文件")

        val opfDir = ZipReader.dirOf(opfPath)

        // 2) manifest: id -> href
        val manifest = HashMap<String, String>()
        Regex("""(?is)<item\b[^>]*>""").findAll(opf).forEach { m ->
            val tag = m.value
            val id = attr(tag, "id")
            val href = attr(tag, "href")
            if (id != null && href != null) manifest[id] = href
        }

        // 3) spine: 阅读顺序
        val spineIds = Regex("""(?is)<itemref\b[^>]*>""").findAll(opf)
            .mapNotNull { attr(it.value, "idref") }
            .toList()

        val orderedHrefs = if (spineIds.isNotEmpty()) {
            spineIds.mapNotNull { manifest[it] }
        } else {
            manifest.values.filter { it.endsWith(".xhtml", true) || it.endsWith(".html", true) }
        }
        if (orderedHrefs.isEmpty()) throw IOException("EPUB 没有可读章节")

        // 4) 逐章抽取文本
        val builder = StringBuilder()
        val chapters = ArrayList<Chapter>()
        var index = 0
        var title = ""

        for (href in orderedHrefs) {
            val path = ZipReader.resolve(opfDir, href)
            val actual = ZipReader.find(files, path) ?: continue
            val raw = ZipReader.text(files, actual) ?: continue

            if (title.isEmpty()) {
                title = HtmlText.extractTitle(raw, "")
            }
            val plain = HtmlText.toPlainText(raw)
            if (plain.isBlank()) continue

            index++
            val chapterTitle = HtmlText.extractTitle(raw, "第 $index 章")
            // 章节标题单独占一行，正文另起
            val start = builder.length
            builder.append(chapterTitle).append('\n').append(plain).append("\n\n")
            chapters.add(Chapter(chapterTitle, start, builder.length))

            if (builder.length > limitChars) break
        }

        if (chapters.isEmpty()) throw IOException("EPUB 解析后没有文字内容（可能是纯图片漫画）")

        val text = TextDecoder.normalize(builder.toString())
        // 规范化后长度可能变化，按比例重算章节区间，保证 offset 有效
        val fixed = remapChapters(chapters, text.length, rawLength = builder.length)

        return BookDocument(
            title = title.ifBlank { fallbackTitle },
            format = BookFormat.EPUB,
            text = text,
            chapters = fixed,
            note = "${fixed.size} 章",
        )
    }

    private fun attr(tag: String, name: String): String? =
        Regex("""(?i)\b$name\s*=\s*["']([^"']*)["']""").find(tag)?.groupValues?.get(1)

    /** 文本规范化会改动长度，这里按比例把旧区间映射到新文本 */
    internal fun remapChapters(chapters: List<Chapter>, newLength: Int, rawLength: Int): List<Chapter> {
        if (rawLength <= 0 || chapters.isEmpty()) {
            return listOf(Chapter("正文", 0, newLength))
        }
        if (rawLength == newLength) return chapters
        val ratio = newLength.toDouble() / rawLength
        val mapped = chapters.map {
            Chapter(
                title = it.title,
                start = (it.start * ratio).toInt().coerceIn(0, newLength),
                end = (it.end * ratio).toInt().coerceIn(0, newLength),
            )
        }.filter { it.end > it.start }
        return mapped.ifEmpty { listOf(Chapter("正文", 0, newLength)) }
    }
}
