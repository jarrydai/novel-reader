package com.dyx.novelreader.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dyx.novelreader.data.model.Book
import com.dyx.novelreader.data.model.RemoteServer
import com.dyx.novelreader.ui.browser.BrowserScreen
import com.dyx.novelreader.ui.reader.ReaderScreen
import com.dyx.novelreader.ui.shelf.ShelfScreen

private data class ReadingSession(val book: Book, val server: RemoteServer)

/**
 * 应用根节点：书架 → 书库浏览 → 阅读页。
 *
 * 刻意不使用 navigation-compose：层级只有三层，
 * 状态切换即可，还能保证返回时书架滚动位置与目录位置不丢。
 */
@Composable
fun AppRoot() {
    var browsing by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<ReadingSession?>(null) }

    Box(Modifier.fillMaxSize()) {
        ShelfScreen(
            onBrowse = { browsing = true },
            onManageServers = { browsing = true },
            onOpenBook = { book, server -> session = ReadingSession(book, server) },
        )

        if (browsing) {
            BrowserScreen(
                onOpenEntry = { entry, server ->
                    browsing = false
                    session = ReadingSession(
                        book = Book(
                            id = Book.idOf(server.key, entry.url),
                            title = entry.displayName.substringBeforeLast('.').ifBlank { entry.displayName },
                            url = entry.url,
                            serverId = server.id,
                            serverName = server.name,
                            format = entry.format,
                            sizeBytes = entry.sizeBytes,
                            addedAt = System.currentTimeMillis(),
                            lastReadAt = System.currentTimeMillis(),
                        ),
                        server = server,
                    )
                },
                onExit = { browsing = false },
            )
        }

        val current = session
        if (current != null) {
            key(current.book.id) {
                ReaderScreen(
                    book = current.book,
                    server = current.server,
                    onBack = { session = null },
                )
            }
        }
    }
}
