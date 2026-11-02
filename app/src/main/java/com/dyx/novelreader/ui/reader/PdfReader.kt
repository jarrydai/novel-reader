package com.dyx.novelreader.ui.reader

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.dyx.novelreader.data.books.BookDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/**
 * PDF 阅读：用系统 PdfRenderer 把每一页渲染成位图显示。
 *
 * Android 的 PdfRenderer 不支持取文字，所以 PDF 走"按页看图"的模式，
 * 进度按页码记录；翻页与文本模式共用同样的左右点击 / 滑动操作。
 */
@Composable
fun PdfReader(
    document: BookDocument,
    colors: ReaderColors,
    initialPage: Int,
    onBack: () -> Unit,
    onOpenChapters: () -> Unit,
    onOpenSettings: () -> Unit,
    onProgress: (Int, String, Boolean) -> Unit,
    jumpTo: Int?,
    onJumpHandled: () -> Unit,
) {
    val path = document.pdfPath ?: return
    var pageIndex by remember(document) {
        mutableIntStateOf(initialPage.coerceIn(0, document.pageCount - 1))
    }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var barsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(jumpTo) {
        val target = jumpTo ?: return@LaunchedEffect
        pageIndex = target.coerceIn(0, document.pageCount - 1)
        onJumpHandled()
    }

    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        val targetWidthPx = with(density) { (maxWidth - 24.dp).roundToPx() }

        LaunchedEffect(pageIndex, targetWidthPx) {
            bitmap = withContext(Dispatchers.IO) {
                renderPdfPage(path, pageIndex, targetWidthPx)
            }
        }

        LaunchedEffect(pageIndex) {
            onProgress(pageIndex, "第 ${pageIndex + 1} 页", pageIndex == document.pageCount - 1)
        }

        fun goNext() {
            if (pageIndex < document.pageCount - 1) pageIndex++
        }

        fun goPrev() {
            if (pageIndex > 0) pageIndex--
        }

        Column(Modifier.fillMaxSize()) {
            ReaderTopBar(
                title = document.title,
                subtitle = "第 ${pageIndex + 1} / ${document.pageCount} 页",
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
                    .pointerInput(document, pageIndex) {
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
                    },
                contentAlignment = Alignment.Center,
            ) {
                val bmp = bitmap
                if (bmp == null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(28.dp),
                            color = colors.dim,
                        )
                        Text(
                            "正在渲染第 ${pageIndex + 1} 页…",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.dim,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                } else {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "第 ${pageIndex + 1} 页",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(4.dp)),
                    )
                }
            }

            ReaderBottomBar(
                visible = barsVisible,
                page = pageIndex + 1,
                pageCount = document.pageCount,
                progress = (pageIndex + 1).toFloat() / document.pageCount,
                chapterTitle = "第 ${pageIndex + 1} 页",
                colors = colors,
            )
        }
    }
}

/** 渲染单页；失败返回 null（加密或缺字体的 PDF 会失败） */
private fun renderPdfPage(path: String, index: Int, targetWidth: Int): Bitmap? = runCatching {
    val file = File(path)
    if (!file.exists()) return null

    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = PdfRenderer(pfd)
    try {
        if (index !in 0 until renderer.pageCount) return null
        renderer.openPage(index).use { page ->
            val scale = targetWidth.toFloat() / page.width
            val width = targetWidth.coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    } finally {
        // PdfRenderer.close() 会一并关闭传入的 ParcelFileDescriptor
        renderer.close()
    }
}.getOrNull()
