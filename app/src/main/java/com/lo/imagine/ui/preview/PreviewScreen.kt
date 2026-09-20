package com.lo.imagine.ui.preview


import com.lo.imagine.ui.theme.PopRadius
import android.app.Activity
import android.graphics.Bitmap
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lo.imagine.ui.PIcon
import com.lo.imagine.data.ImageRepository
import com.lo.imagine.ui.AppBus
import com.lo.imagine.ui.CreativeBusyIcon
import com.lo.imagine.ui.PopBack
import com.lo.imagine.ui.PopCheck
import com.lo.imagine.ui.PopCopy
import com.lo.imagine.ui.PopDownload
import com.lo.imagine.ui.PopEdit
import com.lo.imagine.ui.PopGallery
import com.lo.imagine.ui.PopIconButton
import com.lo.imagine.ui.EditState
import com.lo.imagine.ui.PreviewStore
import com.lo.imagine.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

import com.lo.imagine.ui.theme.themedCorner

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun clampPreviewOffset(
    bitmap: Bitmap,
    scale: Float,
    x: Float,
    y: Float,
    viewportWidth: Int,
    viewportHeight: Int
): Offset {
    if (viewportWidth <= 0 || viewportHeight <= 0) return Offset(x, y)
    val fitScale = minOf(
        viewportWidth.toFloat() / bitmap.width.toFloat(),
        viewportHeight.toFloat() / bitmap.height.toFloat()
    )
    val fittedWidth = bitmap.width * fitScale
    val fittedHeight = bitmap.height * fitScale
    val maxX = ((fittedWidth * scale - viewportWidth) / 2f).coerceAtLeast(0f)
    val maxY = ((fittedHeight * scale - viewportHeight) / 2f).coerceAtLeast(0f)
    return Offset(
        x.coerceIn(-maxX, maxX),
        y.coerceIn(-maxY, maxY)
    )
}

