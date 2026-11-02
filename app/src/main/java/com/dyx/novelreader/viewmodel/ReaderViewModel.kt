package com.dyx.novelreader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dyx.novelreader.data.PrefsStore
import com.dyx.novelreader.data.RemoteFiles
import com.dyx.novelreader.data.books.BookDocument
import com.dyx.novelreader.data.books.DocumentLoader
import com.dyx.novelreader.data.model.Book
import com.dyx.novelreader.data.model.ReaderSettings
import com.dyx.novelreader.data.model.RemoteServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 阅读器：加载远端文档、维护阅读进度、保存排版设置。
 *
 * 进度的权威表示是「全书字符偏移」，UI 负责把它换算成页码/滚动位置。
 */
class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PrefsStore(app)

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val document: BookDocument? = null,
        val book: Book? = null,
        /** 从书架带入的起始偏移 */
        val initialOffset: Int = 0,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val settings: StateFlow<ReaderSettings> = prefs.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PrefsStore.DEFAULT_SETTINGS)

    private var currentBook: Book? = null
    private var currentServer: RemoteServer? = null
    private var saveJob: Job? = null

    /** 打开一本书：加入书架、加载内容、定位到上次位置 */
    fun open(book: Book, server: RemoteServer) {
        currentBook = book
        currentServer = server
        _state.value = UiState(loading = true, book = book, initialOffset = book.charOffset)

        viewModelScope.launch {
            // 打开即入库（幂等）：保证后面写进度时书架里一定有这条记录
            prefs.addToShelf(book)
            prefs.touchBook(book.id)
            withContext(Dispatchers.IO) {
                try {
                    val entry = com.dyx.novelreader.data.model.RemoteEntry(
                        name = book.url.substringAfterLast('/'),
                        displayName = book.title,
                        isDir = false,
                        sizeBytes = book.sizeBytes,
                        url = book.url,
                        kind = com.dyx.novelreader.data.model.FileKind.BOOK,
                        format = book.format,
                    )
                    val doc = DocumentLoader.load(getApplication(), server, entry)
                    // PDF 的 charOffset 存的是页号
                    val offset = if (doc.isPdf) {
                        book.charOffset.coerceIn(0, doc.pageCount - 1)
                    } else {
                        book.charOffset.coerceIn(0, maxOf(doc.length - 1, 0))
                    }
                    withContext(Dispatchers.Main) {
                        _state.value = UiState(
                            loading = false,
                            document = doc,
                            book = book,
                            initialOffset = offset,
                        )
                    }
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
                        _state.value = UiState(
                            loading = false,
                            error = e.message ?: e.javaClass.simpleName,
                            book = book,
                        )
                    }
                }
            }
        }
    }

    fun retry() {
        val book = currentBook ?: return
        val server = currentServer ?: return
        open(book, server)
    }

    /**
     * 保存阅读进度（连续翻页时防抖 500ms，只落最后一次）。
     * @param offset 全书字符偏移；PDF 传页号
     */
    fun saveProgress(offset: Int, chapterTitle: String, finished: Boolean = false) {
        val book = currentBook ?: return
        val doc = _state.value.document
        val progress = when {
            doc == null -> 0f
            doc.isPdf -> (offset + 1).toFloat() / doc.pageCount
            doc.length > 0 -> offset.toFloat() / doc.length
            else -> 0f
        }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            prefs.updateProgress(
                id = book.id,
                charOffset = offset,
                progress = progress,
                chapterTitle = chapterTitle,
                finished = finished,
            )
        }
    }

    fun updateSettings(newSettings: ReaderSettings) {
        viewModelScope.launch { prefs.saveSettings(newSettings) }
    }

    override fun onCleared() {
        super.onCleared()
        currentServer?.let { RemoteFiles.releaseAll() }
        saveJob?.cancel()
    }
}
