package com.dyx.novelreader.data.books

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.dyx.novelreader.data.RemoteFiles
import com.dyx.novelreader.data.model.BookFormat
import com.dyx.novelreader.data.model.RemoteEntry
import com.dyx.novelreader.data.model.RemoteServer
import java.io.File
import java.io.IOException

/**
 * 文档加载总入口：按扩展名选择解析器，产出统一的 [BookDocument]。
 *
 * 传入 [cacheKey]（书籍 ID）时优先读本地缓存，没有缓存才连服务器并把整份文件存下来。
 * 所有方法都在 IO 线程调用，异常信息直接面向用户展示。
 */
object DocumentLoader {

    /** 字节来源：可能是本地缓存文件，也可能是远端连接 */
    private interface Source {
        /** 本地文件路径（缓存命中时有值，PDF 可直接拿来渲染） */
        val localPath: String?
        fun bytes(maxBytes: Long, truncate: Boolean): ByteArray
    }

    private class RemoteSource(
        private val context: Context,
        private val server: RemoteServer,
        private val url: String,
        private val cacheKey: String?,
    ) : Source {

        override val localPath: String? = null

        override fun bytes(maxBytes: Long, truncate: Boolean): ByteArray {
            val data = RemoteFiles.readFile(server, url, maxBytes, truncate)
            // 只有完整拿到文件才写缓存；被截断的大文件不缓存，免得下次读到残本
            if (cacheKey != null && (!truncate || data.size.toLong() < maxBytes)) {
                BookCache.write(context, cacheKey, data)
            }
            return data
        }
    }

    private class CachedSource(private val file: File) : Source {
        override val localPath: String = file.absolutePath
        override fun bytes(maxBytes: Long, truncate: Boolean): ByteArray = file.readBytes()
    }

    fun load(
        context: Context,
        server: RemoteServer,
        entry: RemoteEntry,
        cacheKey: String? = null,
        maxTextBytes: Long = RemoteFiles.MAX_TEXT_BYTES,
    ): BookDocument {
        val format = if (entry.format != BookFormat.UNKNOWN) entry.format else RemoteFiles.formatOf(entry.name)
        val title = entry.displayName.substringBeforeLast('.')

        val cached = cacheKey?.let { BookCache.cachedFile(context, it) }
        if (cached != null) {
            try {
                return parse(context, format, entry, title, CachedSource(cached), cacheKey, maxTextBytes)
            } catch (t: Throwable) {
                // 缓存读坏了（文件被改坏、早先版本格式不同），丢掉重下一次
                BookCache.delete(context, cacheKey)
            }
        }
        val remote = RemoteSource(context, server, entry.url, cacheKey)
        return parse(context, format, entry, title, remote, cacheKey, maxTextBytes)
    }

    private fun parse(
        context: Context,
        format: BookFormat,
        entry: RemoteEntry,
        title: String,
        source: Source,
        cacheKey: String?,
        maxTextBytes: Long,
    ): BookDocument = when (format) {
        BookFormat.TXT, BookFormat.UNKNOWN -> {
            val bytes = source.bytes(maxTextBytes, truncate = true)
            if (bytes.isEmpty()) throw IOException("文件是空的")
            val decoded = TextDecoder.decode(bytes)
            val truncated = bytes.size >= maxTextBytes
            BookDocument(
                title = title,
                format = BookFormat.TXT,
                text = decoded.text,
                chapters = ChapterSplitter.split(decoded.text),
                note = buildString {
                    append("编码 ${decoded.charsetName}")
                    if (truncated) append(" · 文件较大，仅加载前 ${maxTextBytes / 1024 / 1024} MB")
                },
            )
        }

        BookFormat.HTML -> {
            val bytes = source.bytes(maxTextBytes, truncate = true)
            val html = TextDecoder.decode(bytes).text
            val plain = HtmlText.toPlainText(html)
            if (plain.isBlank()) throw IOException("HTML 里没有可读文字")
            BookDocument(
                title = HtmlText.extractTitle(html, title),
                format = BookFormat.HTML,
                text = plain,
                chapters = ChapterSplitter.split(plain),
                note = "HTML 已转为纯文本",
            )
        }

        BookFormat.RTF -> {
            val bytes = source.bytes(maxTextBytes, truncate = true)
            val text = RtfLoader.toPlainText(TextDecoder.decode(bytes).text)
            if (text.isBlank()) throw IOException("RTF 里没有可读文字")
            BookDocument(
                title = title,
                format = BookFormat.RTF,
                text = text,
                chapters = ChapterSplitter.split(text),
                note = "RTF 已转为纯文本",
            )
        }

        BookFormat.EPUB -> {
            val bytes = source.bytes(RemoteFiles.MAX_DOC_BYTES, truncate = false)
            EpubLoader.load(bytes, title)
        }

        BookFormat.DOCX, BookFormat.XLSX, BookFormat.PPTX -> {
            val bytes = source.bytes(RemoteFiles.MAX_DOC_BYTES, truncate = false)
            OoxmlLoader.load(bytes, format, title)
        }

        BookFormat.PDF -> loadPdf(context, entry, title, source, cacheKey)

        BookFormat.DOC, BookFormat.XLS, BookFormat.PPT -> throw IOException(
            "旧版 ${format.label} 是二进制格式，暂不支持解析。可用 Office/WPS 另存为 " +
                    "${format.name.lowercase()}x 或 PDF 后再试。"
        )

        BookFormat.MOBI, BookFormat.AZW3 -> throw IOException(
            "${format.label} 是加密/专有电子书格式，暂不支持。建议先转换为 EPUB 或 TXT。"
        )
    }

    /**
     * PDF：整份存到缓存盘，用系统 PdfRenderer 取页数，阅读时按页渲染成位图。
     *
     * 缓存命中时直接复用该文件；否则落盘到缓存目录，下次打开就不必再下载。
     */
    private fun loadPdf(
        context: Context,
        entry: RemoteEntry,
        title: String,
        source: Source,
        cacheKey: String?,
    ): BookDocument {
        val bytes = source.bytes(RemoteFiles.MAX_DOC_BYTES, truncate = false)
        if (bytes.isEmpty()) throw IOException("PDF 文件是空的")

        val file = source.localPath?.let(::File)
            // 远端这次已经把整份写进缓存了，直接复用，别写第二遍
            ?: cacheKey?.let { key -> BookCache.cachedFile(context, key) ?: BookCache.write(context, key, bytes) }
            ?: File(
                File(context.cacheDir, "pdf").apply { mkdirs() },
                "book_${entry.url.hashCode().toUInt().toString(16)}.pdf",
            ).also { it.writeBytes(bytes) }

        val pageCount = try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { it.pageCount }
            }
        } catch (e: Throwable) {
            file.delete()
            throw IOException("PDF 无法打开（可能已加密或缺字体）：${e.message ?: "未知原因"}", e)
        }
        if (pageCount <= 0) {
            file.delete()
            throw IOException("PDF 没有页面")
        }

        // PDF 走位图渲染，text 只是占位；进度按页计算（charOffset 存页号）
        val virtualText = " ".repeat(pageCount)
        return BookDocument(
            title = title,
            format = BookFormat.PDF,
            text = virtualText,
            chapters = List(pageCount) { i ->
                Chapter("第 ${i + 1} 页", i, i + 1)
            },
            note = "$pageCount 页 · 按页显示",
            pdfPath = file.absolutePath,
            pageCount = pageCount,
        )
    }
}
