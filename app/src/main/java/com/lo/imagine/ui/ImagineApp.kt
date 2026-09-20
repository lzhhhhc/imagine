package com.lo.imagine.ui
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.font.FontFamily
import com.lo.imagine.ui.theme.RefHud

import com.lo.imagine.ui.theme.PopRadius

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.snapshotFlow
import com.lo.imagine.ui.studio.NaiWorkspaceState
import com.lo.imagine.ui.studio.NaiWorkspaceScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lo.imagine.R
import com.lo.imagine.ui.PIcon
import com.lo.imagine.data.AppSettings
import com.lo.imagine.data.ImageRepository
import com.lo.imagine.data.SettingsRepository
import com.lo.imagine.ui.edit.EditScreen
import com.lo.imagine.ui.history.HistoryScreen
import com.lo.imagine.ui.preview.PreviewScreen
import com.lo.imagine.ui.settings.SettingsScreen
import com.lo.imagine.ui.studio.StudioScreen
import com.lo.imagine.ui.theme.Ink
import com.lo.imagine.ui.theme.LocalPopAccents
import com.lo.imagine.ui.theme.Signal
import kotlin.math.abs

private data class BottomItem(
    val route: String,
    val label: String,
    val iconRes: Int
)

private val bottomItems = listOf(
    BottomItem("studio", "标准", com.lo.imagine.R.drawable.ic_ark_spark),
    BottomItem("edit", "修图", com.lo.imagine.R.drawable.ic_ark_crop),
    BottomItem("director", "导演", com.lo.imagine.R.drawable.ic_ark_clapper),
    BottomItem("works", "作品", com.lo.imagine.R.drawable.ic_ark_folder),
    BottomItem("settings", "设置", com.lo.imagine.R.drawable.ic_ark_hex)
)

/**
 * 底栏左右滑动时该去哪个页面：手指左滑 = 下一 tab，右滑 = 上一 tab，两端不越界。
 * NAI 是「标准」的另一种模式，按第 01 格算；未知路由（如预览页）不响应。
 */
internal fun dockSwipeTarget(currentRoute: String?, delta: Float): String? {
    if (delta == 0f) return null
    val shown = if (currentRoute == "nai") "studio" else currentRoute
    val index = bottomItems.indexOfFirst { it.route == shown }
    if (index < 0) return null
    return bottomItems.getOrNull(if (delta < 0) index + 1 else index - 1)?.route
}

