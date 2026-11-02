package com.dyx.novelreader.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dyx.novelreader.data.model.Book
import com.dyx.novelreader.data.model.BookFormat
import com.dyx.novelreader.data.model.ReaderFont
import com.dyx.novelreader.data.model.ReaderSettings
import com.dyx.novelreader.data.model.ReaderTheme
import com.dyx.novelreader.data.model.ReaderWeight
import com.dyx.novelreader.data.model.RemoteProtocol
import com.dyx.novelreader.data.model.RemoteServer
import com.dyx.novelreader.data.model.ShelfSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "novel_reader")

/**
 * DataStore 持久化：远程服务器、书架、阅读进度、阅读排版设置。
 *
 * 全部走 JSON 字符串，避免 protobuf 结构变更导致升级丢数据。
 */
class PrefsStore(private val context: Context) {

    companion object {
        private val KEY_SERVERS = stringPreferencesKey("remote_servers")
        private val KEY_BOOKS = stringPreferencesKey("bookshelf")
        private val KEY_SORT = stringPreferencesKey("shelf_sort")
        private val KEY_FONT_SIZE = floatPreferencesKey("reader_font_size")
        private val KEY_LINE_HEIGHT = floatPreferencesKey("reader_line_height")
        private val KEY_PARA_SPACING = floatPreferencesKey("reader_para_spacing")
        private val KEY_PAGE_MODE = intPreferencesKey("reader_page_mode")
        private val KEY_THEME = stringPreferencesKey("reader_theme")
        private val KEY_KEEP_ON = intPreferencesKey("reader_keep_on")
        private val KEY_FONT = stringPreferencesKey("reader_font")
        private val KEY_WEIGHT = stringPreferencesKey("reader_weight")

        val DEFAULT_SETTINGS = ReaderSettings()
    }

    // ---------------- 服务器 ----------------

    val serversFlow: Flow<List<RemoteServer>> = context.dataStore.data
        .map { prefs -> prefs[KEY_SERVERS]?.let(::decodeServers) ?: emptyList() }

    suspend fun saveServer(server: RemoteServer) {
        val list = serversFlow.first().toMutableList()
        val idx = list.indexOfFirst { it.id == server.id }
        if (idx >= 0) list[idx] = server else list.add(server.copy(id = nextServerId(list)))
        context.dataStore.edit { it[KEY_SERVERS] = encodeServers(list) }
    }

    suspend fun deleteServer(id: Long) {
        val rest = serversFlow.first().filterNot { it.id == id }
        context.dataStore.edit { it[KEY_SERVERS] = encodeServers(rest) }
    }

    private fun nextServerId(list: List<RemoteServer>): Long =
        (list.maxOfOrNull { it.id } ?: 0L) + 1L

    // ---------------- 书架 ----------------

    val booksFlow: Flow<List<Book>> = context.dataStore.data
        .map { prefs -> prefs[KEY_BOOKS]?.let(::decodeBooks) ?: emptyList() }

    val sortFlow: Flow<ShelfSort> = context.dataStore.data.map { prefs ->
        val name = prefs[KEY_SORT] ?: ShelfSort.RECENT.name
        runCatching { ShelfSort.valueOf(name) }.getOrDefault(ShelfSort.RECENT)
    }

    suspend fun setSort(sort: ShelfSort) {
        context.dataStore.edit { it[KEY_SORT] = sort.name }
    }

    /** 新书加入书架；已存在则只更新标题/大小，不清掉已有进度 */
    suspend fun addToShelf(book: Book) {
        val list = booksFlow.first().toMutableList()
        val idx = list.indexOfFirst { it.id == book.id }
        if (idx >= 0) {
            val old = list[idx]
            list[idx] = old.copy(
                title = book.title,
                sizeBytes = book.sizeBytes,
                format = book.format,
                serverName = book.serverName,
                serverId = book.serverId,
            )
        } else {
            list.add(book)
        }
        writeBooks(list)
    }

    suspend fun deleteBook(id: String) {
        writeBooks(booksFlow.first().filterNot { it.id == id })
    }

    /** 批量移出书架 */
    suspend fun deleteBooks(ids: Set<String>) {
        if (ids.isEmpty()) return
        writeBooks(booksFlow.first().filterNot { it.id in ids })
    }

    /** 批量置顶 / 取消置顶 */
    suspend fun setPinned(ids: Set<String>, pinned: Boolean) {
        if (ids.isEmpty()) return
        writeBooks(booksFlow.first().map { if (it.id in ids) it.copy(pinned = pinned) else it })
    }

    suspend fun togglePin(id: String) {
        writeBooks(
            booksFlow.first().map {
                if (it.id == id) it.copy(pinned = !it.pinned) else it
            }
        )
    }

    suspend fun clearShelf() {
        writeBooks(emptyList())
    }

    /**
     * 更新阅读进度。
     * @param charOffset 全书字符偏移（唯一可信锚点）
     * @param progress   0f..1f
     */
    suspend fun updateProgress(
        id: String,
        charOffset: Int,
        progress: Float,
        chapterTitle: String,
        finished: Boolean = false,
    ) {
        val now = System.currentTimeMillis()
        writeBooks(
            booksFlow.first().map { book ->
                if (book.id != id) book
                else book.copy(
                    charOffset = charOffset.coerceAtLeast(0),
                    progress = progress.coerceIn(0f, 1f),
                    chapterTitle = chapterTitle,
                    finished = finished || finished(progress),
                    lastReadAt = now,
                )
            }
        )
    }

