package com.dyx.novelreader.ui.shelf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dyx.novelreader.data.model.Book
import com.dyx.novelreader.data.model.BookFormat
import com.dyx.novelreader.data.model.RemoteServer
import com.dyx.novelreader.data.model.ShelfSort
import com.dyx.novelreader.ui.common.EmptyState
import com.dyx.novelreader.ui.common.headerBrush
import com.dyx.novelreader.viewmodel.ShelfViewModel
import kotlin.math.roundToInt

/**
 * 书架首页：所有读过的书都在这里。
 *
 * 排序、置顶、删除都作用在书架条目上，不会动远端文件。
 * 长按任意一本书或多选按钮进入批量模式，可一次移出/置顶多本。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShelfScreen(
    onBrowse: () -> Unit,
    onManageServers: () -> Unit,
    onOpenBook: (Book, RemoteServer) -> Unit,
) {
    val vm: ShelfViewModel = viewModel()
    val books by vm.books.collectAsState()
    val sort by vm.sort.collectAsState()
    val servers by vm.servers.collectAsState()

    var sortMenu by remember { mutableStateOf(false) }
    var missingServer by remember { mutableStateOf<Book?>(null) }

    // 批量模式
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmBatchDelete by remember { mutableStateOf(false) }

    // 书本被移除后，清掉选中集合里的失效 id
    LaunchedEffect(books) {
        if (selected.isNotEmpty()) {
            val alive = books.map { it.id }.toSet()
            if (!alive.containsAll(selected)) selected = selected.intersect(alive)
        }
        if (books.isEmpty() && selecting) selecting = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!selecting) {
                ExtendedFloatingActionButton(
                    onClick = onBrowse,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("添加书籍") },
                    shape = RoundedCornerShape(18.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                if (selecting) {
                    SelectionHeader(
                        count = selected.size,
                        total = books.size,
                        onExit = { selecting = false },
                    )
                } else {
                    ShelfHeader(
                        subtitle = if (books.isEmpty()) "阅读过的书会自动收进来"
                        else "${books.size} 本 · 按${sort.label}排序",
                        sort = sort,
                        sortMenu = sortMenu,
                        onSortMenu = { sortMenu = it },
                        onPickSort = { vm.setSort(it) },
                        onSelect = { selecting = true },
                        onManageServers = onManageServers,
                        selectEnabled = books.isNotEmpty(),
                    )
                }

                if (books.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().navigationBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        EmptyState(
                            icon = Icons.Rounded.Book,
                            title = "书架还是空的",
                            message = "点下面的「添加书籍」，连接 SMB 或 WebDAV，选一本小说开始读。\n读过的书会自动出现在这里，并记住你读到哪里。",
                            primaryLabel = if (servers.isEmpty()) "添加服务器" else "浏览书库",
                            onPrimary = onBrowse,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 10.dp,
                            bottom = if (selecting) 140.dp else 100.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(books, key = { it.id }) { book ->
                            BookCard(
                                book = book,
                                selecting = selecting,
                                selected = book.id in selected,
                                onClick = {
                                    if (selecting) {
                                        selected = selected.toggle(book.id)
                                    } else {
                                        val server = servers.firstOrNull { it.id == book.serverId }
                                        if (server == null) missingServer = book else onOpenBook(book, server)
                                    }
                                },
                                onLongClick = {
                                    if (selecting) selected = selected.toggle(book.id)
                                    else {
                                        selecting = true
                                        selected = setOf(book.id)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // 批量操作条
            AnimatedVisibility(
                visible = selecting,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                BatchBar(
                    hasSelection = selected.isNotEmpty(),
                    allSelected = selected.size == books.size,
                    onSelectAll = {
                        selected = if (selected.size == books.size) emptySet() else books.map { it.id }.toSet()
                    },
                    onPin = {
                        vm.setPinned(selected, true)
                        selecting = false
                    },
                    onDelete = { confirmBatchDelete = true },
                )
            }
        }
    }

    // 批量移出确认
    if (confirmBatchDelete) {
        AlertDialog(
            onDismissRequest = { confirmBatchDelete = false },
            title = { Text("移出 ${selected.size} 本书？") },
            text = { Text("只会从书架移除，服务器上的文件不会被删除。阅读进度也会一并清除。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteBooks(selected)
                    confirmBatchDelete = false
                    selecting = false
                }) { Text("全部移出", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmBatchDelete = false }) { Text("取消") }
            },
        )
    }

    missingServer?.let { book ->
        AlertDialog(
            onDismissRequest = { missingServer = null },
            title = { Text("找不到服务器") },
            text = {
                Text("《${book.title}》所在的服务器「${book.serverName}」已被删除。\n重新添加该服务器后即可继续阅读。")
            },
            confirmButton = {
                TextButton(onClick = {
                    missingServer = null
                    onManageServers()
                }) { Text("去添加") }
            },
            dismissButton = {
                TextButton(onClick = { missingServer = null }) { Text("关闭") }
            },
        )
    }
}

/** 普通顶栏：标题 + 排序 / 服务器 / 多选 */
@Composable
private fun ShelfHeader(
    subtitle: String,
    sort: ShelfSort,
    sortMenu: Boolean,
    onSortMenu: (Boolean) -> Unit,
    onPickSort: (ShelfSort) -> Unit,
    onSelect: () -> Unit,
    onManageServers: () -> Unit,
    selectEnabled: Boolean,
) {
    Box(Modifier.fillMaxWidth().background(headerBrush()).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("我的书架", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box {
                RoundIconButton(Icons.Rounded.Sort, "排序") { onSortMenu(true) }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { onSortMenu(false) }) {
                    ShelfSort.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            trailingIcon = {
                                if (mode == sort) Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(17.dp),
                                )
                            },
                            onClick = {
                                onPickSort(mode)
                                onSortMenu(false)
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.width(6.dp))
            RoundIconButton(Icons.Rounded.Checklist, "批量管理", enabled = selectEnabled, onClick = onSelect)
            Spacer(Modifier.width(6.dp))
            RoundIconButton(Icons.Rounded.Dns, "服务器", onClick = onManageServers)
        }
    }
}

