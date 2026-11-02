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
 * 所有方法都在 IO 线程调用，异常信息直接面向用户展示。
 */
object DocumentLoader {

    fun load(
        context: Context,
        server: RemoteServer,
        entry: RemoteEntry,
        maxTextBytes: Long = RemoteFiles.MAX_TEXT_BYTES,
    ): BookDocument {
        val format = if (entry.format != BookFormat.UNKNOWN) entry.format else RemoteFiles.formatOf(entry.name)
        val title = entry.displayName.substringBeforeLast('.')

        return when (format) {
            BookFormat.TXT, BookFormat.UNKNOWN -> {
                val bytes = RemoteFiles.readFile(server, entry.url, maxTextBytes, truncate = true)
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
                val bytes = RemoteFiles.readFile(server, entry.url, maxTextBytes, truncate = true)
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
                val bytes = RemoteFiles.readFile(server, entry.url, maxTextBytes, truncate = true)
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
                val bytes = RemoteFiles.readFile(server, entry.url, RemoteFiles.MAX_DOC_BYTES, truncate = false)
                EpubLoader.load(bytes, title)
            }

            BookFormat.DOCX, BookFormat.XLSX, BookFormat.PPTX -> {
                val bytes = RemoteFiles.readFile(server, entry.url, RemoteFiles.MAX_DOC_BYTES, truncate = false)
                OoxmlLoader.load(bytes, format, title)
            }

            BookFormat.PDF -> loadPdf(context, server, entry, title)

            BookFormat.DOC, BookFormat.XLS, BookFormat.PPT -> throw IOException(
                "旧版 ${format.label} 是二进制格式，暂不支持解析。可用 Office/WPS 另存为 " +
                        "${format.name.lowercase()}x 或 PDF 后再试。"
            )

            BookFormat.MOBI, BookFormat.AZW3 -> throw IOException(
                "${format.label} 是加密/专有电子书格式，暂不支持。建议先转换为 EPUB 或 TXT。"
            )
        }
    }

    /** PDF：整份下载到缓存，用系统 PdfRenderer 取页数，阅读时按页渲染成位图 */
    private fun loadPdf(
        context: Context,
        server: RemoteServer,
        entry: RemoteEntry,
        title: String,
    ): BookDocument {
        val bytes = RemoteFiles.readFile(server, entry.url, RemoteFiles.MAX_DOC_BYTES, truncate = false)
        if (bytes.isEmpty()) throw IOException("PDF 文件是空的")

        val dir = File(context.cacheDir, "pdf").apply { mkdirs() }
        val file = File(dir, "book_${entry.url.hashCode().toUInt().toString(16)}.pdf")
        file.writeBytes(bytes)

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

    /** 清理 PDF 缓存（退出应用或换书时调用，避免长期占用空间） */
    fun clearPdfCache(context: Context) {
        runCatching {
            File(context.cacheDir, "pdf").listFiles()?.forEach { it.delete() }
        }
    }
}
