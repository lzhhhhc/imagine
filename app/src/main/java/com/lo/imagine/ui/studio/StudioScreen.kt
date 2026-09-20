package com.lo.imagine.ui.studio
import com.lo.imagine.ui.RefUiTokens
import com.lo.imagine.ui.RefIcons
import com.lo.imagine.ui.RefSectionHeading
import com.lo.imagine.ui.RefPromptHeading
import com.lo.imagine.ui.RefOutlineAction
import com.lo.imagine.ui.RefResolutionBadge
import com.lo.imagine.ui.RefFieldLabel
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription


import com.lo.imagine.ui.theme.PopRadius
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lo.imagine.ui.PIcon
import com.lo.imagine.data.ApiResult
import com.lo.imagine.data.PolishDepth
import com.lo.imagine.data.PolishTemplate
import com.lo.imagine.data.*
import com.lo.imagine.data.StudioPersist
import com.lo.imagine.data.ArtistPreset
import com.lo.imagine.data.AppSettings
import com.lo.imagine.data.CustomPreset
import com.lo.imagine.data.ASPECT_OPTIONS
import com.lo.imagine.data.QUALITY_TIERS
import com.lo.imagine.data.aspectBySize
import com.lo.imagine.data.STYLE_PRESETS
import com.lo.imagine.data.composeNegative
import com.lo.imagine.data.composePrompt
import com.lo.imagine.data.resolveSize
import com.lo.imagine.data.TaskScheduler
import com.lo.imagine.data.GenerationTasks
import com.lo.imagine.data.ImageRepository
import com.lo.imagine.data.SettingsRepository
import com.lo.imagine.ui.AppBus
import com.lo.imagine.ui.AspectGeometry
import com.lo.imagine.ui.PopAlert
import com.lo.imagine.ui.PopNumericField
import com.lo.imagine.ui.PopEdit
import com.lo.imagine.ui.PopAlertDialog
import com.lo.imagine.ui.PopInspect
import com.lo.imagine.ui.PopIconButton
import com.lo.imagine.ui.PopBusySpinner
import com.lo.imagine.ui.PopRefresh
import com.lo.imagine.ui.PopSpark
import com.lo.imagine.ui.PopSwitch
import com.lo.imagine.ui.PopTextField
import com.lo.imagine.ui.PopBack
import com.lo.imagine.ui.PopTrash
import com.lo.imagine.ui.PopTranslate
import com.lo.imagine.ui.PopWand
import com.lo.imagine.ui.PopChevronDown
import com.lo.imagine.ui.PopClose
import com.lo.imagine.ui.PopChevronUp
// popBackdrop 已由壳层 ArkPageBackdrop 接管
import com.lo.imagine.ui.popStyleIcon
import com.lo.imagine.ui.celInk
import com.lo.imagine.ui.CreativeBusyIcon
import com.lo.imagine.ui.DropdownField
import com.lo.imagine.ui.ErrorPanel
import com.lo.imagine.ui.PopErrorDialog
import com.lo.imagine.ui.GenerationStatus
import com.lo.imagine.ui.HorizontalChips
import com.lo.imagine.ui.ImageResultCard
import com.lo.imagine.ui.LoadingLabel
import com.lo.imagine.ui.Panel
import com.lo.imagine.ui.PreviewStore
import com.lo.imagine.ui.SectionTitle
import com.lo.imagine.ui.ScreenHeader
import com.lo.imagine.ui.StudioState
import com.lo.imagine.ui.StudioMode
import com.lo.imagine.ui.StudioModeSwitch
import com.lo.imagine.ui.TinyBadge
import com.lo.imagine.ui.theme.Forest
import com.lo.imagine.ui.theme.Ink
import com.lo.imagine.ui.theme.LocalPopAccents
import com.lo.imagine.ui.theme.Moss
import com.lo.imagine.ui.theme.Signal
import com.lo.imagine.ui.theme.themedCorner

import com.lo.imagine.ui.ArkBlockAction
import com.lo.imagine.ui.ArkPresetDialog
import com.lo.imagine.ui.ArkGenerateBar
import com.lo.imagine.ui.ArkInkPanel
import com.lo.imagine.ui.ArkTileButton
import com.lo.imagine.ui.ArkTranslateGlyph
import com.lo.imagine.ui.theme.ArkRef
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import com.lo.imagine.util.ImageUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/** 首页提示词历史栈：支持「撤回上一步」。打字停顿、翻译、润色应用前都会留一份快照。 */
private object StudioPromptHistory {
    private val stack = ArrayDeque<String>()

    fun push(value: String) {
        if (value.isBlank() || stack.lastOrNull() == value) return
        stack.addLast(value)
        while (stack.size > 40) stack.removeFirst()
    }

    fun pop(): String? = stack.removeLastOrNull()
}

