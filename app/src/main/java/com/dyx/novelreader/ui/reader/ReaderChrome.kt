package com.dyx.novelreader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dyx.novelreader.data.books.BookDocument
import com.dyx.novelreader.data.model.ReaderFont
import com.dyx.novelreader.data.model.ReaderSettings
import com.dyx.novelreader.data.model.ReaderTheme
import com.dyx.novelreader.data.model.ReaderWeight
import kotlin.math.roundToInt

/** 顶部栏：返回 / 书名章节 / 目录 / 设置 */
@Composable
fun ReaderTopBar(
    title: String,
    subtitle: String,
    visible: Boolean,
    colors: ReaderColors,
    onBack: () -> Unit,
    onChapters: () -> Unit,
    onSettings: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.bar)
                .statusBarsPadding()
                .padding(start = 2.dp, end = 6.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = colors.text)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.text,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onChapters) {
                Icon(Icons.AutoMirrored.Rounded.List, "目录", tint = colors.text)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.FormatSize, "阅读设置", tint = colors.text)
            }
        }
    }
}

/** 底部栏：页码 + 进度 */
@Composable
fun ReaderBottomBar(
    visible: Boolean,
    page: Int,
    pageCount: Int,
    progress: Float,
    chapterTitle: String,
    colors: ReaderColors,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.bar)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = colors.dim,
                trackColor = colors.dim.copy(alpha = 0.18f),
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chapterTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.dim,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        if (pageCount > 0) append("$page / $pageCount  ·  ")
                        append("${(progress * 100).roundToInt()}%")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.dim,
                )
            }
        }
    }
}

/** 目录面板 */
@Composable
fun ChapterListSheet(
    document: BookDocument,
    currentOffset: Int,
    colors: ReaderColors,
    onPick: (Int) -> Unit,
) {
    val currentIndex = remember(document, currentOffset) { document.chapterIndexAt(currentOffset) }
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        listState.scrollToItem((currentIndex - 3).coerceAtLeast(0))
    }

    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "目录",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "共 ${document.chapters.size} 章",
                style = MaterialTheme.typography.labelSmall,
                color = colors.dim,
            )
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().height(440.dp)) {
            itemsIndexed(document.chapters) { index, chapter ->
                val active = index == currentIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(chapter.start) }
                        .background(if (active) colors.dim.copy(alpha = 0.12f) else colors.bar)
                        .padding(horizontal = 22.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        chapter.title.ifBlank { "第 ${index + 1} 章" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (active) colors.text else colors.text.copy(alpha = 0.78f),
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (active) {
                        Text("阅读中", style = MaterialTheme.typography.labelSmall, color = colors.dim)
                    }
                }
            }
        }
    }
}

/** 阅读设置面板：字号 / 行距 / 字体 / 字重 / 翻页方式 / 背景 */
@Composable
fun ReaderSettingsSheet(
    settings: ReaderSettings,
    colors: ReaderColors,
    onChange: (ReaderSettings) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        Text(
            "阅读设置",
            style = MaterialTheme.typography.titleMedium,
            color = colors.text,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(14.dp))

        StepperRow(
            label = "字号",
            value = "${settings.fontSize.roundToInt()}",
            colors = colors,
            onMinus = { onChange(settings.copy(fontSize = (settings.fontSize - 1f).coerceAtLeast(12f))) },
            onPlus = { onChange(settings.copy(fontSize = (settings.fontSize + 1f).coerceAtMost(34f))) },
        )
        StepperRow(
            label = "行距",
            value = String.format("%.2f", settings.lineHeight),
            colors = colors,
            onMinus = { onChange(settings.copy(lineHeight = (settings.lineHeight - 0.1f).coerceAtLeast(1.2f))) },
            onPlus = { onChange(settings.copy(lineHeight = (settings.lineHeight + 0.1f).coerceAtMost(2.6f))) },
        )

        // 字体：每个选项直接用自身字体渲染示例字，一眼看出差别
        Text("字体", style = MaterialTheme.typography.labelMedium, color = colors.dim)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReaderFont.entries.forEach { font ->
                ChoiceChip(
                    label = font.label,
                    selected = font == settings.font,
                    colors = colors,
                    fontFamily = font.family(),
                ) { onChange(settings.copy(font = font)) }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("字重", style = MaterialTheme.typography.labelMedium, color = colors.dim)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReaderWeight.entries.forEach { weight ->
                ChoiceChip(
                    label = weight.label,
                    selected = weight == settings.weight,
                    colors = colors,
                    fontWeight = weight.composeWeight,
                ) { onChange(settings.copy(weight = weight)) }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("阅读方式", style = MaterialTheme.typography.labelMedium, color = colors.dim)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceChip("左右翻页", settings.pageMode, colors) { onChange(settings.copy(pageMode = true)) }
            ChoiceChip("上下滚动", !settings.pageMode, colors) { onChange(settings.copy(pageMode = false)) }
        }

        Spacer(Modifier.height(18.dp))
        Text("背景", style = MaterialTheme.typography.labelMedium, color = colors.dim)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReaderTheme.entries.forEach { theme ->
                val c = readerColors(theme)
                val selected = theme == settings.readerTheme
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onChange(settings.copy(readerTheme = theme)) }
                        .padding(horizontal = 5.dp, vertical = 4.dp),
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .border(
                                width = 2.dp,
                                color = if (selected) colors.text else Color.Transparent,
                                shape = CircleShape,
                            )
                            .padding(5.dp)
                            .background(c.background, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("文", color = c.text, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        theme.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) colors.text else colors.dim,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** 宋体 / 黑体 / 等宽 —— 全部映射到系统内置字体族，不额外打包字体 */
internal fun ReaderFont.family(): FontFamily = when (this) {
    ReaderFont.SERIF -> FontFamily.Serif
    ReaderFont.SANS -> FontFamily.SansSerif
    ReaderFont.MONO -> FontFamily.Monospace
}

/** 字重映射：加粗用 SemiBold，中文下比 Bold 更耐读 */
internal val ReaderWeight.composeWeight: FontWeight
    get() = when (this) {
        ReaderWeight.NORMAL -> FontWeight.Normal
        ReaderWeight.BOLD -> FontWeight.SemiBold
    }


@Composable
private fun StepperRow(
    label: String,
    value: String,
    colors: ReaderColors,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.text, modifier = Modifier.width(56.dp))
        Surface(
            onClick = onMinus,
            shape = CircleShape,
            color = colors.dim.copy(alpha = 0.14f),
            modifier = Modifier.size(34.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("−", color = colors.text, fontSize = 17.sp)
            }
        }
        Box(Modifier.width(58.dp), contentAlignment = Alignment.Center) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.text)
        }
        Surface(
            onClick = onPlus,
            shape = CircleShape,
            color = colors.dim.copy(alpha = 0.14f),
            modifier = Modifier.size(34.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("+", color = colors.text, fontSize = 17.sp)
            }
        }
    }
}

/** 通用选择胶囊：字体项会带入自身字族，让选项本身成为预览 */
@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    colors: ReaderColors,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) colors.dim.copy(alpha = 0.22f) else colors.dim.copy(alpha = 0.08f),
        border = if (selected) BorderStroke(1.5.dp, colors.text.copy(alpha = 0.55f)) else null,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = colors.text,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}
