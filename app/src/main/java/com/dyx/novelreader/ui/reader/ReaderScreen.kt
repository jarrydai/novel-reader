package com.dyx.novelreader.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dyx.novelreader.data.books.BookDocument
import com.dyx.novelreader.data.model.Book
import com.dyx.novelreader.data.model.ReaderSettings
import com.dyx.novelreader.data.model.ReaderTheme
import com.dyx.novelreader.data.model.RemoteServer
import com.dyx.novelreader.viewmodel.ReaderViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

/** 阅读界面配色：纸白 / 米黄 / 护眼绿 / 夜间 */
data class ReaderColors(
    val background: Color,
    val text: Color,
    val dim: Color,
    val bar: Color,
)

fun readerColors(theme: ReaderTheme): ReaderColors = when (theme) {
    ReaderTheme.PAPER -> ReaderColors(Color(0xFFF8F6F1), Color(0xFF23211E), Color(0xFF8A857C), Color(0xFFF1EDE5))
    ReaderTheme.SEPIA -> ReaderColors(Color(0xFFF3E7CE), Color(0xFF4E3B26), Color(0xFF9A8463), Color(0xFFEADCBF))
    ReaderTheme.GREEN -> ReaderColors(Color(0xFFDDE9D6), Color(0xFF2A3627), Color(0xFF6E7F66), Color(0xFFD0DFC7))
    ReaderTheme.NIGHT -> ReaderColors(Color(0xFF16171A), Color(0xFFB6B7BC), Color(0xFF6B6D73), Color(0xFF1E2024))
}