@Composable
fun StudioScreen(
    settings: AppSettings,
    repository: ImageRepository,
    settingsRepository: SettingsRepository,
    onPreview: () -> Unit,
    /** 进入 NAI 页：直接导航，不走 AppBus（全局可变状态会因「值没变化」而不触发导航） */
    onOpenNai: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var promptEnhanced by rememberState(false)
    var polishing by rememberState(false)
    var openingResult by rememberState(false)
    var reverseOpen by rememberState(false)
    var showStyleDialog by rememberState(false)
    var translating by rememberState(false)
    var polishTuningOpen by rememberState(false)
    var negativeMenuOpen by rememberState(false)
    var negativeDialogOpen by remember { mutableStateOf(false) }
    var editingNegativeOriginal by remember { mutableStateOf<String?>(null) }
    var negativeDialogName by remember { mutableStateOf("") }
    var negativeDialogText by remember { mutableStateOf("") }
    /** 翻译对：(原文, 译文)。再次点击时在两者间翻转；用户手动编辑后清空 */
    var translationPair by remember { mutableStateOf<Pair<String, String>?>(null) }
    var modelPickerOpen by remember { mutableStateOf(false) }
    /** 撤回：打字停顿后把上一版记进历史，撤回才有东西可回（与 NAI 页各自独立） */
    var lastPrompt by remember { mutableStateOf(StudioState.prompt) }
    LaunchedEffect(StudioState.prompt) {
        delay(900)
        if (StudioState.prompt != lastPrompt) {
            StudioPromptHistory.push(lastPrompt)
            lastPrompt = StudioState.prompt
        }
    }
    var presetSaveHint by remember { mutableStateOf<String?>(null) }

    val selectedStyle = STYLE_PRESETS.firstOrNull { it.id == StudioState.styleId } ?: STYLE_PRESETS.first()
    val selectedQuality = QUALITY_TIERS.firstOrNull { it.id == StudioState.qualityId } ?: QUALITY_TIERS[1]
    val currentAspect = ASPECT_OPTIONS.firstOrNull { it.label == StudioState.aspectLabel }
        ?: aspectBySize(StudioState.size)
        ?: ASPECT_OPTIONS.first()
    var homeParallel by remember(settings.maxParallel) {
        mutableIntStateOf(settings.maxParallel.coerceIn(1, 4))
    }
    var homeParallelText by remember(settings.maxParallel) {
        mutableStateOf(settings.maxParallel.coerceIn(1, 4).toString())
    }
    var homeParallelFocused by remember { mutableStateOf(false) }
    var countText by remember { mutableStateOf(StudioState.count.toString()) }
    var countFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val negativePresets by settingsRepository.negativePresetsFlow().collectAsState(initial = emptyList())
    val customPresets by settingsRepository.customPresetsFlow().collectAsState(initial = emptyList())
    val activePresetName by settingsRepository.activePresetFlow().collectAsState(initial = null)
    /** 本地乐观选中预设的唯一键（name|baseUrl）：点击即高亮，不依赖 DataStore 回流 */
    var pickedPresetKey by remember { mutableStateOf<String?>(null) }
    // 冷启动/设置页改动后：用持久化的 active 预设名回填本地选中
    LaunchedEffect(activePresetName, customPresets) {
        val name = activePresetName?.trim()
        pickedPresetKey = if (name == null) null
        else customPresets.firstOrNull { it.name.trim() == name }
            ?.let { it.name.trim() + "|" + it.baseUrl } ?: pickedPresetKey
    }

    // 冷启动恢复创作参数（进程内只恢复一次，避免覆盖用户正在调的值）
    LaunchedEffect(Unit) {
        if (!StudioState.paramsRestored) {
            StudioState.paramsRestored = true
            settingsRepository.studioStateFlow().firstOrNull()?.let { s ->
                if (StudioState.prompt.isBlank()) StudioState.prompt = s.prompt
                if (StudioState.negative.isBlank()) StudioState.negative = s.negative
                StudioState.styleId = s.styleId
                StudioState.qualityId = s.qualityId
                StudioState.size = s.size
                // 旧存档没有 aspectLabel：按 size 反推一次完成迁移；失败则回退 1:1
                StudioState.aspectLabel = s.aspectLabel.ifBlank {
                    aspectBySize(s.size)?.label ?: "1:1"
                }
                StudioState.count = s.count
                StudioState.safeNegative = s.safeNegative
                StudioState.naiOptions = s.naiOptions ?: NaiOptions()
                StudioState.naiMode = false
                StudioState.naiOptions = StudioState.naiOptions.copy(modeEnabled = false)
                StudioState.polishDepth = PolishDepth.entries.firstOrNull { it.id == s.polishDepthId }
                    ?: PolishDepth.MEDIUM
                StudioState.polishTemplate = PolishTemplate.entries.firstOrNull { it.id == s.polishTemplateId }
                    ?: PolishTemplate.AUTO
            }
        }
    }

    // 参数防抖持久化：停止调整 600ms 后写盘，杀进程重进自动恢复
    LaunchedEffect(
        StudioState.prompt,
        StudioState.negative,
        StudioState.styleId,
        StudioState.qualityId,
        StudioState.size,
        StudioState.count,
        StudioState.safeNegative,
        StudioState.polishDepth,
        StudioState.polishTemplate,
        StudioState.naiOptions,
        StudioState.naiMode
    ) {
        delay(600)
        settingsRepository.saveStudioState(
            StudioPersist(
                prompt = StudioState.prompt,
                negative = StudioState.negative,
                styleId = StudioState.styleId,
                qualityId = StudioState.qualityId,
                size = StudioState.size,
                aspectLabel = StudioState.aspectLabel,
                count = StudioState.count,
                safeNegative = StudioState.safeNegative,
                polishDepthId = StudioState.polishDepth.id,
                polishTemplateId = StudioState.polishTemplate.id,
                naiOptions = StudioState.naiOptions,
                naiMode = StudioState.naiMode
            )
        )
    }

    fun applyCustomPreset(preset: CustomPreset) {
        modelPickerOpen = false
        // 本地乐观选中：立即高亮/更新角标，不等 DataStore 回流（避免“要点两次”）
        pickedPresetKey = preset.name.trim() + "|" + preset.baseUrl
        scope.launch {
            settingsRepository.save(
                settings.copy(
                    baseUrl = preset.baseUrl,
                    apiKey = preset.apiKey,
                    model = preset.model,
                    editMode = preset.editMode
                )
            )
            settingsRepository.saveActivePreset(preset.name)
        }
    }


    fun setHomeParallel(value: Int) {
        val next = value.coerceIn(1, 4)
        homeParallel = next
        TaskScheduler.configure(next)
        scope.launch { settingsRepository.save(settings.copy(maxParallel = next)) }
    }

    fun commitCount() {
        val next = countText.toIntOrNull()?.coerceIn(1, 4) ?: 1
        countText = next.toString()
        StudioState.count = next
    }

    fun commitHomeParallel() {
        val next = homeParallelText.toIntOrNull()?.coerceIn(1, 4) ?: 1
        homeParallelText = next.toString()
        setHomeParallel(next)
    }

    LaunchedEffect(StudioState.count, countFocused) {
        if (!countFocused) countText = StudioState.count.coerceIn(1, 4).toString()
    }

    LaunchedEffect(homeParallel, homeParallelFocused) {
        if (!homeParallelFocused) homeParallelText = homeParallel.coerceIn(1, 4).toString()
    }

    LaunchedEffect(StudioState.loading) {
        while (StudioState.loading) {
            StudioState.elapsed = ((System.currentTimeMillis() - StudioState.startedAt) / 1000).toInt().coerceAtLeast(0)
            delay(500)
        }
    }

    fun generate() {
        val raw = StudioState.prompt.trim()
        if (raw.isBlank() || StudioState.loading) return
        com.lo.imagine.data.imageApiConfigurationError(settings)?.let {
            StudioState.error = it
            return
        }
        val naiProfile = resolveNaiProfile(settings.genModel, StudioState.naiOptions)
        if (isNaiModel(settings.genModel) && StudioState.naiOptions.profileId == "auto" && naiProfile == null) {
            StudioState.error = "无法确认 NAI 版本，请在 NAI 提示词工程中选择 4.5 / 5，或关闭专用适配。"
            return
        }
        val nai = naiProfile?.let {
            // 普通模式的 NAI 兼容分支：无画师串来源（画师串只属于 NAI 工作台），传空串
            assembleNaiPrompt(raw, "", StudioState.negative, selectedStyle, it, StudioState.naiOptions)
        }
        if (nai != null && nai.errors.isNotEmpty()) {
            StudioState.error = nai.errors.joinToString("\n")
            return
        }
        StudioState.loading = true
        StudioState.genActive = true
        StudioState.startedAt = System.currentTimeMillis()
        StudioState.elapsed = 0
        StudioState.error = null
        StudioState.results = emptyList()
        // 画师串只属于 NAI 通道（NAI 页有自己的 c.artists 面板）：
        // 普通模式绝不拼接任何画师串——旧版曾把持久化的画师串注入首页生成，
        // 用户已无该入口，残留值只会污染提示词。
        StudioState.resultPrompt = nai?.positive
            ?: composePrompt(raw, selectedStyle, selectedQuality, settings.genModel)
        StudioState.resultModel = settings.genModel
        val finalSize = resolveSize(currentAspect, selectedQuality)
        val totalCount = StudioState.count
        val finalPrompt = StudioState.resultPrompt
        val finalNeg = if (nai != null) nai.negative else composeNegative(
            StudioState.negative, selectedStyle, StudioState.safeNegative
        )

        com.lo.imagine.data.GenerationTasks.launch(
            context = context,
            doneTitle = "创作完成",
            failTitle = "创作失败",
            work = {
                var summary = ""
                var taskOk = false
                // 画幅守卫：上游输出比例与请求严重不符（>20%）时记录首次出现的尺寸，
                // 生成完成后弹一次性提示——模型/服务商不支持所选画幅是模型端行为，不是 app 出错
                var ratioMismatch: String? = null
                try {
                    val tasks = List(totalCount) {
                        suspend {
                            repository.generate(
                                settings = settings,
                                prompt = finalPrompt,
                                negativePrompt = finalNeg,
                                size = finalSize,
                                count = 1
                            )
                        }
                    }
                    // 流式回调：每张图一回来就追加到 results，用户能看到「一张张出来」
                    val errs = mutableListOf<String>()
                    try {
                        TaskScheduler.parallelStream(tasks, isActive = { StudioState.genActive }) { _, r ->
                            // 用户已取消且该任务未开始（被调度器跳过）：不计入失败，直接忽略
                            if (r.exceptionOrNull() is kotlinx.coroutines.CancellationException) return@parallelStream
                            val res = r.getOrNull()
                            if (res is ApiResult.Success) {
                                // 画质兜底：解码 → 若上游忽略了 size（实测企鹅会无视），放大到所选档位，
                                // 并用放大后的位图回填 results / 落库，保证预览、保存、作品库全部一致。
                                val sizeParts = finalSize.split("x")
                                val targetW = sizeParts.getOrNull(0)?.toIntOrNull() ?: 0
                                val targetH = sizeParts.getOrNull(1)?.toIntOrNull() ?: 0
                                res.images.forEach { img ->
                                    val rawBitmap = repository.resolveBitmap(img)
                                    val finalBitmap = if (rawBitmap != null && !settings.upscaleEnabled) {
                                        rawBitmap
                                    } else {
                                        ImageUtils.ensureResolution(rawBitmap, targetW, targetH)
                                    }
                                    val upstreamNote = rawBitmap?.let { "${it.width}x${it.height}" }
                                    // 比例守卫：与 ensureResolution 的「严重偏差」阈值一致（>20%），
                                    // 该分支会保持上游原比例输出 → 用户拿到的图不是所选画幅，必须让他知道原因
                                    if (rawBitmap != null && targetW > 0 && targetH > 0 && ratioMismatch == null) {
                                        val srcRatio = rawBitmap.width.toFloat() / rawBitmap.height
                                        val tgtRatio = targetW.toFloat() / targetH
                                        if (kotlin.math.abs(srcRatio - tgtRatio) / tgtRatio > 0.20f) {
                                            ratioMismatch = upstreamNote
                                        }
                                    }
                                    if (rawBitmap != null) {
                                        android.util.Log.i(
                                            "ImagineHttp",
                                            "upstream $upstreamNote → target ${targetW}x${targetH}" +
                                                if (finalBitmap !== rawBitmap) " (已补齐)" else ""
                                        )
                                    }
                                    if (rawBitmap != null && finalBitmap !== rawBitmap) {
                                        val upscaledB64 = android.util.Base64.encodeToString(
                                            ImageUtils.bitmapToJpegBytes(finalBitmap),
                                            android.util.Base64.NO_WRAP
                                        )
                                        // url 一并置空：结果卡 url 优先渲染，留着会让结果区显示
                                        // 上游原图而非放大补齐后的版本，与作品库不一致
                                        StudioState.results = StudioState.results +
                                            img.copy(b64Json = upscaledB64, url = null, upstreamSize = upstreamNote)
                                        ImageUtils.archiveResult(
                                            context = context,
                                            bitmap = finalBitmap,
                                            prompt = finalPrompt,
                                            model = settings.genModel,
                                            kind = "gen",
                                            elapsedSec = (System.currentTimeMillis() - StudioState.startedAt) / 1000,
                                            toGallery = settings.autoSaveGallery
                                        )
                                    } else if (rawBitmap != null) {
                                        StudioState.results = StudioState.results + img.copy(upstreamSize = upstreamNote)
                                        ImageUtils.archiveResult(
                                            context = context,
                                            bitmap = finalBitmap,
                                            prompt = finalPrompt,
                                            model = settings.genModel,
                                            kind = "gen",
                                            elapsedSec = (System.currentTimeMillis() - StudioState.startedAt) / 1000,
                                            toGallery = settings.autoSaveGallery
                                        )
                                    } else {
                                        // 成功但没有可解码图片（返回空 images / b64 与 url 双空）：
                                        // 按失败计，不能让「成功却无图」静默溜走
                                        errs.add("上游返回了成功状态但没有图片数据")
                                    }
                                }
                                if (res.images.isEmpty()) {
                                    // 成功状态但 images 列表为空：同样按失败计，防止通知误报成功
                                    errs.add("上游未返回任何图片（响应可能被截断或额度不足）")
                                }
                            } else {
                                // 关键修复：ApiResult.Error（HTTP/上游错误）此前既不是 Success 也不是 null，
                                // 两个旧分支都不命中 → 错误被吞 → 汇总误报「全部成功」
                                val msg = (res as? ApiResult.Error)?.message
                                    ?: r.exceptionOrNull()?.message
                                    ?: "生成失败"
                                errs.add(msg)
                            }
                        }
                    } catch (e: Exception) {
                        // 单图落库/解码抛出的异常会从 awaitAll 冒出：归入失败，不让任务无声死亡
                        errs.add(e.message ?: "结果处理失败")
                    }
                    summary = if (!StudioState.genActive) {
                        "已取消 · 已完成 ${StudioState.results.size} 张，未开始的请求已跳过"
                    } else when {
                        StudioState.results.isNotEmpty() && errs.isNotEmpty() ->
                            "${errs.size} 张失败 · 其余 ${StudioState.results.size} 张已存入作品库"
                        errs.isNotEmpty() -> "全部失败：${errs.firstOrNull() ?: "未知错误"}"
                        else -> "全部成功 · 已存入作品库"
                    }
                    // 显式成败：零失败且真的出了图才算成功
                    taskOk = StudioState.genActive && errs.isEmpty() && StudioState.results.isNotEmpty()
                    // 画幅不符一次性提示：上游无视了 size 请求（模型/服务商不支持该画幅），
                    // 图已正常保存但比例不是所选的——必须让用户知道原因与出路，而不是让他以为 app 出 bug
                    if (ratioMismatch != null && taskOk && !StudioState.aspectWarnShown) {
                        StudioState.aspectWarnShown = true
                        val near = nearestRatioLabel(ratioMismatch ?: "")
                        StudioState.error =
                            "画幅提示（图已正常保存）：你选的是 ${currentAspect.label}（$finalSize），" +
                                "但当前模型/服务商不支持该尺寸，上游按自身支持的 $near（${ratioMismatch}）输出。" +
                                "想要 ${currentAspect.label} 请换支持任意分辨率的模型（如 NovelAI、自建 FLUX 端点、Grok），或改用相近画幅。"
                    }
                    // 全部失败时用弹窗完整展示报错；部分失败也弹，给出失败原因（用户主动取消不弹）
                    if (errs.isNotEmpty() && StudioState.genActive) {
                        StudioState.error = if (StudioState.results.isEmpty()) {
                            errs.firstOrNull() ?: "生成失败"
                        } else {
                            "部分失败（${errs.size}/${totalCount}）：${errs.firstOrNull() ?: "未知错误"}"
                        }
                    }
                } finally {
                    // 无论中途抛出什么异常，loading 必须复位，否则按钮永久转圈
                    StudioState.loading = false
                    StudioState.genActive = false
                    PreviewStore.elapsedText = ImageUtils.formatElapsed((System.currentTimeMillis() - StudioState.startedAt) / 1000)
                }
                com.lo.imagine.data.TaskOutcome(summary, taskOk)
            }
        )
    }

    /** 用户主动取消生成：等待中的请求不再发起，在途请求正常跑完（结果照常入库） */
    fun cancelGeneration() {
        StudioState.genActive = false
    }

    fun polish() {
        if (StudioState.prompt.isBlank() || polishing) return
        polishing = true
        scope.launch {
            repository.polishPrompt(
                settings,
                StudioState.prompt,
                "gen",
                depth = StudioState.polishDepth,
                template = StudioState.polishTemplate,
                naiProfile = resolveNaiProfile(settings.genModel, StudioState.naiOptions),
                // 画师串由应用在生成时注入，润色时绝不带上下文——LLM 会回显画师串污染输出
                artistContext = "",
                styleContext = if (StudioState.naiOptions.styleEnabled) selectedStyle.suffix else "",
                aspectLabel = currentAspect.label,
                aspectOrientation = when {
                    currentAspect.ratioW > currentAspect.ratioH * 1.05f -> "landscape"
                    currentAspect.ratioH > currentAspect.ratioW * 1.05f -> "portrait"
                    else -> "square"
                }
            )
                .onSuccess { polished ->
                    // 应用润色前记录当前版本，撤回有东西可回
                    StudioPromptHistory.push(StudioState.prompt)
                    StudioState.prompt = polished
                    lastPrompt = polished
                    promptEnhanced = true
                }
                .onFailure { e ->
                    StudioState.error = e.message ?: "润色失败"
                }
            polishing = false
        }
    }

    // 中英互译：第一次点击把当前文本翻译成另一语言并记住原文；
    // 之后再点则在原文/译文之间翻转。用户手动编辑提示词后清空翻译对。
    fun toggleTranslate() {
        val text = StudioState.prompt.trim()
        if (text.isBlank() || translating) return
        val pair = translationPair
        if (pair != null) {
            StudioPromptHistory.push(StudioState.prompt)
            StudioState.prompt =
                if (StudioState.prompt == pair.first) pair.second else pair.first
            lastPrompt = StudioState.prompt
            promptEnhanced = false
            return
        }
        translating = true
        scope.launch {
            repository.translateText(settings, text)
                .onSuccess { translated ->
                    if (translated.isNotBlank()) {
                        translationPair = text to translated
                        StudioPromptHistory.push(StudioState.prompt)
                        StudioState.prompt = translated
                        lastPrompt = translated
                        promptEnhanced = false
                    }
                }
                .onFailure { e -> StudioState.error = e.message ?: "翻译失败" }
            translating = false
        }
    }

    /** 撤回：弹出历史栈上一版，恢复到提示词框（与 NAI 页各自独立的历史） */
    fun undoPrompt() {
        val prev = StudioPromptHistory.pop()
        if (prev == null) {
            StudioState.error = "没有可撤回的修改"
            return
        }
        lastPrompt = prev
        StudioState.prompt = prev
        promptEnhanced = false
        translationPair = null
    }

    fun openResult(data: com.lo.imagine.data.ImageData) {
        if (openingResult) return // 解码期间忽略连点，避免重复 decode
        openingResult = true
        scope.launch {
            val bitmap = repository.resolveBitmap(data)
            openingResult = false
            if (bitmap != null) {
                PreviewStore.bitmap = bitmap
                PreviewStore.prompt = StudioState.resultPrompt
                PreviewStore.model = settings.genModel
                PreviewStore.sizeNote = data.upstreamSize
                PreviewStore.historyList = null // 创作/修图入口不支持左右切换
                onPreview()
            } else {
                StudioState.error = "图片加载失败，请稍后重试"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
                Surface(
                color = Color.Transparent,
                shadowElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                ArkGenerateBar(
                    loading = StudioState.loading,
                    enabled = StudioState.prompt.isNotBlank() || StudioState.loading,
                    label = if (StudioState.loading) "${StudioState.elapsed}s · ${StudioState.results.size}/${StudioState.count} · 点此取消" else "生成 ${StudioState.count} 张作品",
                    sub = if (StudioState.loading) "WAITING / TAP TO CANCEL" else "GENERATE",
                    onClick = { if (StudioState.loading) cancelGeneration() else generate() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = RefUiTokens.pageInset, vertical = 14.dp).heightIn(min = RefUiTokens.actionHeight)
                )
            }
        }
    ) { scaffoldPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPadding)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .pointerInput(Unit) {
                // 点击空白处收起键盘并释放输入框焦点（否则张数/并发输入框光标一直闪烁）
                detectTapGestures { focusManager.clearFocus() }
            }
            .padding(bottom = 12.dp)
    ) {
        ScreenHeader(
            title = "标准",
            subtitle = null,
            // 左上角标题即模式切换：点「NAI」进工作台
            titleOverride = {
                StudioModeSwitch(selected = StudioMode.NORMAL, onSelect = { mode ->
                    if (mode == StudioMode.NAI) onOpenNai()
                })
            },
            action = {
                val pickedName = pickedPresetKey?.substringBefore('|')
                val activeName = (pickedName ?: activePresetName)?.trim()
                ArkBlockAction(
                    icon = com.lo.imagine.R.drawable.ic_ark_transfer,
                    cn = activeName ?: "中转",
                    en = "TRANSFER",
                    onClick = { modelPickerOpen = true }
                )
            }
        )

        if (modelPickerOpen) {
            ArkPresetDialog(
                title = "切换模型预设",
                hint = "首页通道：只切换普通模式的绘图接口，NAI 工作台用自己的配置，互不影响。",
                emptyText = "还没有保存的自定义 API 预设，请先到设置页创建。",
                isEmpty = customPresets.isEmpty(),
                onDismiss = { modelPickerOpen = false }
            ) {
                customPresets
                    .sortedWith(compareByDescending<CustomPreset> { it.fav }.thenBy { it.name })
                    .forEach { preset ->
                        val selected = pickedPresetKey == preset.name.trim() + "|" + preset.baseUrl
                        Surface(
                            onClick = { applyCustomPreset(preset) },
                            shape = com.lo.imagine.ui.theme.themedShape(PopRadius.field),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .62f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val displayLabel = preset.name.trim().ifEmpty {
                                        android.net.Uri.parse(preset.baseUrl).host ?: "未命名预设"
                                    }
                                    val detailHost = android.net.Uri.parse(preset.baseUrl).host ?: ""
                                    Text(
                                        displayLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        if (detailHost.isBlank()) preset.model.ifBlank { "未设模型" }
                                        else "$detailHost · ${preset.model.ifBlank { "未设模型" }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (selected) {
                                    PIcon(
                                        com.lo.imagine.ui.PopCheck,
                                        contentDescription = "当前预设",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
            }
        }

        if (showStyleDialog) {
                StylePickerDialog(
                    selectedId = StudioState.styleId,
                    onPick = { picked ->
                        StudioState.styleId = picked
                        showStyleDialog = false
                    },
                    onDismiss = { showStyleDialog = false }
                )
            }

            if (polishTuningOpen) {
                PolishTuningDialog(onDismiss = { polishTuningOpen = false })
            }

        ArkInkPanel(modifier = Modifier.padding(horizontal = 18.dp)) {
            RefPromptHeading {
                Spacer(Modifier.width(6.dp))
                RefOutlineAction(RefIcons.Sliders, "深度", "润色深度：${StudioState.polishDepth.label}",
                    onClick = { polishTuningOpen = true })
                Spacer(Modifier.width(6.dp))
                RefOutlineAction(RefIcons.Style, selectedStyle.label, "选择风格：${selectedStyle.label}",
                    onClick = { showStyleDialog = true })
            }
            // 生成中：整个提示词编辑区（操作行 + 输入框）收起——等待出图时不需要整段文字占屏
            if (!StudioState.loading) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ArkTileButton(
                        cn = "撤回",
                        en = "UNDO",
                        icon = com.lo.imagine.R.drawable.ic_ark_undo,
                        onClick = { undoPrompt() },
                        enabled = StudioState.prompt.isNotBlank() && !polishing && !translating,
                        modifier = Modifier.weight(1f).heightIn(min = RefUiTokens.tileHeight)
                    )
                    ArkTileButton(
                        cn = when {
                            translating -> "翻译中"
                            translationPair == null -> "翻译"
                            StudioState.prompt == translationPair?.second -> "译回原文"
                            else -> "翻译"
                        },
                        en = "TRANSLATE",
                        glyph = { ArkTranslateGlyph() },
                        onClick = { toggleTranslate() },
                        enabled = StudioState.prompt.isNotBlank() && !translating,
                        modifier = Modifier.weight(1f).heightIn(min = RefUiTokens.tileHeight)
                    )
                    ArkTileButton(
                        cn = "图像",
                        en = "IMAGE",
                        icon = com.lo.imagine.R.drawable.ic_ark_image,
                        onClick = { reverseOpen = true },
                        enabled = !polishing,
                        modifier = Modifier.weight(1f).heightIn(min = RefUiTokens.tileHeight)
                    )
                    ArkTileButton(
                        cn = if (polishing) "润色中" else "润色",
                        en = "ENHANCE",
                        glyph = { PIcon(RefIcons.Wand, null, Modifier.size(RefUiTokens.tileIcon).padding(1.dp)) },
                        onClick = { polish() },
                        enabled = StudioState.prompt.isNotBlank() && !polishing,
                        modifier = Modifier.weight(1f).heightIn(min = RefUiTokens.tileHeight)
                    )
                    ArkTileButton(
                        cn = "清空",
                        en = "CLEAR",
                        icon = com.lo.imagine.R.drawable.ic_ark_trash,
                        onClick = {
                            StudioState.prompt = ""
                            promptEnhanced = false
                            translationPair = null
                        },
                        enabled = StudioState.prompt.isNotBlank() && !polishing,
                        modifier = Modifier.weight(1f).heightIn(min = RefUiTokens.tileHeight)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(com.lo.imagine.ui.theme.themedShape(PopRadius.field))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .border(.8.dp, MaterialTheme.colorScheme.outlineVariant, com.lo.imagine.ui.theme.themedShape(PopRadius.field))
                ) {
                    BasicTextField(
                        value = StudioState.prompt,
                        onValueChange = {
                            StudioState.prompt = it
                            promptEnhanced = false
                            translationPair = null
                        },
                        enabled = !polishing,
                        textStyle = LocalTextStyle.current.merge(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                if (StudioState.prompt.isEmpty()) {
                                    Text(
                                        "例如：雨夜东京街头，一只白猫站在霓虹灯下…",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                                inner()
                            }
                        },
                        minLines = 3,
                        maxLines = 7,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "画面描述" }
                            .drawWithContent {
                                drawContent()
                                if (polishing) drawRect(Color.Black.copy(alpha = .55f))
                            }
                    )

                    if (polishing) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable(enabled = false) { },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(
                                        ArkRef.ink.copy(alpha = .94f),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 11.dp)
                            ) {
                                PopBusySpinner(modifier = Modifier.size(20.dp))
                                Spacer(Modifier.size(9.dp))
                                Text(
                                    "提示词润色中……",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ArkRef.inkText
                                )
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${StudioState.prompt.length} / 2000", fontSize = 10.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (promptEnhanced) { Spacer(Modifier.width(8.dp)); TinyBadge("已润色", accent = true) }
                    }
                    Text("TEXT INPUT", fontSize = 8.sp, lineHeight = 11.sp, letterSpacing = .2.em, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // 润色完成状态在输入框统计行显示，避免挤压标题与操作入口。
        }

        if (negativeDialogOpen) {
                PopAlertDialog(
                    title = if (editingNegativeOriginal == null) "新增负面词预设" else "编辑负面词预设",
                    onDismissRequest = { negativeDialogOpen = false },
                    text = {
                        Column {
                            PopTextField(
                                value = negativeDialogName,
                                onValueChange = { negativeDialogName = it },
                                label = "名称",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            PopTextField(
                                value = negativeDialogText,
                                onValueChange = { negativeDialogText = it },
                                label = "负面词内容",
                                placeholder = "lowres, bad hands, watermark…",
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (negativePresets.any {
                                    it.name == negativeDialogName.trim() && it.name != editingNegativeOriginal
                                }
                            ) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "该名称已被使用",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    confirmLabel = "保存",
                    confirmEnabled = negativeDialogName.isNotBlank() && negativeDialogText.isNotBlank(),
                    onConfirm = {
                        val name = negativeDialogName.trim()
                        val content = negativeDialogText.trim()
                        if (name.isBlank() || content.isBlank()) return@PopAlertDialog
                        val next = negativePresets
                            .filterNot { it.name == name || it.name == editingNegativeOriginal }
                            .plus(ArtistPreset(name, content))
                        scope.launch { settingsRepository.saveNegativePresets(next) }
                        StudioState.negative = content
                        negativeDialogOpen = false
                    },
                    dismissLabel = "取消",
                    onDismiss = { negativeDialogOpen = false },
                    extraActions = {
                        if (editingNegativeOriginal != null) {
                            TextButton(onClick = {
                                scope.launch {
                                    settingsRepository.saveNegativePresets(
                                        negativePresets.filterNot { it.name == editingNegativeOriginal }
                                    )
                                }
                                negativeDialogOpen = false
                            }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                )
            }

            Spacer(Modifier.height(16.dp))
            Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RefSectionHeading("输出设置", "OUTPUT SETTINGS", Modifier.weight(1f))
                    RefResolutionBadge(resolveSize(currentAspect, selectedQuality).replace('x', '×'))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RefFieldLabel("画幅", "ASPECT RATIO")
                        DropdownField(
                            selected = currentAspect.label,
                            options = ASPECT_OPTIONS.map { it.label },
                            fieldLabel = "画幅",
                            leadingContent = { AspectGeometry(currentAspect) },
                            optionLeadingContent = { label ->
                                ASPECT_OPTIONS.firstOrNull { it.label == label }?.let { AspectGeometry(it) }
                            },
                            onSelect = { label ->
                                ASPECT_OPTIONS.firstOrNull { it.label == label }?.let {
                                    // size 保留标准画幅标识，实际输出分辨率由画质档动态计算。
                                    StudioState.aspectLabel = it.label
                                    StudioState.size = it.size
                                }
                            }
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RefFieldLabel("画质", "QUALITY")
                        DropdownField(
                            selected = selectedQuality.label,
                            options = QUALITY_TIERS.map { it.label },
                            fieldLabel = "画质",
                            leadingContent = { PIcon(RefIcons.Layers, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary) },
                            optionLeadingContent = { PIcon(RefIcons.Layers, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
                            menuHeight = 200,
                            onSelect = { label ->
                                QUALITY_TIERS.firstOrNull { it.label == label }?.let {
                                    StudioState.qualityId = it.id
                                }
                            }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RefFieldLabel("张数", "QUANTITY")
                        PopNumericField(
                            value = countText,
                            label = "张数",
                            onValueChange = { value -> countText = value.filter { it.isDigit() } },
                            onCommit = { commitCount() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RefFieldLabel("并发", "CONCURRENCY")
                        PopNumericField(
                            value = homeParallelText,
                            label = "并发",
                            onValueChange = { value -> homeParallelText = value.filter { it.isDigit() } },
                            onCommit = { commitHomeParallel() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            if (StudioState.loading) {
                GenerationStatus(
                    elapsedSeconds = StudioState.elapsed,
                    mode = "create",
                    completed = StudioState.results.size,
                    total = StudioState.count
                )
                Spacer(Modifier.height(12.dp))
            }
            if (StudioState.error != null) {
                PopErrorDialog(
                    message = StudioState.error ?: "",
                    title = "生成失败",
                    onDismiss = { StudioState.error = null }
                )
            }

            // 主操作固定在底部拇指区，滚动页面时始终可达。

            if (StudioState.results.isNotEmpty()) {
                SectionTitle(
                    "生成结果",
                    null,
                    action = { TinyBadge("${StudioState.results.size} 张") }
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (StudioState.results.size > 2) 380.dp else 190.dp)
                ) {
                    items(StudioState.results) { data ->
                        ImageResultCard(
                            data = data,
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                            onClick = { openResult(data) }
                        )
                    }
                }
            }
        }

                if (reverseOpen) {
            ReversePromptDialog(
                settings = settings,
                repository = repository,
                onApply = { p ->
                    StudioState.prompt = p
                    promptEnhanced = true
                },
                onDismiss = { reverseOpen = false }
            )
        }
    }
    }
}

@Composable
internal fun StudioPromptAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = com.lo.imagine.ui.theme.themedShape(PopRadius.field),
        color = if (accent) LocalPopAccents.current.a else MaterialTheme.colorScheme.surface,
        contentColor = if (accent) com.lo.imagine.ui.theme.Ink else MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = if (accent) 1.dp else 0.dp,
        border = BorderStroke(
            1.dp,
            if (accent) com.lo.imagine.ui.theme.Ink
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .62f)
        ),
        modifier = modifier.height(42.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 5.dp)
        ) {
            PIcon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(5.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

/** 润色调校弹窗：深度三档 + 按目标模型的输出模板，点选即生效。 */
@Composable
private fun PolishTuningDialog(onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = com.lo.imagine.ui.theme.themedShape(PopRadius.sheet),
            border = BorderStroke(2.dp, celInk()),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 26.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "润色调校",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "润色深度 · 决定改动力度",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                PolishDepth.entries.forEach { d ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                StudioState.polishDepth = d
                                onDismiss()
                            }
                            .padding(vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                d.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (d == StudioState.polishDepth)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                d.desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (d == StudioState.polishDepth) {
                            Text(
                                "✓",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "输出模板 · 跟随目标模型的提示词最佳实践",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                PolishTemplate.entries.forEach { t ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                StudioState.polishTemplate = t
                                onDismiss()
                            }
                            .padding(vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                t.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (t == StudioState.polishTemplate)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                t.desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (t == StudioState.polishTemplate) {
                            Text(
                                "✓",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StylePickerDialog(
    selectedId: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = com.lo.imagine.ui.theme.themedShape(PopRadius.sheet),
            border = BorderStroke(2.dp, celInk()),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 26.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "风格库",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${STYLE_PRESETS.size} 种风格 · 点选即应用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.fillMaxWidth().height(420.dp)
                ) {
                    items(STYLE_PRESETS) { s ->
                        val selected = s.id == selectedId
                        Surface(
                            onClick = { onPick(s.id) },
                            shape = com.lo.imagine.ui.theme.themedShape(PopRadius.field),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                            tonalElevation = if (selected) 1.dp else 0.dp,
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .35f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)
                            ),
                            modifier = Modifier.fillMaxWidth().height(64.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
                            ) {
                                Surface(
                                    color = if (selected) LocalPopAccents.current.a else MaterialTheme.colorScheme.surface,
                                    contentColor = if (selected) com.lo.imagine.ui.theme.Ink else MaterialTheme.colorScheme.primary,
                                    shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip),
                                    border = BorderStroke(1.dp, if (selected) com.lo.imagine.ui.theme.Ink else MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        PIcon(popStyleIcon(s.id), contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Spacer(Modifier.size(7.dp))
                                Text(
                                    s.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberState(initial: Boolean): androidx.compose.runtime.MutableState<Boolean> =
    androidx.compose.runtime.remember { mutableStateOf(initial) }

/** 把上游实际输出尺寸（如 "1024x576"）翻译成最接近的常见比例名，用于画幅不符提示 */
private fun nearestRatioLabel(size: String): String {
    val parts = size.split("x")
    val w = parts.getOrNull(0)?.toFloatOrNull() ?: return size
    val h = parts.getOrNull(1)?.toFloatOrNull() ?: return size
    if (w <= 0f || h <= 0f) return size
    val r = w / h
    val labels = listOf(
        "1:1" to 1f,
        "4:3" to 4f / 3f, "3:4" to 3f / 4f,
        "16:9" to 16f / 9f, "9:16" to 9f / 16f,
        "3:2" to 3f / 2f, "2:3" to 2f / 3f,
        "21:9" to 21f / 9f, "16:10" to 1.6f
    )
    return labels.minByOrNull { kotlin.math.abs(it.second - r) }?.first
        ?: "约${"%.2f".format(r)}:1"
}
