package com.dyx.novelreader.data.model

import android.os.Parcelable
import com.dyx.novelreader.data.RemoteFiles
import kotlinx.parcelize.Parcelize

/** 支持的远程协议 */
enum class RemoteProtocol(val label: String) {
    SMB("SMB"),
    WEBDAV("WebDAV"),
}

/**
 * 已保存的远程服务器配置（SMB 或 WebDAV）。
 *
 * @param host     SMB：`192.168.1.10` 或 `host:445`；WebDAV：`192.168.1.10:5005`（不含协议）
 * @param share    SMB：共享名（可留空表示浏览全部共享）；WebDAV：基础路径（如 `/dav/书库`，可为空）
 * @param secure   WebDAV 是否使用 https
 * @param insecureTls WebDAV 是否接受自签名证书（NAS 常见）
 */
@Parcelize
data class RemoteServer(
    val id: Long,
    val name: String,
    val protocol: RemoteProtocol = RemoteProtocol.SMB,
    val host: String,
    val share: String = "",
    val domain: String = "",
    val username: String = "",
    val password: String = "",
    val anonymous: Boolean = false,
    val secure: Boolean = false,
    val insecureTls: Boolean = false,
) : Parcelable {

    /** Android 侧稳定标识：防止 id 变化导致书架条目找不到服务器 */
    val key: String get() = "${protocol.name.lowercase()}://$host/$share|$username"

    /** WebDAV 基础路径：以 `/` 开头、不以 `/` 结尾；根目录返回空串 */
    val webdavPath: String
        get() = share.trim().trim('/').let { if (it.isEmpty()) "" else "/$it" }

    /** 卡片上展示的地址 */
    val addressLabel: String
        get() = when (protocol) {
            RemoteProtocol.SMB -> "$host/${share.trim('/')}"
            RemoteProtocol.WEBDAV -> "${if (secure) "https" else "http"}://$host$webdavPath"
        }
}

/** 条目类型：目录 / 可阅读文档 / 其它（不展示） */
enum class FileKind { DIR, BOOK, OTHER }

/** 文档格式 */
enum class BookFormat(val label: String) {
    TXT("TXT"),
    EPUB("EPUB"),
    PDF("PDF"),
    DOCX("Word"),
    DOC("DOC"),
    XLSX("Excel"),
    XLS("XLS"),
    PPTX("PPT"),
    PPT("PPT"),
    HTML("HTML"),
    RTF("RTF"),
    MOBI("MOBI"),
    AZW3("AZW3"),
    UNKNOWN("文档"),
}

/** 远程目录条目 */
@Parcelize
data class RemoteEntry(
    val name: String,
    val displayName: String,
    val isDir: Boolean,
    val sizeBytes: Long,
    val url: String,
    val kind: FileKind = FileKind.BOOK,
    // 由文件名推导，构造方漏传时也不会退化成「未知格式」
    val format: BookFormat = RemoteFiles.formatOf(name),
) : Parcelable

/** 局域网发现到的服务 */
data class DiscoveredService(
    val host: String,
    val port: Int,
    val protocol: RemoteProtocol,
    val name: String,
) {
    val label: String get() = host
}

/**
 * 书架条目。
 *
 * 进度锚点用「全书字符偏移」而不是页码：改字号、切换翻页/滚动后仍然能精确还原。
 *
 * @param id           稳定 ID（服务器 key + 远端 URL）
 * @param charOffset   已读到的全书字符偏移
 * @param progress     0f..1f，用于书架进度条
 * @param chapterTitle 上次读到的章节标题，用于副标题展示
 */
@Parcelize
data class Book(
    val id: String,
    val title: String,
    val url: String,
    val serverId: Long,
    val serverName: String,
    val format: BookFormat = BookFormat.TXT,
    val sizeBytes: Long = 0,
    val addedAt: Long = 0,
    val lastReadAt: Long = 0,
    val charOffset: Int = 0,
    val progress: Float = 0f,
    val chapterTitle: String = "",
    val pinned: Boolean = false,
    val finished: Boolean = false,
) : Parcelable {

    /** 书架副标题：章节 · 百分比 */
    val progressLabel: String
        get() = when {
            finished -> "已读完"
            progress <= 0f -> "未开始"
            chapterTitle.isNotBlank() -> "$chapterTitle · ${(progress * 100).toInt()}%"
            else -> "${(progress * 100).toInt()}%"
        }

    companion object {
        fun idOf(serverKey: String, url: String): String = "$serverKey|$url"
    }
}

/** 书架排序方式 */
enum class ShelfSort(val label: String) {
    RECENT("最近阅读"),
    ADDED("添加时间"),
    TITLE("书名"),
    PROGRESS("阅读进度"),
}

/** 阅读界面配色 */
enum class ReaderTheme(val label: String) {
    PAPER("纸白"),
    SEPIA("米黄"),
    GREEN("护眼绿"),
    NIGHT("夜间"),
}

/**
 * 阅读正文字体。
 *
 * 都取自系统内置字体族，不额外打包字体文件（体积为零，且中文由系统按族自动匹配
 * 思源宋体 / 思源黑体，不会出现方框或缺字）。
 */
enum class ReaderFont(val label: String, val sample: String) {
    SERIF("宋体", "宋"),
    SANS("黑体", "黑"),
    MONO("等宽", "同"),
}

/** 字重 */
enum class ReaderWeight(val label: String, val sample: String) {
    NORMAL("常规", "文"),
    BOLD("加粗", "文"),
}

/** 阅读排版设置 */
data class ReaderSettings(
    val fontSize: Float = 18f,
    val lineHeight: Float = 1.75f,
    val paragraphSpacing: Float = 0.7f,
    val pageMode: Boolean = true,
    val readerTheme: ReaderTheme = ReaderTheme.PAPER,
    val keepScreenOn: Boolean = true,
    val font: ReaderFont = ReaderFont.SERIF,
    val weight: ReaderWeight = ReaderWeight.NORMAL,
)