/** 相册模式页图缓存：按内存配额 LRU 缓存解码后的 Bitmap，翻页与预加载零重复解码。 */
private object PageImageCache {
    private val maxKb = (Runtime.getRuntime().maxMemory() / 1024L / 8L).toInt().coerceAtLeast(24 * 1024)
    private val lru = object : android.util.LruCache<String, Bitmap>(maxKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun get(file: File): Bitmap? = lru.get(file.absolutePath)

    suspend fun load(file: File): Bitmap? = withContext(Dispatchers.IO) {
        lru.get(file.absolutePath) ?: ImageUtils.decodeFile(file)?.also { lru.put(file.absolutePath, it) }
    }
}

@Composable
fun PreviewScreen(
    repository: ImageRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bitmap = PreviewStore.bitmap
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var viewportWidth by remember { mutableIntStateOf(0) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    val latestZoom = rememberUpdatedState(zoom)
    val latestOffsetX = rememberUpdatedState(offsetX)
    val latestOffsetY = rememberUpdatedState(offsetY)
    var sheetOpen by remember { mutableStateOf(false) }
    var switchDir by remember { mutableIntStateOf(1) }
    var immersive by remember { mutableStateOf(false) }
    // 相册模式（historyList 非空）专用：当前页是否处于放大态 / 缩放复位信号
    var pagerZoomed by remember { mutableStateOf(false) }
    var pagerResetTick by remember { mutableIntStateOf(0) }

    fun setImmersive(on: Boolean) {
        immersive = on
        val activity = context.findActivity() ?: return
        val controller = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (on) {
            controller.hide(android.view.WindowInsets.Type.systemBars())
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(android.view.WindowInsets.Type.systemBars())
        }
    }
    fun resetTransform() {
        zoom = 1f
        offsetX = 0f
        offsetY = 0f
    }

    // Offset clamping is handled by clampPreviewOffset().


    var switching by remember { mutableStateOf(false) }
    fun switchImage(dir: Int) {
        val list = PreviewStore.historyList ?: return
        val next = PreviewStore.historyIndex + dir
        if (next !in list.indices) return
        if (switching) return // 解码期间忽略连点，避免 bitmap 与元数据错位
        switching = true
        val entry = list[next]
        scope.launch {
            val nextBitmap = withContext(Dispatchers.IO) { ImageUtils.decodeFile(entry.file) }
            switching = false
            if (nextBitmap == null) return@launch
            switchDir = dir
            PreviewStore.historyIndex = next
            PreviewStore.bitmap = nextBitmap
            PreviewStore.prompt = entry.meta.prompt
            PreviewStore.model = entry.meta.model
            PreviewStore.workflowDetails = entry.meta.workflowDetails
            PreviewStore.elapsedText = ImageUtils.formatElapsed(entry.meta.elapsedSec)
            PreviewStore.sizeNote = null // 历史条目未记录上游原始像素
            saved = false
            resetTransform()
        }
    }

    DisposableEffect(Unit) {
        onDispose { setImmersive(false) }
    }

    BackHandler {
        when {
            sheetOpen -> sheetOpen = false
            // 相册模式：先复位当前页缩放，再退出
            PreviewStore.historyList?.isNotEmpty() == true && pagerZoomed -> pagerResetTick++
            zoom != 1f || offsetX != 0f || offsetY != 0f -> resetTransform()
            else -> onBack()
        }
    }

    fun copyPrompt() {
        if (PreviewStore.prompt.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("绘世提示词", PreviewStore.prompt))
        Toast.makeText(context, "提示词已复制", Toast.LENGTH_SHORT).show()
    }

    fun saveToGallery() {
        if (bitmap == null || saving || saved) return
        saving = true
        scope.launch {
            val uri = ImageUtils.saveToGallery(context, bitmap)
            saving = false
            if (uri != null) {
                saved = true
                Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "保存失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // The image uses a single, gesture-controlled scale. An extra resting scale would
    // make the zoom boundary and pan range move whenever the chrome appears.

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D10))
    ) {
        if (bitmap != null) {
            val history = PreviewStore.historyList
            if (!history.isNullOrEmpty()) {
                // —— 相册模式：HorizontalPager 跟手翻页，相邻页提前组合并异步解码，滑动零卡顿 ——
                val pagerState = rememberPagerState(
                    initialPage = PreviewStore.historyIndex.coerceIn(0, history.lastIndex)
                ) { history.size }
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.settledPage }.collect { page ->
                        val entry = history.getOrNull(page) ?: return@collect
                        if (PreviewStore.historyIndex != page) {
                            PreviewStore.historyIndex = page
                            saved = false
                        }
                        PreviewStore.prompt = entry.meta.prompt
                        PreviewStore.model = entry.meta.model
                        PreviewStore.workflowDetails = entry.meta.workflowDetails
                        PreviewStore.elapsedText = ImageUtils.formatElapsed(entry.meta.elapsedSec)
                        PreviewStore.sizeNote = null // 历史条目未记录上游原始像素
                        // 供保存/继续修图使用（通常已被页缓存命中，不触发磁盘解码）
                        PageImageCache.load(entry.file)?.let { PreviewStore.bitmap = it }
                    }
                }
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.currentPage }.collect {
                        // 翻过页后复位各页缩放
                        pagerResetTick++
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    GalleryPageView(
                        file = history[page].file,
                        immersive = immersive,
                        resetTick = pagerResetTick,
                        onToggleImmersive = { setImmersive(!immersive) },
                        onZoomChanged = { pagerZoomed = it }
                    )
                }
            } else {
            AnimatedContent(
                targetState = bitmap,
                transitionSpec = {
                    val forward = switchDir >= 0
                    if (forward) {
                        (slideInHorizontally(tween(240)) { it / 3 } + fadeIn(tween(240))) togetherWith
                            (slideOutHorizontally(tween(240)) { -it / 3 } + fadeOut(tween(240)))
                    } else {
                        (slideInHorizontally(tween(240)) { -it / 3 } + fadeIn(tween(240))) togetherWith
                            (slideOutHorizontally(tween(240)) { it / 3 } + fadeOut(tween(240)))
                    }
                },
                label = "previewSwitch",
                modifier = Modifier.fillMaxSize()
            ) { current ->
                Image(
                    bitmap = current.asImageBitmap(),
                    contentDescription = "创作预览",
                    contentScale = ContentScale.Fit,
modifier = Modifier
.fillMaxSize()
                         .padding(
                             top = if (immersive) 0.dp else 54.dp,
                             bottom = if (immersive) 0.dp else 148.dp,
                             start = 8.dp,
                             end = 8.dp
                         )
                         .onSizeChanged { viewportWidth = it.width; viewportHeight = it.height }
                         .clip(RoundedCornerShape(if (immersive) 0.dp else 18.dp))
                         .pointerInput(current, viewportWidth, viewportHeight) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false).consume()
                                var transformGesture = false
                                var movedHorizontally = false
                                var cancelled = false
                                var totalDx = 0f
                                var totalDy = 0f
                                var gestureZoom = latestZoom.value.coerceIn(1f, 6f)
                                var gestureOffset = Offset(latestOffsetX.value, latestOffsetY.value)

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pointerCount = event.changes.count { it.pressed }
                                    if (pointerCount == 0) break

                                    if (pointerCount >= 2) {
                                        // Pinch and two-finger pan share one local accumulator. This
                                        // prevents recomposition from resetting the gesture baseline.
                                        transformGesture = true
                                        val oldZoom = gestureZoom
                                        val newZoom = (oldZoom * event.calculateZoom()).coerceIn(1f, 6f)
                                        val pan = event.calculatePan()
                                        val bounded = clampPreviewOffset(
                                            current,
                                            newZoom,
                                            gestureOffset.x + pan.x,
                                            gestureOffset.y + pan.y,
                                            viewportWidth,
                                            viewportHeight
                                        )
                                        gestureZoom = newZoom
                                        gestureOffset = bounded
                                        zoom = newZoom
                                        offsetX = bounded.x
                                        offsetY = bounded.y
                                        event.changes.forEach { it.consume() }
                                    } else {
                                        val change = event.changes.firstOrNull() ?: break
                                        val delta = change.positionChange()
                                        totalDx += delta.x
                                        totalDy += delta.y

                                        if (transformGesture || gestureZoom > 1.001f) {
                                            // Once zoomed, one finger pans the image and never
                                            // competes with the history swipe gesture.
                                            transformGesture = true
                                            val bounded = clampPreviewOffset(
                                                current,
                                                gestureZoom,
                                                gestureOffset.x + delta.x,
                                                gestureOffset.y + delta.y,
                                                viewportWidth,
                                                viewportHeight
                                            )
                                            gestureOffset = bounded
                                            offsetX = bounded.x
                                            offsetY = bounded.y
                                            change.consume()
                                        } else if (!cancelled) {
                                            if (
                                                abs(totalDx) > viewConfiguration.touchSlop &&
                                                abs(totalDx) > abs(totalDy) * 1.2f
                                            ) {
                                                movedHorizontally = true
                                                change.consume()
                                            } else if (abs(totalDy) > viewConfiguration.touchSlop) {
                                                // A vertical move is neither a tap nor a history swipe.
                                                cancelled = true
                                            }
                                        }
                                    }
                                }

                                when {
                                    transformGesture -> Unit
                                    !cancelled && !movedHorizontally -> setImmersive(!immersive)
                                    movedHorizontally && gestureZoom <= 1.001f && totalDx <= -60f -> switchImage(1)
                                    movedHorizontally && gestureZoom <= 1.001f && totalDx >= 60f -> switchImage(-1)
                                }
                            }
                        }
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = offsetX
                            translationY = offsetY
                        }
                )
            }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) {
                PIcon(PopGallery, contentDescription = null, tint = Color.White.copy(alpha = .8f), modifier = Modifier.size(42.dp))
                Spacer(Modifier.size(10.dp))
                Text("图片暂时不可用", color = Color.White.copy(alpha = .8f))
            }
        }

        AnimatedVisibility(
            visible = !immersive,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                PopIconButton(
                    icon = PopBack,
                    contentDescription = "返回",
                    onClick = onBack,
                    modifier = Modifier.size(40.dp),
                    containerColor = Color.Black.copy(alpha = .56f),
                    iconTint = Color.White,
                    iconSize = 22.dp,
                    shape = CircleShape,
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("预览", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (PreviewStore.model.isNotBlank()) {
                        Text(PreviewStore.model, color = Color.White.copy(alpha = .58f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // 右上角：复制/保存图标一字排开，像素角标居其下
        AnimatedVisibility(
            visible = !immersive && bitmap != null,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 48.dp, end = 12.dp)
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = { copyPrompt() },
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = .56f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            PIcon(
                                PopCopy,
                                contentDescription = "复制提示词",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Surface(
                        onClick = { saveToGallery() },
                        enabled = !saving && !saved,
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = .56f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            if (saving) {
                                CreativeBusyIcon(Modifier.size(19.dp))
                            } else if (saved) {
                                PIcon(
                                    PopCheck,
                                    contentDescription = "已保存",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            } else {
                                PIcon(
                                    PopDownload,
                                    contentDescription = "保存到相册",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
                Surface(
                    color = Color.Black.copy(alpha = .32f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text("${bitmap?.width} × ${bitmap?.height}", color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.labelSmall)
                        if (PreviewStore.elapsedText.isNotBlank()) {
                            Spacer(Modifier.width(7.dp))
                            Text(PreviewStore.elapsedText, color = Color.White.copy(alpha = .55f), style = MaterialTheme.typography.labelSmall)
                        }
                        val upstreamNote = PreviewStore.sizeNote
                        if (upstreamNote != null && bitmap != null && upstreamNote != "${bitmap.width}x${bitmap.height}") {
                            Spacer(Modifier.width(7.dp))
                            Text(
                                "模型实际输出 ${upstreamNote.replace("x", "×")}",
                                color = Color.White.copy(alpha = .55f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !immersive && PreviewStore.prompt.isNotBlank(),
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(120)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .navigationBarsPadding()
                .padding(bottom = 10.dp)
        ) {
            PromptDrawer(
                open = sheetOpen,
                onOpenChange = { sheetOpen = it },
                                 onEdit = {
                     PreviewStore.bitmap?.let { current ->
                         val (editBitmap, editBytes) = ImageUtils.prepareForEdit(current)
                         // 源图即修图对象：必须同步 refBitmaps，否则修图页缩略图空白、生成被判空静默 return
                         EditState.setSource(editBitmap, editBytes, "image/jpeg", asSingleReference = true)
                        EditState.prompt = ""
                        AppBus.goTo("edit")
                        onBack()
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = !immersive && PreviewStore.prompt.isBlank(),
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).navigationBarsPadding()) {
                    TextButton(
                        onClick = {
                            PreviewStore.bitmap?.let { current ->
                                val (editBitmap, editBytes) = ImageUtils.prepareForEdit(current)
                                EditState.setSource(editBitmap, editBytes, "image/jpeg", asSingleReference = true)
                                EditState.prompt = ""
                                AppBus.goTo("edit")
                                onBack()
                            }
                        },
                        enabled = bitmap != null,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        PIcon(PopEdit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("继续修图")
                    }
                    Button(
                        onClick = { saveToGallery() },
                        enabled = bitmap != null && !saving && !saved,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        if (saving) {
                            CreativeBusyIcon(Modifier.size(22.dp))
                        } else {
                            PIcon(PopDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.size(6.dp))
                        Text(if (saved) "已保存" else "保存到相册")
                    }
                }
            }
        }

        if (immersive && bitmap != null) {
            Surface(
                color = Color.Black.copy(alpha = .38f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp)
            ) {
                Text("点按画面显示控件", color = Color.White.copy(alpha = .62f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }
    }
}

@Composable
private fun GalleryPageView(
    file: File,
    immersive: Boolean,
    resetTick: Int,
    onToggleImmersive: () -> Unit,
    onZoomChanged: (Boolean) -> Unit
) {
    // 每页独立解码与缩放状态：LruCache 命中即秒显，未命中后台解码
    var bmp by remember { mutableStateOf(PageImageCache.get(file)) }
    LaunchedEffect(file) {
        if (bmp == null) bmp = PageImageCache.load(file)
    }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var viewportWidth by remember { mutableIntStateOf(0) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    val latestZoom = rememberUpdatedState(zoom)
    val latestOffsetX = rememberUpdatedState(offsetX)
    val latestOffsetY = rememberUpdatedState(offsetY)

    // 翻页后复位缩放
    LaunchedEffect(resetTick) {
        if (resetTick > 0) {
            zoom = 1f
            offsetX = 0f
            offsetY = 0f
            onZoomChanged(false)
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val current = bmp
        if (current == null) {
            CreativeBusyIcon(Modifier.size(34.dp))
        } else {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "创作预览",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = if (immersive) 0.dp else 54.dp,
                        bottom = if (immersive) 0.dp else 148.dp,
                        start = 8.dp,
                        end = 8.dp
                    )
                    .onSizeChanged { viewportWidth = it.width; viewportHeight = it.height }
                    .clip(RoundedCornerShape(if (immersive) 0.dp else 18.dp))
                    // 轻点切沉浸：交给 detectTapGestures，翻页手势不会误触发
                    .pointerInput(current) {
                        detectTapGestures { onToggleImmersive() }
                    }
                    // 双指捏合与放大后的平移：仅这两种情况消费事件，
                    // 其余横向位移全部放行给 Pager 实现跟手翻页
                    .pointerInput(current, viewportWidth, viewportHeight) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var transformGesture = false
                            var gestureZoom = latestZoom.value.coerceIn(1f, 6f)
                            var gestureOffset = Offset(latestOffsetX.value, latestOffsetY.value)

                            while (true) {
                                val event = awaitPointerEvent()
                                val pointerCount = event.changes.count { it.pressed }
                                if (pointerCount == 0) break

                                if (pointerCount >= 2) {
                                    transformGesture = true
                                    val oldZoom = gestureZoom
                                    val newZoom = (oldZoom * event.calculateZoom()).coerceIn(1f, 6f)
                                    val pan = event.calculatePan()
                                    val bounded = clampPreviewOffset(
                                        current,
                                        newZoom,
                                        gestureOffset.x + pan.x,
                                        gestureOffset.y + pan.y,
                                        viewportWidth,
                                        viewportHeight
                                    )
                                    gestureZoom = newZoom
                                    gestureOffset = bounded
                                    zoom = newZoom
                                    offsetX = bounded.x
                                    offsetY = bounded.y
                                    onZoomChanged(newZoom > 1.001f)
                                    event.changes.forEach { it.consume() }
                                } else {
                                    val change = event.changes.firstOrNull() ?: break
                                    val delta = change.positionChange()
                                    if (transformGesture || gestureZoom > 1.001f) {
                                        val bounded = clampPreviewOffset(
                                            current,
                                            gestureZoom,
                                            gestureOffset.x + delta.x,
                                            gestureOffset.y + delta.y,
                                            viewportWidth,
                                            viewportHeight
                                        )
                                        gestureOffset = bounded
                                        offsetX = bounded.x
                                        offsetY = bounded.y
                                        change.consume()
                                    }
                                    // 未放大时不消费任何位移：横向跟手翻页
                                }
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = offsetX
                        translationY = offsetY
                    }
            )
        }
    }
}

@Composable
private fun PromptDrawer(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val collapsedHeight = 118.dp
    val expandedHeight = 414.dp
    val dragRange = expandedHeight - collapsedHeight
    val panelShape = com.lo.imagine.ui.theme.themedShape(PopRadius.card)
    val expansionState = remember { mutableFloatStateOf(if (open) 1f else 0f) }
    var dragging by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val currentOpen by rememberUpdatedState(open)

    fun animateTo(target: Float) {
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(
                initialValue = expansionState.floatValue,
                targetValue = target,
                animationSpec = spring(dampingRatio = .84f, stiffness = 620f)
            ) { value, _ -> expansionState.floatValue = value }
        }
    }

    LaunchedEffect(open) {
        if (!dragging) animateTo(if (open) 1f else 0f)
    }

    val expansion = expansionState.floatValue.coerceIn(0f, 1f)
    val panelHeight = (
        collapsedHeight.value +
            (expandedHeight.value - collapsedHeight.value) * expansion
        ).dp
    val actionsVisible = expansion > .92f

    Surface(
        color = Color(0xF21A1A20),
        contentColor = Color.White,
        shape = panelShape,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(panelHeight)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部整条（把手 + 标题行）是拖动热区：上拖面板上沿跟手展开，下拖收起。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            dragging = false
                            val targetOpen = expansionState.floatValue < .5f
                            if (targetOpen != currentOpen) onOpenChange(targetOpen)
                            else animateTo(if (targetOpen) 1f else 0f)
                        }
                    }
                    .pointerInput(Unit) {
                        val rangePx = dragRange.toPx().coerceAtLeast(1f)
                        val tracker = VelocityTracker()
                        detectVerticalDragGestures(
                            onDragStart = {
                                tracker.resetTracking()
                                settleJob?.cancel()
                                dragging = true
                            },
                            onVerticalDrag = { change, dragAmount ->
                                tracker.addPosition(change.uptimeMillis, change.position)
                                expansionState.floatValue =
                                    (expansionState.floatValue - dragAmount / rangePx).coerceIn(0f, 1f)
                            },
                            onDragEnd = {
                                dragging = false
                                val velocityY = tracker.calculateVelocity().y
                                val targetOpen = when {
                                    velocityY < -700f -> true
                                    velocityY > 700f -> false
                                    else -> expansionState.floatValue >= .46f
                                }
                                if (targetOpen != currentOpen) onOpenChange(targetOpen)
                                else animateTo(if (targetOpen) 1f else 0f)
                            },
                            onDragCancel = {
                                dragging = false
                                animateTo(if (currentOpen) 1f else 0f)
                            }
                        )
                    }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().height(26.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(4.dp)
                            .background(Color.White.copy(alpha = .3f), RoundedCornerShape(50))
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(48.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "提示词",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        if (PreviewStore.model.isNotBlank()) {
                            Text(
                                PreviewStore.model,
                                color = Color.White.copy(alpha = .5f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                                    }
            }

            // 正文随面板高度连续伸缩：折叠时露出两行，拖开时逐行增加。
            Text(
                listOfNotNull(PreviewStore.prompt.takeIf { it.isNotBlank() }, PreviewStore.workflowDetails).joinToString("\n\n"),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = .9f),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            )

            if (actionsVisible) {
                TextButton(
                    onClick = onEdit,
                    enabled = PreviewStore.bitmap != null,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    PIcon(PopEdit, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("继续修图")
                }
            }
        }
    }
}
// 旧的自研 rolloutHandleGesture 已移除：拖动热区与手势检测并入 PromptDrawer 顶部横条。