package com.dyx.novelreader.data.books

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * 本地缓存：从共享服务器打开过的书，整份存到 `filesDir/books`。
 *
 * 下次打开先读本地，不再连服务器（断网、服务器关机时也能继续读）；
 * 移出书架时对应文件一并删除，不会无限堆积。
 *
 * 文件名用书籍 ID 的 SHA-1，只作为键，不保留扩展名——解析时格式来自 [com.dyx.novelreader.data.model.Book.format]。
 */
object BookCache {

    private const val DIR_NAME = "books"

    private fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    private fun nameOf(bookId: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(bookId.toByteArray(Charsets.UTF_8))
        return buildString {
            digest.take(12).forEach { append("%02x".format(it.toInt() and 0xff)) }
            append(".bin")
        }
    }

    fun fileFor(context: Context, bookId: String): File = File(dir(context), nameOf(bookId))

    /** 已缓存的文件；没有或为空返回 null */
    fun cachedFile(context: Context, bookId: String): File? {
        val file = fileFor(context, bookId)
        return if (file.isFile && file.length() > 0L) file else null
    }

    fun isCached(context: Context, bookId: String): Boolean = cachedFile(context, bookId) != null

    /**
     * 写入缓存。先写临时文件再改名，避免下载中断留下半截文件被当成完整缓存。
     * 失败不抛异常——缓存只是加速手段，不该影响阅读。
     */
    fun write(context: Context, bookId: String, bytes: ByteArray): File? = runCatching {
        val target = fileFor(context, bookId)
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        target
    }.getOrNull()

    fun delete(context: Context, bookId: String) {
        runCatching { fileFor(context, bookId).delete() }
    }

    fun deleteAll(context: Context) {
        runCatching { dir(context).listFiles()?.forEach { it.delete() } }
    }

    /** 单本缓存占用 */
    fun sizeOf(context: Context, bookId: String): Long =
        cachedFile(context, bookId)?.length() ?: 0L

    /** 全部缓存占用 */
    fun totalSize(context: Context): Long =
        dir(context).listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L
}
