package com.dyx.novelreader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dyx.novelreader.data.PrefsStore
import com.dyx.novelreader.data.model.Book
import com.dyx.novelreader.data.model.RemoteEntry
import com.dyx.novelreader.data.model.RemoteServer
import com.dyx.novelreader.data.model.ShelfSort
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

/**
 * 书架：书籍集合、排序、置顶、删除。
 *
 * 书架的每一本书都记录所属服务器（serverId），
 * 打开前用它找回连接参数；服务器被删掉时书架条目仍保留，只是打开会提示。
 */
class ShelfViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PrefsStore(app)

    val servers: StateFlow<List<RemoteServer>> = prefs.serversFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sort: StateFlow<ShelfSort> = prefs.sortFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShelfSort.RECENT)

    /** 已排序的书架列表 */
    val books: StateFlow<List<Book>> =
        combine(prefs.booksFlow, prefs.sortFlow) { list, sortMode -> sortBooks(list, sortMode) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSort(mode: ShelfSort) {
        viewModelScope.launch { prefs.setSort(mode) }
    }

    /** 打开一本书时把它加入/更新到书架（阅读过的自动进书架） */
    fun addToShelf(server: RemoteServer, entry: RemoteEntry) {
        val now = System.currentTimeMillis()
        val book = Book(
            id = Book.idOf(server.key, entry.url),
            title = entry.displayName.substringBeforeLast('.').ifBlank { entry.displayName },
            url = entry.url,
            serverId = server.id,
            serverName = server.name,
            format = entry.format,
            sizeBytes = entry.sizeBytes,
            addedAt = now,
            lastReadAt = now,
        )
        viewModelScope.launch { prefs.addToShelf(book) }
    }

    fun deleteBook(id: String) {
        viewModelScope.launch { prefs.deleteBook(id) }
    }

    /** 批量移出书架（不动远端文件） */
    fun deleteBooks(ids: Set<String>) {
        viewModelScope.launch { prefs.deleteBooks(ids) }
    }

    /** 批量置顶 / 取消置顶 */
    fun setPinned(ids: Set<String>, pinned: Boolean) {
        viewModelScope.launch { prefs.setPinned(ids, pinned) }
    }

    fun togglePin(id: String) {
        viewModelScope.launch { prefs.togglePin(id) }
    }

    fun clearShelf() {
        viewModelScope.launch { prefs.clearShelf() }
    }

    /** 用书架上记录的 serverId 找回服务器配置 */
    fun serverFor(book: Book): RemoteServer? =
        servers.value.firstOrNull { it.id == book.serverId }

    private fun sortBooks(list: List<Book>, mode: ShelfSort): List<Book> {
        val collator = Collator.getInstance(Locale.CHINA)
        val byTitle = Comparator<Book> { a, b -> collator.compare(a.title, b.title) }
        val sorted = when (mode) {
            ShelfSort.RECENT -> list.sortedWith(
                compareByDescending<Book> { it.lastReadAt }.thenByDescending { it.addedAt }
            )

            ShelfSort.ADDED -> list.sortedByDescending { it.addedAt }
            ShelfSort.TITLE -> list.sortedWith(byTitle)
            ShelfSort.PROGRESS -> list.sortedWith(
                compareByDescending<Book> { it.progress }.thenByDescending { it.lastReadAt }
            )
        }
        // 置顶的永远排在最前，内部保持当前排序
        return sorted.sortedByDescending { it.pinned }
    }
}