@Composable
fun ImagineApp(
    settingsRepository: SettingsRepository,
    imageRepository: ImageRepository
) {
    val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
    LaunchedEffect(settingsRepository) {
        if (!NaiWorkspaceState.ready) {
            // 设置存储没有响应时绝不能一直转圈：5 秒后按内存默认值进入页面并说明原因
            val loaded = withTimeoutOrNull(5_000) { runCatching { settingsRepository.naiWorkspaceFlow().first() }.getOrNull() }
            if (loaded != null) {
                NaiWorkspaceState.config = loaded
            } else {
                NaiWorkspaceState.error = "工作台配置读取失败：设置存储 5 秒内未响应，已按默认值进入，本次改动可能不会保存"
            }
            NaiWorkspaceState.ready = true
        }
        // 写失败/写不动都不能让收集器退出，否则之后所有配置改动都不再落盘
        snapshotFlow { NaiWorkspaceState.config }.collect { value ->
            val result = runCatching { withTimeoutOrNull(5_000) { settingsRepository.saveNaiWorkspace(value) } }
            result.onFailure { e -> android.util.Log.w("ImagineHttp", "NAI 配置保存失败：${e.message}") }
            if (result.getOrNull() == null) android.util.Log.w("ImagineHttp", "NAI 配置保存超时（设置存储 5 秒未响应），本次改动仅在内存")
        }
    }
    // 一次性清理：旧版「前置提示词」已下线，存储里那份文本一并删掉（它会被上游当越狱请求而拒答）
    LaunchedEffect(settingsRepository, "legacyPrePromptCleanup") {
        withTimeoutOrNull(5_000) { runCatching { settingsRepository.clearLegacyPrePrompt() } }
    }
    // 一次性清理：旧版首页画师串残留（首页已无编辑入口，它曾污染每次普通模式生成）
    LaunchedEffect(settingsRepository, "legacyArtistStringCleanup") {
        withTimeoutOrNull(5_000) { runCatching { settingsRepository.clearLegacyArtistString() } }
    }
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val view = LocalView.current
    SideEffect {
        val activity = view.context as? android.app.Activity
        if (activity != null) {
            val lightBars = settings.themeMode != "ark_dark"
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
    }

    LaunchedEffect(AppBus.requestRoute) {
        AppBus.requestRoute?.let { requested ->
            navController.navigate(requested) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            AppBus.consumeRoute()
        }
    }

    fun navigateTo(destination: String) {
        // NAI 模式记忆：切走再回到「创作」时回到 NAI 页，不被拽回普通模式
        val target = if (destination == "studio" && NaiModeState.active) "nai" else destination
        navController.navigate(target) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val directorKeyboardOpen = route == "director" && WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (route != "preview" && !directorKeyboardOpen) {
                AppDock(currentRoute = if (route == "nai") "studio" else route, onNavigate = ::navigateTo)
            }
        }
    ) { padding ->
        val dockRoute by rememberUpdatedState(if (route == "preview") null else route)
        val backdropRoute = when (route) {
            "preview" -> null
            "nai" -> "studio"
            else -> route
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var locked = false
                        var totalDx = 0f
                        var totalDy = 0f
                        val slop = viewConfiguration.touchSlop
                        // 快速翻页的触发距离：竖排文字滑动的两倍左右，轻扫即翻
                        val trigger = 48.dp.toPx()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.pressed } ?: break
                            // 子层已消费（纵向滚动/横滑容器）：整次手势让出
                            if (change.isConsumed) break
                            // 不用 positionChange()：它会消费并沿整个手势传递消费态，
                            // 下一帧就会被 isConsumed 误判成子页面抢走。
                            totalDx += change.position.x - change.previousPosition.x
                            totalDy += change.position.y - change.previousPosition.y
                            if (!locked) {
                                when {
                                    // 横向意图明确：锁定为本手势所有
                                    abs(totalDx) > slop && abs(totalDx) > abs(totalDy) -> locked = true
                                    // 纵向占优：这是上下滚，交还页面
                                    abs(totalDy) > slop -> break
                                }
                            }
                            if (locked && abs(totalDx) >= trigger) {
                                // 立即翻页：转场交给 NavHost 现有动画，不做任何跟手位移，
                                // 避免内容位移与页面转场叠加（上一版卡半路的根因）。
                                val target = dockSwipeTarget(dockRoute, totalDx) ?: break
                                change.consume()
                                navigateTo(target)
                                break
                            }
                        }
                    }
                }
        ) {
            if (backdropRoute != null) {
                ArkPageBackdrop(route = backdropRoute, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }
        NavHost(
            navController = navController,
            startDestination = "studio",
            modifier = Modifier.padding(if (route == "preview") PaddingValues(0.dp) else padding)
                .then(if (route == "director") Modifier.consumeWindowInsets(padding) else Modifier),
            // 官网转场语言：内容缩放回落（scale 1.04→1）+ 淡入，exit 快淡出；总时长对齐官网 .6s 节奏
            enterTransition = { fadeIn(tween(360, easing = FastOutSlowInEasing)) + scaleIn(initialScale = 1.04f, animationSpec = tween(360, easing = FastOutSlowInEasing)) },
            exitTransition = { fadeOut(tween(220, easing = FastOutSlowInEasing)) },
            popEnterTransition = { fadeIn(tween(360, easing = FastOutSlowInEasing)) + scaleIn(initialScale = 1.04f, animationSpec = tween(360, easing = FastOutSlowInEasing)) },
            popExitTransition = { fadeOut(tween(220, easing = FastOutSlowInEasing)) }
        ) {
            composable("nai") {
                // 进入 NAI 页即记住「当前模式」
                LaunchedEffect(Unit) { NaiModeState.active = true }
                NaiWorkspaceScreen(settings, settingsRepository, imageRepository,
                    onHome = {
                        // 手动回普通模式：先清标记，否则 navigateTo 又会被拽回 NAI
                        NaiModeState.active = false
                        navigateTo("studio")
                    },
                    onPreview = { navController.navigate("preview") })
            }
            composable("studio") {
                // 真正落到普通创作页时才清掉 NAI 模式记忆
                LaunchedEffect(Unit) { NaiModeState.active = false }
                StudioScreen(
                    settings = settings,
                    repository = imageRepository,
                    settingsRepository = settingsRepository,
                    onPreview = { navController.navigate("preview") },
                    onOpenNai = {
                        NaiModeState.active = true
                        navigateTo("nai")
                    }
                )
            }
             composable("edit") {
                EditScreen(
                    settings = settings,
                    repository = imageRepository,
                    onPreview = { navController.navigate("preview") }
                )
            }
            composable("director") {
                com.lo.imagine.ui.director.DirectorScreen(
                    settings = settings,
                    repository = imageRepository,
                    settingsRepository = settingsRepository
                )
            }
            composable("works") {
                HistoryScreen(onPreview = { navController.navigate("preview") })
            }
            composable("settings") {
                SettingsScreen(
                    current = settings,
                    repository = settingsRepository,
                    imageRepository = imageRepository
                )
            }
            composable("preview") {
                PreviewScreen(
                    repository = imageRepository,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        }
    }
}

@Composable
private fun AppDock(currentRoute: String?, onNavigate: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(color = c.background, tonalElevation = 0.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(.7.dp).background(c.outlineVariant))
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                bottomItems.forEachIndexed { index, item ->
                    val selected = currentRoute == item.route
                    Surface(onClick = { onNavigate(item.route) }, color = Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).heightIn(min = RefUiTokens.dockHeight)
                            .semantics { this.selected = selected; role = Role.Tab }) {
                        Box(Modifier.fillMaxWidth()) {
                            if (selected) Box(Modifier.matchParentSize().background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(listOf(
                                    c.primaryContainer, c.primaryContainer.copy(alpha = .15f))), RoundedCornerShape(12.dp)))
                            if (selected) Box(Modifier.align(Alignment.TopCenter).width(42.dp).height(1.5.dp)
                                .background(c.primary, CircleShape))
                            Column(Modifier.fillMaxWidth().padding(top = 17.dp, bottom = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                PIcon(item.iconRes, null, Modifier.size(RefUiTokens.dockIcon), tint = c.primary)
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("0${index + 1}", fontFamily = RefHud, fontSize = 8.sp, lineHeight = 12.sp,
                                        fontWeight = FontWeight.SemiBold, color = if (selected) c.primary else c.onSurfaceVariant)
                                    Spacer(Modifier.width(3.dp))
                                    Text(item.label, fontFamily = FontFamily.SansSerif, fontSize = 11.sp, lineHeight = 15.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) c.onSurface else c.onSurfaceVariant, maxLines = 1)
                                }
                            }
                            if (selected) Box(Modifier.align(Alignment.BottomCenter).width(22.dp).height(2.dp)
                                .background(c.primary, CircleShape))
                        }
                    }
                }
            }
        }
    }
}