/**
 * 阅读页。
 *
 * 两种阅读方式共用同一个「全书字符偏移」锚点：
 *  - 翻页模式：按可用区域实测分页，左右 1/3 点击翻页，中间点击呼出工具栏
 *  - 滚动模式：整章连续滚动，滚动时回写可见段落偏移
 * 切换模式、改字号后阅读位置保持不变。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    book: Book,
    server: RemoteServer,
    onBack: () -> Unit,
) {
    val vm: ReaderViewModel = viewModel()
    val state by vm.state.collectAsState()
    val settings by vm.settings.collectAsState()

    LaunchedEffect(book.id) { vm.open(book, server) }


    val colors = readerColors(settings.readerTheme)
    var showChapters by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var jumpTo by remember { mutableStateOf<Int?>(null) }

    // 返回键：先关面板，再退出阅读
    BackHandler(enabled = showChapters || showSettings) {
        showChapters = false
        showSettings = false
    }
    BackHandler(enabled = !showChapters && !showSettings) { onBack() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 阅读时保持屏幕常亮
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(containerColor = colors.background, contentWindowInsets = WindowInsets(0)) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "正在打开《${book.title}》…",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.dim,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }

                state.error != null -> ReaderError(
                    message = state.error ?: "未知错误",
                    colors = colors,
                    onRetry = { vm.retry() },
                    onBack = onBack,
                )

                state.document != null -> {
                    val doc = state.document!!
                    if (doc.isPdf) {
                        PdfReader(
                            document = doc,
                            colors = colors,
                            initialPage = state.initialOffset,
                            onBack = onBack,
                            onOpenChapters = { showChapters = true },
                            onOpenSettings = { showSettings = true },
                            onProgress = { page, title, finished -> vm.saveProgress(page, title, finished) },
                            jumpTo = jumpTo,
                            onJumpHandled = { jumpTo = null },
                        )
                    } else {
                        TextReader(
                            document = doc,
                            settings = settings,
                            colors = colors,
                            initialOffset = state.initialOffset,
                            jumpTo = jumpTo,
                            onJumpHandled = { jumpTo = null },
                            onBack = onBack,
                            onOpenChapters = { showChapters = true },
                            onOpenSettings = { showSettings = true },
                            onProgress = { offset, title, finished -> vm.saveProgress(offset, title, finished) },
                        )
                    }
                }
            }
        }
    }

    if (showChapters && state.document != null) {
        ModalBottomSheet(
            onDismissRequest = { showChapters = false },
            sheetState = sheetState,
            containerColor = colors.bar,
        ) {
            ChapterListSheet(
                document = state.document!!,
                currentOffset = state.initialOffset,
                colors = colors,
                onPick = { offset ->
                    showChapters = false
                    jumpTo = offset
                },
            )
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = sheetState,
            containerColor = colors.bar,
        ) {
            ReaderSettingsSheet(
                settings = settings,
                colors = colors,
                onChange = { vm.updateSettings(it) },
            )
        }
    }
}

@Composable
private fun ReaderError(
    message: String,
    colors: ReaderColors,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(38.dp),
        )
        Text(
            "无法打开这本书",
            style = MaterialTheme.typography.titleMedium,
            color = colors.text,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = colors.dim,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(Modifier.padding(top = 20.dp)) {
            TextButton(onClick = onBack) { Text("返回") }
            Spacer(Modifier.width(10.dp))
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}

/** 纯文本阅读：翻页 / 滚动 */
@Composable
private fun TextReader(
    document: BookDocument,
    settings: ReaderSettings,
    colors: ReaderColors,
    initialOffset: Int,
    jumpTo: Int?,
    onJumpHandled: () -> Unit,
    onBack: () -> Unit,
    onOpenChapters: () -> Unit,
    onOpenSettings: () -> Unit,
    onProgress: (Int, String, Boolean) -> Unit,
) {
    var barsVisible by remember { mutableStateOf(true) }
    var chapterIndex by remember(document) { mutableIntStateOf(document.chapterIndexAt(initialOffset)) }
    var pageIndex by remember(document) { mutableIntStateOf(0) }
    var currentOffset by remember(document) { mutableIntStateOf(initialOffset) }
    /** 滚动模式当前所在章：跟着可见段落走，与分页用的 chapterIndex 分开，避免重建列表 */
    var scrollChapter by remember(document) { mutableIntStateOf(document.chapterIndexAt(initialOffset)) }
    /** 目录跳转时递增，用来把滚动列表整体重建到目标位置 */
    var scrollVersion by remember(document) { mutableIntStateOf(0) }
    /** 需要定位到的偏移；分页完成后消费掉 */
    var pendingOffset by remember(document) { mutableStateOf<Int?>(initialOffset) }
    /** 滚动到过末尾就一直算读完，别被后续回滚的进度覆盖 */
    var finishedOnce by remember(document) { mutableStateOf(false) }

    val textStyle = remember(settings, colors) {
        TextStyle(
            fontSize = settings.fontSize.sp,
            lineHeight = (settings.fontSize * settings.lineHeight).sp,
            color = colors.text,
            fontFamily = settings.font.family(),
            fontWeight = settings.weight.composeWeight,
        )
    }
    val measurer = rememberTextMeasurer()

    // 目录跳转
    LaunchedEffect(jumpTo) {
        val target = jumpTo ?: return@LaunchedEffect
        val idx = document.chapterIndexAt(target)
        chapterIndex = idx
        scrollChapter = idx
        currentOffset = target
        pendingOffset = target
        if (!settings.pageMode) scrollVersion++
        onJumpHandled()
    }

    // 从滚动切回翻页：接着当前阅读位置分页
    LaunchedEffect(settings.pageMode) {
        if (settings.pageMode) {
            chapterIndex = scrollChapter
            pendingOffset = currentOffset
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val hPad = 22.dp
        val vPad = 30.dp
        val contentWidthPx = with(density) { (maxWidth - hPad * 2).roundToPx() }
        val contentHeightPx = with(density) { (maxHeight - vPad * 2).roundToPx() }

        val chapter = document.chapters.getOrNull(chapterIndex)

        // 分页只在翻页模式下做，滚动模式不需要（省掉整章排版的开销）
        val pages = if (settings.pageMode) {
            remember(chapterIndex, settings.fontSize, settings.lineHeight, settings.font, settings.weight, contentWidthPx, contentHeightPx, document) {
                if (chapter == null) emptyList()
                else Paginator(measurer, textStyle, contentWidthPx, contentHeightPx)
                    .paginate(document.text, chapter.start, chapter.end)
            }
        } else {
            emptyList()
        }

        // 分页就绪后统一处理定位（首次进入 / 跳章 / 切回翻页）
        LaunchedEffect(pages, settings.pageMode) {
            if (!settings.pageMode || pages.isEmpty()) return@LaunchedEffect
            val target = pendingOffset
            val idx = if (target != null) {
                pages.indexOfFirst { target in it.first..it.last }.let { if (it >= 0) it else 0 }
            } else {
                pageIndex.coerceIn(0, pages.lastIndex)
            }
            pageIndex = idx
            currentOffset = pages[idx].first
            pendingOffset = null
        }

        fun goNext() {
            if (pageIndex < pages.size - 1) {
                pageIndex++
                val range = pages[pageIndex]
                currentOffset = range.first
                onProgress(range.first, chapter?.title.orEmpty(), false)
            } else {
                val nextIdx = chapterIndex + 1
                if (nextIdx < document.chapters.size) {
                    val next = document.chapters[nextIdx]
                    chapterIndex = nextIdx
                    pendingOffset = next.start
                    currentOffset = next.start
                    onProgress(next.start, next.title, nextIdx == document.chapters.lastIndex)
                }
            }
        }

        fun goPrev() {
            if (pageIndex > 0) {
                pageIndex--
                val range = pages[pageIndex]
                currentOffset = range.first
                onProgress(range.first, chapter?.title.orEmpty(), false)
            } else if (chapterIndex > 0) {
                val prevIdx = chapterIndex - 1
                val prev = document.chapters[prevIdx]
                chapterIndex = prevIdx
                // 定位到上一章末尾
                pendingOffset = (prev.end - 1).coerceAtLeast(prev.start)
                currentOffset = pendingOffset!!
                onProgress(prev.start, prev.title, false)
            }
        }

        Column(Modifier.fillMaxSize()) {
            val shownTitle = if (settings.pageMode) chapter?.title.orEmpty()
            else document.chapters.getOrNull(scrollChapter)?.title.orEmpty()

            ReaderTopBar(
                title = document.title,
                subtitle = shownTitle,
                visible = barsVisible,
                colors = colors,
                onBack = onBack,
                onChapters = onOpenChapters,
                onSettings = onOpenSettings,
            )

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = hPad, vertical = vPad)
            ) {
                if (settings.pageMode) {
                    val range = pages.getOrNull(pageIndex)
                    val body = if (range == null) "" else
                        document.text.substring(range.first, (range.last + 1).coerceAtMost(document.text.length))
                    Text(text = body, style = textStyle, modifier = Modifier.fillMaxSize())

                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(pages, chapterIndex) {
                                var dragged = 0f
                                var fired = false
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: continue
                                        when (event.type) {
                                            PointerEventType.Press -> {
                                                dragged = 0f
                                                fired = false
                                            }

                                            PointerEventType.Move -> {
                                                dragged += change.positionChange().x
                                                if (!fired && abs(dragged) > 70f) {
                                                    fired = true
                                                    if (dragged < 0) goNext() else goPrev()
                                                }
                                            }

                                            PointerEventType.Release -> {
                                                if (!fired) {
                                                    val x = change.position.x
                                                    when {
                                                        x < size.width * 0.32f -> goPrev()
                                                        x > size.width * 0.68f -> goNext()
                                                        else -> barsVisible = !barsVisible
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                    )
                } else {
                    // 换章/跳转通过 version 重建列表，内部滚动不重建
                    key(scrollVersion) {
                        ScrollContent(
                            document = document,
                            initialChapter = scrollChapter,
                            initialOffset = currentOffset,
                            textStyle = textStyle,
                            paragraphSpacing = settings.paragraphSpacing,
                            onOffsetChange = { offset ->
                                currentOffset = offset
                                val ci = document.chapterIndexAt(offset)
                                onProgress(
                                    offset,
                                    document.chapters.getOrNull(ci)?.title.orEmpty(),
                                    finishedOnce,
                                )
                            },
                            onChapterChange = { idx ->
                                if (idx != scrollChapter) scrollChapter = idx
                            },
                            onReachEnd = {
                                finishedOnce = true
                                onProgress(
                                    document.length,
                                    document.chapters.lastOrNull()?.title.orEmpty(),
                                    true,
                                )
                            },
                        )
                    }
                }
            }

            ReaderBottomBar(
                visible = barsVisible || !settings.pageMode,
                page = if (settings.pageMode) pageIndex + 1 else 0,
                pageCount = if (settings.pageMode) pages.size else 0,
                progress = if (document.length > 0) currentOffset.toFloat() / document.length else 0f,
                chapterTitle = shownTitle,
                colors = colors,
            )
        }
    }
}

/**
 * 滚动模式：整章连续滚动，按可见段落回写偏移。
 *
 * 列表按「窗口」加载——初始是当前章 + 下一章，滚到末尾自动追加下一章，
 * 滚到顶部自动补上一章，所以能像翻页模式一样一路读到底，不会卡在章末。
 * 补章时用字符偏移把阅读位置钉回原处，视觉上不会跳动。
 */
@Composable
private fun ScrollContent(
    document: BookDocument,
    initialChapter: Int,
    initialOffset: Int,
    textStyle: TextStyle,
    paragraphSpacing: Float,
    onOffsetChange: (Int) -> Unit,
    onChapterChange: (Int) -> Unit,
    onReachEnd: () -> Unit,
) {
    val lastChapter = document.chapters.lastIndex.coerceAtLeast(0)
    val startChapter = initialChapter.coerceIn(0, lastChapter)

    var from by remember { mutableIntStateOf(startChapter) }
    var to by remember { mutableIntStateOf((startChapter + 1).coerceAtMost(lastChapter)) }
    val listState = rememberLazyListState()
    var restored by remember { mutableStateOf(false) }
    /** 补章前记下的字符偏移，列表变长后据此重新定位 */
    var anchorOffset by remember { mutableStateOf<Int?>(null) }
    /** 锚点段落内部的滚动偏移，补章后一起还原，位移精确到像素 */
    var anchorItemOffset by remember { mutableIntStateOf(0) }
    /** 读到末尾只上报一次 */
    var endReported by remember { mutableStateOf(false) }

    // 段间距按字号换算，改字号时段落节奏跟着变
    val gap = (textStyle.fontSize.value * paragraphSpacing * 0.72f).dp
    val titleGap = gap * 1.6f
    val chapterGap = gap * 2.2f

    val items = remember(document, from, to) { buildParagraphs(document, from, to) }
    val liveItems by rememberUpdatedState(items)

    // 首次进入 / 目录跳转后定位到上次读到的那一段
    LaunchedEffect(Unit) {
        if (items.isEmpty()) return@LaunchedEffect
        val idx = items.indexOfLast { it.offset <= initialOffset }.coerceAtLeast(0)
        listState.scrollToItem(idx)
        restored = true
    }

    // 前面补了章：同一段话在列表里往后挪了，连段内偏移一起还原，画面纹丝不动
    LaunchedEffect(items) {
        val target = anchorOffset ?: return@LaunchedEffect
        val idx = items.indexOfLast { it.offset <= target }
        if (idx >= 0) listState.scrollToItem(idx, anchorItemOffset)
        anchorOffset = null
    }

    LaunchedEffect(listState, restored) {
        if (!restored) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()?.index ?: 0
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            Triple(first, last, info.totalItemsCount)
        }
            .distinctUntilChanged()
            .collect { (firstIdx, lastIdx, total) ->
                val visible = liveItems.getOrNull(firstIdx) ?: return@collect
                onOffsetChange(visible.offset)
                onChapterChange(visible.chapterIndex)

                // 快到底了就继续追加下一章
                if (total > 0 && lastIdx >= total - 4 && to < lastChapter) to += 1

                // 离顶还剩一两段时就把上一章补进来，这样滑到章首还能接着往上读
                if (firstIdx <= 1 && from > 0 && anchorOffset == null) {
                    anchorOffset = visible.offset
                    anchorItemOffset = listState.firstVisibleItemScrollOffset
                    from -= 1
                }

                // 全书最后一段已经露出来，算读完
                if (!endReported && to >= lastChapter && total > 0 && lastIdx >= total - 1) {
                    endReported = true
                    onReachEnd()
                }
            }
    }

    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 80.dp)) {
        itemsIndexed(items) { index, para ->
            Text(
                text = para.text,
                style = textStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        // 章与章之间留一口气，第一项不加
                        top = if (para.startsChapter && index > 0) chapterGap else 0.dp,
                        bottom = if (para.isTitle) titleGap else gap,
                    ),
            )
        }
    }
}

