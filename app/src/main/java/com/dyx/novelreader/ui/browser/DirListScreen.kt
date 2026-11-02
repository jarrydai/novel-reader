package com.dyx.novelreader.ui.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dyx.novelreader.data.RemoteFiles
import com.dyx.novelreader.data.model.BookFormat
import com.dyx.novelreader.data.model.FileKind
import com.dyx.novelreader.data.model.RemoteEntry
import com.dyx.novelreader.data.model.RemoteServer
import com.dyx.novelreader.ui.common.EmptyState
import com.dyx.novelreader.ui.common.ErrorState
import com.dyx.novelreader.ui.common.IconTile
import com.dyx.novelreader.ui.common.LoadingState
import com.dyx.novelreader.ui.common.formatSize
import com.dyx.novelreader.ui.common.headerBrush

/**
 * 目录浏览：只展示文件夹与可阅读的文档，点书直接进阅读页。
 */
@Composable
fun DirListScreen(
    server: RemoteServer,
    path: String,
    entries: List<RemoteEntry>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onOpenDir: (String) -> Unit,
    onOpenEntry: (RemoteEntry) -> Unit,
    onExitToList: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        DirHeader(server = server, path = path, onBack = onBack, onExitToList = onExitToList)

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingState("正在读取目录…")
            }

            error != null -> ErrorState(
                title = "无法打开这个目录",
                message = error,
                onRetry = onRetry,
            )

            entries.isEmpty() -> EmptyState(
                icon = Icons.Rounded.FolderOpen,
                title = "这个文件夹是空的",
                message = "没有找到可阅读的文档（txt / epub / pdf / docx / xlsx / pptx 等）。",
            )

            else -> {
                val folders = entries.count { it.isDir }
                val books = entries.count { !it.isDir }
                Text(
                    buildString {
                        val parts = mutableListOf<String>()
                        if (folders > 0) parts.add("$folders 个文件夹")
                        if (books > 0) parts.add("$books 本书")
                        append(parts.ifEmpty { listOf("空目录") }.joinToString(" · "))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 22.dp, top = 14.dp),
                )
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(entries, key = { it.url }) { entry ->
                        EntryRow(
                            entry = entry,
                            onClick = { if (entry.isDir) onOpenDir(entry.url) else onOpenEntry(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DirHeader(
    server: RemoteServer,
    path: String,
    onBack: () -> Unit,
    onExitToList: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(headerBrush())
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "上一级",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    server.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    RemoteFiles.prettyPath(server, path),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                onClick = onExitToList,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Dns,
                        contentDescription = "服务器列表",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(entry: RemoteEntry, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (entry.isDir) {
                IconTile(
                    icon = Icons.Rounded.Folder,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    size = 44.dp,
                    iconSize = 22.dp,
                    shape = RoundedCornerShape(14.dp),
                )
            } else {
                val (container, tint) = formatColors(entry.format)
                IconTile(
                    icon = Icons.Rounded.Book,
                    container = container,
                    tint = tint,
                    size = 44.dp,
                    iconSize = 22.dp,
                    shape = RoundedCornerShape(14.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        if (entry.isDir) append("文件夹") else append(entry.format.label)
                        formatSize(entry.sizeBytes).takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun formatColors(format: BookFormat): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> =
    when (format) {
        BookFormat.TXT, BookFormat.HTML, BookFormat.RTF ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant

        BookFormat.EPUB ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer

        BookFormat.PDF ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer

        else ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