/** 多选顶栏：退出 / 已选数量 */
@Composable
private fun SelectionHeader(
    count: Int,
    total: Int,
    onExit: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().background(headerBrush()).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onExit) {
                Icon(Icons.Rounded.Close, contentDescription = "退出批量管理",
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (count == 0) "选择书籍" else "已选 $count 本",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "共 $total 本 · 长按或点击选择",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 批量操作条：全选 / 置顶 / 移出书架 */
@Composable
private fun BatchBar(
    hasSelection: Boolean,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            BatchAction(
                icon = Icons.Rounded.SelectAll,
                label = if (allSelected) "取消全选" else "全选",
                enabled = true,
                onClick = onSelectAll,
            )
            BatchAction(
                icon = Icons.Rounded.PushPin,
                label = "置顶",
                enabled = hasSelection,
                onClick = onPin,
            )
            BatchAction(
                icon = Icons.Rounded.DeleteOutline,
                label = "移出书架",
                enabled = hasSelection,
                tint = MaterialTheme.colorScheme.error,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun BatchAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    val color = if (enabled) tint else tint.copy(alpha = 0.35f)
    TextButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        enabled = enabled,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = description,
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.35f),
            )
        }
    }
}

/**
 * 书架卡片：做成一本竖立的书——左侧书脊、右缘纸张厚度、封面上的书名，
 * 读起来像真正的书架，而不是一排色块。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(
    book: Book,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = RoundedCornerShape(topStart = 3.dp, topEnd = 7.dp, bottomEnd = 7.dp, bottomStart = 3.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(3.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        BookCover(book)

        // 多选态下的遮罩与勾选标记
        if (selecting) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else Color.White.copy(alpha = 0.02f)
                    )
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(16.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.38f),
                        CircleShape,
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = "已选中",
                        tint = Color.White,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        } else if (book.pinned) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(16.dp)
                    .background(Color.Black.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = "已置顶",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(9.dp),
                )
            }
        }
    }
}

/** 封面：布面底色 + 书脊 + 书页厚度 + 书名 */
@Composable
private fun BookCover(book: Book) {
    Box(Modifier.fillMaxWidth().aspectRatio(0.74f)) {
        // 布面底色
        Box(Modifier.matchParentSize().background(coverBrush(book.format)))
        // 顶光与底部压暗，制造装帧的起伏
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.12f), Color.Transparent, Color.Black.copy(alpha = 0.14f))
                )
            )
        )
        // 书脊：凸起的圆柱面——两侧压暗、中间一道高光
        Box(
            Modifier
                .fillMaxHeight()
                .width(7.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.40f),
                            Color.White.copy(alpha = 0.17f),
                            Color.Black.copy(alpha = 0.36f),
                        )
                    )
                )
        )
        // 书脊与封面交界处的折缝
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = 7.dp)
                .fillMaxHeight()
                .width(1.dp)
                .background(Color.Black.copy(alpha = 0.22f))
        )
        // 书页厚度：右缘露出的纸页侧面
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(3.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.24f),
                            Color(0xFFF3EFE7).copy(alpha = 0.62f),
                        )
                    )
                )
        )
        // 页与页之间的细缝
        listOf(1.3f, 2.3f).forEach { offsetDp ->
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-offsetDp).dp)
                    .fillMaxHeight()
                    .width(0.5.dp)
                    .background(Color.Black.copy(alpha = 0.09f))
            )
        }

        // 封面内容
        Column(
            Modifier
                .matchParentSize()
                .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 20.dp)
        ) {
            Box(Modifier.width(10.dp).height(1.dp).background(Color(0xFFE3B67A).copy(alpha = 0.9f)))
            Spacer(Modifier.height(6.dp))
            Text(
                book.title,
                color = Color.White.copy(alpha = 0.95f),
                fontFamily = FontFamily.Serif,
                fontSize = 10.sp,
                lineHeight = 13.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
        }

        // 阅读进度压在封面下沿：格式在左、百分比在右，底边一道金色刻度线
        val ratio = if (book.finished) 1f else book.progress.coerceIn(0f, 1f)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(20.dp)
        ) {
            if (ratio > 0f) {
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.66f))
                        )
                    )
                )
            }
            Row(
                Modifier
                    .matchParentSize()
                    .padding(start = 10.dp, end = 6.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    book.format.label,
                    fontSize = 6.sp,
                    color = Color.White.copy(alpha = if (ratio > 0f) 0.72f else 0.82f),
                    letterSpacing = 0.4.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                if (ratio > 0f) {
                    Text(
                        if (book.finished) "读完" else "${(ratio * 100).roundToInt()}%",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.96f),
                        letterSpacing = 0.2.sp,
                        maxLines = 1,
                    )
                }
            }
        }
        if (ratio > 0f) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(ratio)
                        .fillMaxHeight()
                        .background(Color(0xFFE3B67A))
                )
            }
        }
    }
}

/** 布面装帧色：每个格式一种书脊颜色，整体压暗以衬托白色书名 */
private fun coverBrush(format: BookFormat): Brush {
    val (top, bottom) = when (format) {
        BookFormat.TXT -> Color(0xFF3E5C8A) to Color(0xFF2A4166)
        BookFormat.EPUB -> Color(0xFF2E6B57) to Color(0xFF1F4C3E)
        BookFormat.PDF -> Color(0xFF9E4638) to Color(0xFF743024)
        BookFormat.DOCX, BookFormat.DOC -> Color(0xFF2F5A9E) to Color(0xFF22417A)
        BookFormat.XLSX, BookFormat.XLS -> Color(0xFF2E6B45) to Color(0xFF204F33)
        BookFormat.PPTX, BookFormat.PPT -> Color(0xFF9C5528) to Color(0xFF743D1B)
        BookFormat.HTML -> Color(0xFF63479A) to Color(0xFF483274)
        else -> Color(0xFF4A5766) to Color(0xFF353F4B)
    }
    return Brush.linearGradient(listOf(top, bottom))
}

private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id