private data class Paragraph(
    val text: String,
    val offset: Int,
    val isTitle: Boolean,
    val chapterIndex: Int = 0,
    /** 是所在章的第一个段落，用来在章首留间距 */
    val startsChapter: Boolean = false,
)

/** 把 [from, to] 这些章拆成段落，offset 是全书绝对偏移，跨章连续 */
private fun buildParagraphs(document: BookDocument, from: Int, to: Int): List<Paragraph> {
    if (document.chapters.isEmpty()) return emptyList()
    val result = ArrayList<Paragraph>(1024)

    for (ci in from.coerceAtLeast(0)..to.coerceAtMost(document.chapters.lastIndex)) {
        val chapter = document.chapters[ci]
        val safeEnd = chapter.end.coerceAtMost(document.text.length)
        if (safeEnd <= chapter.start) continue

        val slice = document.text.substring(chapter.start, safeEnd)
        var cursor = chapter.start
        var first = true
        slice.split('\n').forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) {
                result.add(
                    Paragraph(
                        text = trimmed,
                        offset = cursor + line.indexOf(trimmed),
                        isTitle = index == 0 || (trimmed.length < 30 && trimmed == chapter.title),
                        chapterIndex = ci,
                        startsChapter = first,
                    )
                )
                first = false
            }
            cursor += line.length + 1
        }
        if (first) result.add(Paragraph(slice.take(2000), chapter.start, false, ci, true))
    }
    return result
}