    /** 打开书籍即刷新最近阅读时间（即使没翻页） */
    suspend fun touchBook(id: String) {
        val now = System.currentTimeMillis()
        writeBooks(
            booksFlow.first().map { if (it.id == id) it.copy(lastReadAt = now) else it }
        )
    }

    private fun finished(progress: Float): Boolean = progress >= 0.995f

    private suspend fun writeBooks(list: List<Book>) {
        context.dataStore.edit { it[KEY_BOOKS] = encodeBooks(list) }
    }

    // ---------------- 阅读设置 ----------------

    val settingsFlow: Flow<ReaderSettings> = context.dataStore.data.map { prefs ->
        ReaderSettings(
            fontSize = prefs[KEY_FONT_SIZE] ?: DEFAULT_SETTINGS.fontSize,
            lineHeight = prefs[KEY_LINE_HEIGHT] ?: DEFAULT_SETTINGS.lineHeight,
            paragraphSpacing = prefs[KEY_PARA_SPACING] ?: DEFAULT_SETTINGS.paragraphSpacing,
            pageMode = (prefs[KEY_PAGE_MODE] ?: 1) == 1,
            readerTheme = runCatching {
                ReaderTheme.valueOf(prefs[KEY_THEME] ?: ReaderTheme.PAPER.name)
            }.getOrDefault(ReaderTheme.PAPER),
            keepScreenOn = (prefs[KEY_KEEP_ON] ?: 1) == 1,
            font = runCatching {
                ReaderFont.valueOf(prefs[KEY_FONT] ?: ReaderFont.SERIF.name)
            }.getOrDefault(ReaderFont.SERIF),
            weight = runCatching {
                ReaderWeight.valueOf(prefs[KEY_WEIGHT] ?: ReaderWeight.NORMAL.name)
            }.getOrDefault(ReaderWeight.NORMAL),
        )
    }

    suspend fun saveSettings(settings: ReaderSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FONT_SIZE] = settings.fontSize
            prefs[KEY_LINE_HEIGHT] = settings.lineHeight
            prefs[KEY_PARA_SPACING] = settings.paragraphSpacing
            prefs[KEY_PAGE_MODE] = if (settings.pageMode) 1 else 0
            prefs[KEY_THEME] = settings.readerTheme.name
            prefs[KEY_KEEP_ON] = if (settings.keepScreenOn) 1 else 0
            prefs[KEY_FONT] = settings.font.name
            prefs[KEY_WEIGHT] = settings.weight.name
        }
    }

    // ---------------- JSON ----------------

    private fun encodeServers(list: List<RemoteServer>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("protocol", it.protocol.name)
                    .put("host", it.host)
                    .put("share", it.share)
                    .put("domain", it.domain)
                    .put("username", it.username)
                    .put("password", it.password)
                    .put("anonymous", it.anonymous)
                    .put("secure", it.secure)
                    .put("insecureTls", it.insecureTls)
            )
        }
        return arr.toString()
    }

    private fun decodeServers(json: String): List<RemoteServer> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            RemoteServer(
                id = o.optLong("id"),
                name = o.optString("name"),
                protocol = runCatching {
                    RemoteProtocol.valueOf(o.optString("protocol", "SMB"))
                }.getOrDefault(RemoteProtocol.SMB),
                host = o.optString("host"),
                share = o.optString("share"),
                domain = o.optString("domain"),
                username = o.optString("username"),
                password = o.optString("password"),
                anonymous = o.optBoolean("anonymous"),
                secure = o.optBoolean("secure"),
                insecureTls = o.optBoolean("insecureTls"),
            )
        }
    }.getOrDefault(emptyList())

    private fun encodeBooks(list: List<Book>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("url", it.url)
                    .put("serverId", it.serverId)
                    .put("serverName", it.serverName)
                    .put("format", it.format.name)
                    .put("size", it.sizeBytes)
                    .put("addedAt", it.addedAt)
                    .put("lastReadAt", it.lastReadAt)
                    .put("charOffset", it.charOffset)
                    .put("progress", it.progress.toDouble())
                    .put("chapterTitle", it.chapterTitle)
                    .put("pinned", it.pinned)
                    .put("finished", it.finished)
            )
        }
        return arr.toString()
    }

    private fun decodeBooks(json: String): List<Book> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Book(
                id = o.optString("id"),
                title = o.optString("title"),
                url = o.optString("url"),
                serverId = o.optLong("serverId"),
                serverName = o.optString("serverName"),
                // 旧版本可能存成 UNKNOWN，这里按 URL 后缀自愈一次
                format = runCatching {
                    BookFormat.valueOf(o.optString("format", "TXT"))
                }.getOrDefault(BookFormat.UNKNOWN).let { stored ->
                    if (stored == BookFormat.UNKNOWN) RemoteFiles.formatOf(o.optString("url")) else stored
                },
                sizeBytes = o.optLong("size"),
                addedAt = o.optLong("addedAt"),
                lastReadAt = o.optLong("lastReadAt"),
                charOffset = o.optInt("charOffset"),
                progress = o.optDouble("progress", 0.0).toFloat(),
                chapterTitle = o.optString("chapterTitle"),
                pinned = o.optBoolean("pinned"),
                finished = o.optBoolean("finished"),
            )
        }
    }.getOrDefault(emptyList())
}
