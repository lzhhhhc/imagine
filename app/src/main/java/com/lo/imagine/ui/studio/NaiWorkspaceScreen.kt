package com.lo.imagine.ui.studio


import androidx.compose.ui.layout.layout
import com.lo.imagine.ui.theme.PopRadius
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import com.lo.imagine.ui.theme.ArkRef
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lo.imagine.data.*
import com.lo.imagine.ui.*
import com.lo.imagine.util.ImageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

/** NAI 会话里的一张图（内存态；落盘仍走作品库 / 系统相册，与首页一致）。 */
data class NaiShot(
    val bitmap: Bitmap,
    val prompt: String,
    val model: String,
    val sizeNote: String,
    /** 本次结果完成解码的时间；内存态结果也要和作品库一样能追溯生成时间。 */
    val createdAt: Long = System.currentTimeMillis()
)

object NaiWorkspaceState {
    var config by mutableStateOf(NaiWorkspaceConfig())
    var ready by mutableStateOf(false)
    /** 由任务自己在协程内置位/复位：作用域被取消、任务根本没起跑时，不会留下永久转圈 */
    var busy by mutableStateOf(false)
    var job by mutableStateOf<Job?>(null)
    /** 进程级作用域：任务不随页面销毁中断——切到别的页面后继续跑完 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** 进程级客户端：与任务同生命周期；取消时由它真正掐掉进行中的 HTTP 连接 */
    val client = NaiNativeClient()
    /** 当前阶段与起始时间：卡住时能直接看出卡在哪一步、卡了多久 */
    var stage by mutableStateOf("")
    var startedAt by mutableStateOf(0L)
    var error by mutableStateOf<String?>(null)
    var result by mutableStateOf<Bitmap?>(null)
    /** 本次会话生成的所有图：NAI 页按相册那套展示（点开预览、长按多选、全选） */
    var shots by mutableStateOf<List<NaiShot>>(emptyList())
    fun addShot(shot: NaiShot) { shots = shots + shot }
    /** 与首页一致：新一批生成前清空上一批，不跨批次累积 */
    fun clearShots() { shots = emptyList() }
    fun removeShots(indices: Set<Int>) { shots = shots.filterIndexed { i, _ -> i !in indices } }
    var resultPrompt by mutableStateOf("")
    var resultModel by mutableStateOf("")
    var usedSeed by mutableStateOf<Long?>(null)
    /** 润色候选文本：放进程级状态，切页回来仍能弹「润色结果」对话框 */
    var polishCandidate by mutableStateOf<String?>(null)

    /** 统一任务入口：同一时刻只允许一个任务；任务跑在进程级作用域，切页不中断。 */
    fun runTask(task: suspend () -> Unit) = runTaskIn(scope, task)

    /** 可注入作用域的入口：单元测试用它验证 busy / error 行为，正式代码走 runTask */
    internal fun runTaskIn(scope: CoroutineScope, task: suspend () -> Unit) {
        if (busy) {
            // 不能静默 return：否则用户只会看到进度条一直在转，以为生成卡死了
            error = "已有任务在等待中（${stage.ifBlank { "处理中" }} · ${((System.currentTimeMillis() - startedAt) / 1000).toInt()}s），点「取消」结束它"
            return
        }
        error = null
        job = scope.launch {
            busy = true
            startedAt = System.currentTimeMillis()
            try {
                task()
            } catch (e: CancellationException) {
                error = "已取消等待"
                throw e
            } catch (e: Exception) {
                error = e.message ?: "请求失败"
            } finally {
                busy = false
                job = null
                stage = ""
            }
        }
    }

    /** 取消等待：协程 + 真正掐掉进行中的 HTTP 连接（execute() 不会被协程取消中断）。 */
    fun cancelWaiting() {
        job?.cancel()
        job = null
        client.cancelActive()
        busy = false
        stage = ""
    }

    /** 页面重新进入时清掉上一次遗留的等待态；没有活着的任务就不该显示进度条。 */
    fun reconcile() {
        if (job?.isActive != true && !client.busy) {
            busy = false
            job = null
            stage = ""
        }
    }
}

/** 粗略 token 估算：按非空白片段计，够用来判断有没有超出 512。 */
private fun naiTokenEstimate(text: String): Int =
    if (text.isBlank()) 0 else Regex("""[A-Za-z0-9]+|[^\sA-Za-z0-9]""").findAll(text).count()

private val NAI_TABS = listOf(
    Triple("生图", "GENERATE", "gen"),
    Triple("预设", "PRESET", "preset"),
    Triple("角色", "CHARACTER", "character"),
    Triple("更多", "MORE", "more")
)

/** 画面提示词的历史栈：支持「撤回上一步」。打字停顿、翻译、润色应用前都会留一份快照。 */
private object NaiPromptHistory {
    private val stack = ArrayDeque<String>()

    fun push(value: String) {
        if (value.isBlank() || stack.lastOrNull() == value) return
        stack.addLast(value)
        while (stack.size > 40) stack.removeFirst()
    }

    fun pop(): String? = stack.removeLastOrNull()
}

@Composable
fun NaiWorkspaceScreen(
    settings: AppSettings,
    settingsRepository: SettingsRepository,
    imageRepository: ImageRepository,
    onSelectMode: (String) -> Unit,
    onPreview: () -> Unit
) {
    val context = LocalContext.current
    val c = NaiWorkspaceState.config
    val scope = rememberCoroutineScope()
    // 与任务共用同一客户端实例：取消时能真正掐掉进行中的 HTTP 连接
    val client = NaiWorkspaceState.client
    val scrollState = rememberScrollState()
    val artistPresets by settingsRepository.artistPresetsFlow().collectAsState(initial = emptyList())
    /** 生图页「选择人物」面板折叠态：人物来源 = 本通道角色卡（tag 角色），点选即切换启用 */
    var characterOpen by remember { mutableStateOf(false) }

    var tab by remember { mutableStateOf("gen") }
    var saveKind by remember { mutableStateOf<String?>(null) }
    var saveName by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    var showRequest by remember { mutableStateOf(false) }
    var artistOpen by remember { mutableStateOf(false) }
    /** 生成参数折叠：摘要行显示当前参数，点开才展开全部控件 */
    var paramsOpen by remember { mutableStateOf(false) }
    var artistDialogOpen by remember { mutableStateOf(false) }
    var editingArtistOriginal by remember { mutableStateOf<String?>(null) }
    var artistDialogName by remember { mutableStateOf("") }
    var artistDialogContent by remember { mutableStateOf("") }
    var cardIndex by remember { mutableIntStateOf(0) }
    /** 页头右上角：NAI 通道的预设选择器（与首页同一份预设列表，各选各的） */
    var presetPickerOpen by remember { mutableStateOf(false) }
    var naiPresets by remember { mutableStateOf<List<CustomPreset>>(emptyList()) }
    var activeNaiPresetName by remember { mutableStateOf<String?>(null) }

    fun update(value: NaiWorkspaceConfig) { NaiWorkspaceState.config = value }

    // 预设：与首页共用同一份保存列表，但 NAI 通道单独记「当前选中」，互不影响
    LaunchedEffect(Unit) {
        naiPresets = settingsRepository.loadCustomPresets()
            .sortedWith(compareByDescending<CustomPreset> { it.fav }.thenBy { it.name })
        activeNaiPresetName = settingsRepository.loadActiveNaiPreset()
    }

    /** 选中预设 = 把它的地址与 Key 应用到 NAI 通道；同时关掉「跟随首页」，两边从此各用各的 */
    fun applyNaiPreset(preset: CustomPreset) {
        val current = NaiWorkspaceState.config
        update(
            current.copy(
                endpoint = preset.baseUrl,
                token = preset.apiKey,
                model = preset.model.trim().ifBlank { current.model },
                useBackendApi = false
            )
        )
        activeNaiPresetName = preset.name
        scope.launch { settingsRepository.saveActiveNaiPreset(preset.name) }
        presetPickerOpen = false
    }
    fun updateCard(index: Int, transform: (NaiCharacterPrompt) -> NaiCharacterPrompt) {
        // 点击时读实时配置：切换「启用」或编辑字段前若有其它未落定的改动，也不能用旧快照覆盖
        val live = NaiWorkspaceState.config
        if (index !in live.characterCards.indices) return
        update(live.copy(characterCards = live.characterCards.mapIndexed { i, v -> if (i == index) transform(v) else v }))
    }
    LaunchedEffect(tab) { scrollState.scrollTo(0) }
    // 进入页面先对账：上一次遗留的等待态不该继续显示进度条
    LaunchedEffect(Unit) { NaiWorkspaceState.reconcile() }
    // 任务跑在进程级作用域（NaiWorkspaceState）：切到别的页面不会中断生成，回来时进度与结果都在；
    // 离开页面不再主动掐连接——只有用户点「取消」或请求自身超时才结束

    // 文本任务（润色 / 翻译）进行态：进度就地盖在提示词框上（与首页同一交互），
    // 不再挂到页面底部——用户点完操作人就在提示词区，不用滚下去找进度
    val textTaskStage = NaiWorkspaceState.stage
    val textTaskBusy = NaiWorkspaceState.busy &&
        (textTaskStage.startsWith("润色") || textTaskStage.startsWith("翻译") || textTaskStage.startsWith("融合"))
    val textTaskLabel = when {
        textTaskStage.startsWith("润色") -> "提示词润色中"
        textTaskStage.startsWith("翻译") -> "提示词翻译中"
        textTaskStage.startsWith("融合") -> "角色场景融合中"
        else -> textTaskStage.ifBlank { "处理中" }
    }

    // 四角星悬浮操作已下线：改为与首页「描述你的画面」同款按钮行（撤回 / 反推 / 翻译 / 润色 + 清空）
    var reverseOpen by remember { mutableStateOf(false) }
    var lastPrompt by remember { mutableStateOf(c.prompt) }
    // 打字停顿后把上一版记进历史，撤回才有东西可回
    LaunchedEffect(c.prompt) {
        delay(900)
        if (c.prompt != lastPrompt) {
            NaiPromptHistory.push(lastPrompt)
            lastPrompt = c.prompt
        }
    }

    fun applyPrompt(next: String) {
        val current = NaiWorkspaceState.config
        NaiPromptHistory.push(current.prompt)
        lastPrompt = next
        update(current.copy(prompt = next))
    }

    fun undoPrompt() {
        val prev = NaiPromptHistory.pop()
        if (prev == null) {
            NaiWorkspaceState.error = "没有可撤回的修改"
            return
        }
        lastPrompt = prev
        update(NaiWorkspaceState.config.copy(prompt = prev))
    }

    fun runTranslate() {
        if (NaiWorkspaceState.config.prompt.isBlank()) { NaiWorkspaceState.error = "先写点内容再翻译"; return }
        NaiWorkspaceState.runTask {
            NaiWorkspaceState.stage = "翻译中"
            client.translate(c, settings, NaiWorkspaceState.config.prompt).fold(
                { out -> if (out.isBlank()) NaiWorkspaceState.error = "翻译返回空内容" else applyPrompt(out) },
                { NaiWorkspaceState.error = it.message }
            )
        }
    }
    fun runPolish() {
        if (NaiWorkspaceState.config.prompt.isBlank()) { NaiWorkspaceState.error = "先写点内容再润色"; return }
        NaiWorkspaceState.runTask {
            NaiWorkspaceState.stage = "润色中 · ${settings.llmModel.ifBlank { "未配置 LLM" }}"
            client.polish(c, settings).fold(
                { NaiWorkspaceState.polishCandidate = it },
                { NaiWorkspaceState.error = it.message }
            )
        }
    }

    /** 生图页「选择人物」：切换角色卡启用态——启用的角色由请求组装写入 NAI 角色字段（最多 6 个） */
    fun toggleNaiCharacter(index: Int) {
        updateCard(index) { it.copy(enabled = !it.enabled) }
    }

    /**
     * LLM 场景融合：把启用角色的完整设定资料 + 画面提示词交给 LLM 按场景取景推理，
     * 产物写入各角色的 fusedCaption——生成请求优先使用它，替代机械拼接的角色标签。
     */
    fun runFuse() {
        val live = NaiWorkspaceState.config
        val enabledCount = live.characterCards.count { it.enabled && it.caption.isNotBlank() }
        if (enabledCount == 0) { NaiWorkspaceState.error = "先启用至少一个角色"; return }
        if (live.prompt.isBlank()) { NaiWorkspaceState.error = "先写画面提示词再融合"; return }
        NaiWorkspaceState.runTask {
            NaiWorkspaceState.stage = "融合中 · ${settings.llmModel.ifBlank { "未配置 LLM" }}"
            client.fuseCharacters(live, settings, live.prompt).fold({ fused ->
                // 按 | 切段：一段对一个启用角色（顺序与请求组装一致——过滤后按原序）
                val segments = fused.split("|").map { it.trim() }.filter { it.isNotBlank() }
                if (segments.isEmpty()) {
                    NaiWorkspaceState.error = "融合返回空内容"
                    return@fold
                }
                val enabledIndices = live.characterCards.indices.filter { i ->
                    val card = live.characterCards[i]
                    card.enabled && card.caption.isNotBlank()
                }
                val updated = live.characterCards.mapIndexed { i, card ->
                    val segIdx = enabledIndices.indexOf(i)
                    if (segIdx >= 0 && segIdx < segments.size) card.copy(fusedCaption = segments[segIdx]) else card
                }
                update(live.copy(characterCards = updated))
                NaiWorkspaceState.error = null
                android.widget.Toast.makeText(
                    context,
                    "融合完成：$enabledCount 个角色的场景标签已生成（生成时优先使用）",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }, { NaiWorkspaceState.error = it.message })
        }
    }

    /** 清除全部融合产物，退回机械拼接模式 */
    fun clearFuse() {
        val live = NaiWorkspaceState.config
        update(live.copy(characterCards = live.characterCards.map { it.copy(fusedCaption = "") }))
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 16.dp).imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.layout { measurable, constraints ->
            val extra = 32.dp.roundToPx()
            val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra))
            layout(constraints.maxWidth, placeable.height) { placeable.placeRelative(-16.dp.roundToPx(), 0) }
        }) {
        ScreenHeader(
            title = "NAI",
            subtitle = "独立生图工作台",
            // 左上角标题即模式切换：点「标准」回普通页
            titleOverride = {
                StudioModeSwitch(selected = StudioMode.NAI, onSelect = onSelectMode)
            },
            // 右上角：与首页同款角标，切的是「NAI 通道当前预设」——
            // 同一份预设列表，两边各记各的选择，互不影响。
            action = {
                ArkBlockAction(
                    icon = com.lo.imagine.R.drawable.ic_ark_transfer,
                    cn = activeNaiPresetName?.trim()?.takeIf { it.isNotEmpty() } ?: "选择预设",
                    en = "TRANSFER",
                    onClick = { presetPickerOpen = true }
                )
            }
        )
        }

        if (presetPickerOpen) {
            ArkPresetDialog(
                title = "NAI 通道预设",
                hint = "与首页共用同一份预设列表，但两边各记各的选择、互不影响。选中后本工作台使用该预设自己的地址与 Key（不再跟随首页）。",
                emptyText = "还没有保存的预设，先到「设置 → 绘图引擎」新建一个。",
                isEmpty = naiPresets.isEmpty(),
                onDismiss = { presetPickerOpen = false }
            ) {
                naiPresets.forEach { preset ->
                    val label = preset.name.trim().ifEmpty {
                        android.net.Uri.parse(preset.baseUrl).host ?: "未命名预设"
                    }
                    val selected = activeNaiPresetName?.trim() == preset.name.trim()
                    // NAI 原生接口只吃 nai-diffusion-* 模型；预设里带别的模型时提前提醒
                    val modelOk = preset.model.isBlank() || preset.model.trim() in NAI_NATIVE_MODELS
                    Surface(
                        onClick = { applyNaiPreset(preset) },
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
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val host = android.net.Uri.parse(preset.baseUrl).host
                                Text(
                                    (host?.takeIf { it.isNotBlank() }?.let { "$it · " } ?: "") +
                                        naiModelLabel(preset.model),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!modelOk) {
                                    Text(
                                        "该预设的模型不是 NAI 原生模型，生成前需在工作台里改模型",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            if (selected) {
                                PIcon(
                                    PopCheck,
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
        if (!NaiWorkspaceState.ready) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            return@Column
        }

        ArkTabRow(
            tabs = NAI_TABS,
            selectedKey = tab,
            onSelect = { tab = it }
        )
        Text(
            "${c.model.removePrefix("nai-diffusion-")} · ${c.width}×${c.height} · ${c.steps} steps · ${c.profileName}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )

        when (tab) {
            // ===== 生图：提示词 + 画师串 + 参数 =====
            "gen" -> {
                ArkInkPanel {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // 标题行（与首页「描述你的画面」同款结构）
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RefSectionHeading("描述你的画面", "PROMPT INPUT", Modifier.weight(1f))
                            // NAI 专属：token 估算徽章
                            val tokens = naiTokenEstimate(c.prompt)
                            TinyBadge(
                                text = "$tokens / 512",
                                accent = tokens <= 512
                            )
                        }
                        // 与标准首页共用圆角工具磁贴
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
                                enabled = c.prompt.isNotBlank(),
                                modifier = Modifier.weight(1f).heightIn(min = RefUiTokens.tileHeight)
                            )
                            ArkTileButton(
                                cn = if (textTaskStage.startsWith("翻译")) "翻译中" else "翻译",
                                en = "TRANSLATE",
                                glyph = { ArkTranslateGlyph() },
                                onClick = { runTranslate() },
                                enabled = c.prompt.isNotBlank() && !NaiWorkspaceState.busy,
                                modifier = Modifier.weight(1f).heightIn(min = RefUiTokens.tileHeight)
                            )
                            ArkTileButton(
                                cn = "图像",
                                en = "IMAGE",
                                icon = com.lo.imagine.R.drawable.ic_ark_image,
                                onClick = { reverseOpen = true },
                                enabled = !NaiWorkspaceState.busy,
                                modifier = Modifier.weight(1f).heightIn(min = RefUiTokens.tileHeight)
                            )
                            ArkTileButton(
                                cn = if (textTaskStage.startsWith("润色")) "润色中" else "润色",
                                en = "ENHANCE",
                                glyph = { PIcon(RefIcons.Wand, null, Modifier.size(RefUiTokens.tileIcon).padding(1.dp)) },
                                onClick = { runPolish() },
                                enabled = c.prompt.isNotBlank() && !NaiWorkspaceState.busy,
                                modifier = Modifier.weight(1f).heightIn(min = RefUiTokens.tileHeight)
                            )
                            ArkTileButton(
                                cn = "清空",
                                en = "CLEAR",
                                icon = com.lo.imagine.R.drawable.ic_ark_trash,
                                onClick = { update(c.copy(prompt = "")) },
                                enabled = c.prompt.isNotBlank() && !NaiWorkspaceState.busy,
                                modifier = Modifier.weight(1f).heightIn(min = RefUiTokens.tileHeight)
                            )
                        }
                        // 明暗语义色输入表面，与首页共用圆角规格
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(com.lo.imagine.ui.theme.themedShape(PopRadius.field))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .border(.8.dp, MaterialTheme.colorScheme.outlineVariant, com.lo.imagine.ui.theme.themedShape(PopRadius.field))
                        ) {
                            BasicTextField(
                                value = c.prompt,
                                onValueChange = { update(c.copy(prompt = it)) },
                                textStyle = LocalTextStyle.current.merge(
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { inner ->
                                    Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                        if (c.prompt.isEmpty()) {
                                            Text(
                                                "1girl, standing in a garden, ...",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp
                                            )
                                        }
                                        inner()
                                    }
                                },
                                minLines = 4,
                                maxLines = 8,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // 文本任务进行中：就地盖一层遮罩（与首页同一交互）
                            if (textTaskBusy) {
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
                                            "$textTaskLabel……",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 画师串：折叠下拉 + 预设（与创作页同一套预设）
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            onClick = { artistOpen = !artistOpen },
                            shape = com.lo.imagine.ui.theme.themedShape(PopRadius.field),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .62f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)
                            ) {
                                PIcon(PopSpark, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    artistPresets.firstOrNull { it.content == c.artists }?.name
                                        ?: c.artists.take(16).ifBlank { "未设置画师串" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (c.artists.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                PIcon(if (artistOpen) PopChevronUp else PopChevronDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (artistOpen) {
                            Surface(
                                onClick = { update(c.copy(artists = "")); artistOpen = false },
                                shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip),
                                color = if (c.artists.isBlank()) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(
                                    1.dp,
                                    if (c.artists.isBlank()) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    PIcon(PopClose, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (c.artists.isBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(10.dp))
                                    Text("不使用画师串", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (c.artists.isBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            if (artistPresets.isEmpty()) {
                                Text("还没有预设，点下方「＋ 新增画师串」创建", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                artistPresets.forEach { preset ->
                                    val isSelected = preset.content == c.artists
                                    Surface(
                                        onClick = { update(c.copy(artists = preset.content)); artistOpen = false },
                                        shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerLow,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    preset.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    preset.content,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            PopIconButton(PopEdit, "编辑预设", onClick = {
                                                editingArtistOriginal = preset.name
                                                artistDialogName = preset.name
                                                artistDialogContent = preset.content
                                                artistDialogOpen = true
                                            }, modifier = Modifier.size(32.dp), iconSize = 15.dp, iconTint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            PopIconButton(PopTrash, "删除预设", onClick = {
                                                scope.launch { settingsRepository.saveArtistPresets(artistPresets.filterNot { it.name == preset.name }) }
                                            }, modifier = Modifier.size(32.dp), iconSize = 15.dp, iconTint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = {
                                    editingArtistOriginal = null
                                    artistDialogName = ""
                                    artistDialogContent = ""
                                    artistDialogOpen = true
                                }, modifier = Modifier.fillMaxWidth()) { Text("＋ 新增画师串") }
                            }
                        }
                    }
                }

                // 选择人物：本通道角色卡（tag 角色）直选——点击切换启用，启用后由请求组装写入角色字段
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            onClick = { characterOpen = !characterOpen },
                            shape = com.lo.imagine.ui.theme.themedShape(PopRadius.field),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .62f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)
                            ) {
                                PIcon(PopPerson, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(10.dp))
                                val enabledNames = c.characterCards.filter { it.enabled }.map { it.name.ifBlank { "未命名角色" } }
                                Text(
                                    if (enabledNames.isEmpty()) "未选择人物"
                                    else enabledNames.joinToString("、"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (enabledNames.isEmpty()) FontWeight.Normal else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                PIcon(if (characterOpen) PopChevronUp else PopChevronDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (characterOpen) {
                            if (c.characterCards.isEmpty()) {
                                Text(
                                    "还没有角色卡，到「角色」页新增或导入智绘姬角色后即可在这里选择。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                c.characterCards.forEachIndexed { index, card ->
                                    val selected = card.enabled
                                    Surface(
                                        onClick = { toggleNaiCharacter(index) },
                                        shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip),
                                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerLow,
                                        border = BorderStroke(
                                            1.dp,
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    card.name.ifBlank { "角色 ${index + 1}" },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    // 融合版优先展示——它才是生成时真正使用的内容
                                                    card.fusedCaption.ifBlank { card.caption }
                                                        .ifBlank { "（角色特征为空，到「角色」页补充）" },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (card.fusedCaption.isNotBlank()) {
                                                    Spacer(Modifier.height(2.dp))
                                                    Text(
                                                        "✦ 已融合场景（生成时优先使用）",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                            if (selected) {
                                                PIcon(PopCheck, contentDescription = "已启用", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            // LLM 场景融合：把角色设定当上下文交给 LLM 按场景推理，替代机械拼接
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { runFuse() },
                                    enabled = !NaiWorkspaceState.busy &&
                                        c.characterCards.any { it.enabled && it.caption.isNotBlank() } &&
                                        c.prompt.isNotBlank(),
                                    modifier = Modifier.weight(1f)
                                ) { Text("✦ 融合到场景") }
                                OutlinedButton(
                                    onClick = { clearFuse() },
                                    enabled = c.characterCards.any { it.fusedCaption.isNotBlank() },
                                    modifier = Modifier.weight(1f)
                                ) { Text("清除融合") }
                            }
                            Text(
                                "融合 = 让 LLM 按当前画面的取景与状态，从角色设定里推理出「此刻可见」的标签段——" +
                                    "特写不写腿、背影用背面资料、多套服装选一套。比机械拼接更贴近场景。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                NaiSection("生成参数", "预设尺寸与手填宽高二选一，宽高必须是 64 的倍数") {
                    // 摘要行：折叠时显示当前关键参数，点击展开/收起全部控件
                    Surface(
                        onClick = { paramsOpen = !paramsOpen },
                        shape = com.lo.imagine.ui.theme.themedShape(PopRadius.field),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .62f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            PIcon(PopSliders, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "${c.width}×${c.height} · ${c.steps} steps · ${c.sampler}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            PIcon(if (paramsOpen) PopChevronUp else PopChevronDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (paramsOpen) {
                    NaiDropdown("预设尺寸", "${c.width}x${c.height}", NAI_SIZE_PRESETS) { size ->
                        val parts = size.split("x")
                        update(c.copy(width = parts[0], height = parts[1]))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { NaiNum("宽度", c.width) { update(c.copy(width = it)) } }
                        Box(Modifier.weight(1f)) { NaiNum("高度", c.height) { update(c.copy(height = it)) } }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { NaiNum("步数", c.steps) { update(c.copy(steps = it)) } }
                        Box(Modifier.weight(1f)) { NaiNum("种子 · -1 随机", c.seed) { update(c.copy(seed = it)) } }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { NaiNum("Prompt Guidance", c.scale) { update(c.copy(scale = it)) } }
                        Box(Modifier.weight(1f)) { NaiNum("Guidance Rescale", c.rescale) { update(c.copy(rescale = it)) } }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { NaiDropdown("采样方法", c.sampler, NAI_SAMPLERS) { update(c.copy(sampler = it)) } }
                        Box(Modifier.weight(1f)) { NaiDropdown("噪点表", c.schedule, NAI_SCHEDULES) { update(c.copy(schedule = it)) } }
                    }
                    }
                }

                NaiWorkspaceState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                // 出图进度：跟在生成按钮区；润色/翻译等文本任务走提示词框上的就地遮罩（与首页同款），不在这里重复
                if (NaiWorkspaceState.busy && !textTaskBusy) {
                    // 兜底看门狗：即使底层连接卡住或等待态残留，最迟 220 秒也强制结束
                    LaunchedEffect(NaiWorkspaceState.startedAt) {
                        delay(220_000)
                        if (NaiWorkspaceState.busy && System.currentTimeMillis() - NaiWorkspaceState.startedAt >= 219_000) {
                            NaiWorkspaceState.cancelWaiting()
                            NaiWorkspaceState.error = "已自动结束等待：220 秒没有响应，通常是中转或上游 NovelAI 卡住；可稍后重试或在设置页换通道"
                        }
                    }
                    // 生成中：只保留渲染动画 + 「正在生图」；详细说明不再铺在页面上
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            RunnerDinoLoader(modifier = Modifier.fillMaxWidth(), trackHeight = 44.dp)
                            Spacer(Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "正在生图",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { NaiWorkspaceState.cancelWaiting() }) { Text("取消") }
                            }
                        }
                    }
                }
                 ArkGenerateBar(
                    loading = NaiWorkspaceState.busy,
                    label = if (NaiWorkspaceState.busy) "正在生成 · 点此取消" else "生成 1 张 · NAI",
                    sub = if (NaiWorkspaceState.busy) "TAP TO CANCEL" else "GENERATE / NAI",
                    onClick = {
                        if (NaiWorkspaceState.busy) { NaiWorkspaceState.cancelWaiting(); return@ArkGenerateBar }
                        naiApiConfigurationError(c, settings)?.let {
                            NaiWorkspaceState.error = it
                            return@ArkGenerateBar
                        }
                        val seed = if (c.seed == "-1") kotlin.random.Random.nextLong(0, 4294967296L) else c.seed.toLongOrNull()
                        if (seed == null) { NaiWorkspaceState.error = "种子格式错误"; return@ArkGenerateBar }
                        val validation = runCatching { naiNativePayload(c, seed) }
                        if (validation.isFailure) { NaiWorkspaceState.error = validation.exceptionOrNull()?.message; return@ArkGenerateBar }
                        NaiWorkspaceState.runTask {
                            // 与首页一致：新一批生成前清空上一批结果，只显示最近一次生成
                            NaiWorkspaceState.clearShots()
                            // 配置由 ImagineApp 的 snapshotFlow 收集器负责落盘；出图请求前不再等设置存储，
                            // 否则存储写不进去就会永远卡在「保存配置」这一步
                            NaiWorkspaceState.stage = "请求出图"
                            client.generate(c, seed, settings).fold({ images ->
                                NaiWorkspaceState.stage = "解码图片"
                                val bitmap = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(images.first(), 0, images.first().size) }
                                if (bitmap == null) {
                                    NaiWorkspaceState.error = "返回图片解码失败"
                                } else {
                                    NaiWorkspaceState.result = bitmap
                                    NaiWorkspaceState.usedSeed = seed
                                    // 存档/预览用完整提示词：主体 + 角色卡（角色走独立字段不进 input，必须在这里补上）
                                    NaiWorkspaceState.resultPrompt = naiDisplayPrompt(c, validation.getOrThrow()["input"].toString())
                                    NaiWorkspaceState.resultModel = c.model
                                    NaiWorkspaceState.addShot(
                                        NaiShot(
                                            bitmap = bitmap,
                                            prompt = NaiWorkspaceState.resultPrompt,
                                            model = c.model,
                                            sizeNote = "${bitmap.width}x${bitmap.height}",
                                            createdAt = System.currentTimeMillis()
                                        )
                                    )
                                    // 与普通模式同一条落盘链：作品库 + 系统相册；落盘走 IO 且限时，卡住也不占用等待态
                                    NaiWorkspaceState.stage = "写入作品库"
                                    val archived = withTimeoutOrNull(20_000) {
                                        withContext(Dispatchers.IO) {
                                            ImageUtils.archiveResult(
                                                context = context,
                                                bitmap = bitmap,
                                                prompt = NaiWorkspaceState.resultPrompt,
                                                model = c.model,
                                                kind = "gen",
                                                toGallery = settings.autoSaveGallery
                                            )
                                        }
                                    }
                                    if (archived == null) NaiWorkspaceState.error = "图片已生成，但写入作品库/相册超时，可去预览里手动保存"
                                }
                            }, { NaiWorkspaceState.error = it.message })
                        }
                    },
                    enabled = NaiWorkspaceState.busy || c.prompt.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                )

                NaiWorkspaceState.shots.let { shots ->
                    if (shots.isNotEmpty()) {
                        // 与作品库共用 PopMediaCard / SelectionHeaderRow：点开预览，长按多选，右上角勾选 / 全选
                        var selecting by remember { mutableStateOf(false) }
                        var picked by remember { mutableStateOf<Set<Int>>(emptySet()) }
                        fun exitSelection() { selecting = false; picked = emptySet() }
                        fun preview(shot: NaiShot) {
                            PreviewStore.bitmap = shot.bitmap
                            PreviewStore.prompt = shot.prompt
                            PreviewStore.model = shot.model
                            PreviewStore.workflowDetails = null
                            PreviewStore.sizeNote = shot.sizeNote
                            PreviewStore.historyList = null
                            onPreview()
                        }
                        SelectionHeaderRow(
                            selecting = selecting,
                            selectedCount = picked.size,
                            totalCount = shots.size,
                            idleText = "本次生成 ${shots.size}张 ·长按多选",
                            allSelected = picked.size == shots.size,
                            onToggleAll = { picked = if (picked.size == shots.size) emptySet() else shots.indices.toSet() }
                        ) {
                            PopIconButton(
                                icon = PopDownload,
                                contentDescription = "写入相册",
                                onClick = {
                                    val chosen = shots.filterIndexed { i, _ -> i in picked }
                                    if (chosen.isNotEmpty()) {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                chosen.forEach { shot ->
                                                    runCatching {
                                                        ImageUtils.archiveResult(
                                                            context = context, bitmap = shot.bitmap, prompt = shot.prompt,
                                                            model = shot.model, kind = "gen", toGallery = true
                                                        )
                                                    }
                                                }
                                            }
                                            android.widget.Toast.makeText(context, "已写入相册 ${chosen.size} 张", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    exitSelection()
                                },
                                modifier = Modifier.size(32.dp),
                                iconSize = 17.dp
                            )
                            PopIconButton(
                                icon = PopTrash,
                                contentDescription = "从本次列表移除",
                                onClick = {
                                    NaiWorkspaceState.removeShots(picked)
                                    exitSelection()
                                },
                                modifier = Modifier.size(32.dp),
                                iconTint = MaterialTheme.colorScheme.error,
                                iconSize = 17.dp
                            )
                            PopIconButton(
                                icon = PopClose,
                                contentDescription = "退出选择",
                                onClick = { exitSelection() },
                                modifier = Modifier.size(32.dp),
                                iconSize = 17.dp
                            )
                        }
                        // 修复：用 LazyVerticalGrid + 固定高度，避免生成多张图片后页面无限延伸
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (shots.size > 2) 380.dp else 190.dp)
                        ) {
                            itemsIndexed(shots) { index, shot ->
                                val isPicked = index in picked
                                PopMediaCard(
                                    aspectRatio = .8f,
                                    selecting = selecting,
                                    selected = isPicked,
                                    footerText = "NAI  //  ${ImageUtils.formatTimestamp(shot.createdAt)}",
                                    cornerBadge = if (selecting) null else "${index + 1}",
                                    animationDelayMs = index * 70,
                                    onClick = {
                                        if (selecting) {
                                            picked = if (isPicked) picked - index else picked + index
                                        } else {
                                            preview(shot)
                                        }
                                    },
                                    onLongClick = {
                                        if (selecting) {
                                            picked = picked - index
                                        } else {
                                            selecting = true
                                            picked = setOf(index)
                                        }
                                    },
                                    contentDescription = "NAI 生成结果"
                                ) {
                                    Image(
                                        shot.bitmap.asImageBitmap(),
                                        null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ===== 预设：固定提示词 / 替换规则 / 质量预设 =====
            "preset" -> {
                NaiSection(
                    "固定提示词",
                    "提示词预设管住这三块；每次出图按「画师串 → 固定前置 → 画面主体 → 固定后置 → 质量词」注入"
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            DropdownField(
                                selected = c.promptPresetName,
                                options = listOf("当前") + c.promptPresets.map { it.name },
                                onSelect = { name -> c.promptPresets.firstOrNull { it.name == name }?.let { update(applyNaiPromptPreset(c, it)) } },
                                menuHeight = 240
                            )
                        }
                        PopIconButton(PopEdit, "新建提示词预设", onClick = { saveName = ""; saveKind = "prompt" }, modifier = Modifier.size(44.dp))
                        PopIconButton(PopCheck, "保存到当前预设", onClick = {
                            val n = c.promptPresetName
                            update(c.copy(promptPresets = c.promptPresets.filterNot { it.name == n } + NaiPromptPreset(n, c.fixedPrefix, c.fixedSuffix, c.negative, c.artists)))
                        }, modifier = Modifier.size(44.dp))
                        PopIconButton(PopTrash, "删除当前预设", onClick = {
                            update(c.copy(promptPresets = c.promptPresets.filterNot { it.name == c.promptPresetName }, promptPresetName = "默认"))
                        }, modifier = Modifier.size(44.dp), iconTint = MaterialTheme.colorScheme.error)
                    }
                    NaiTextField("固定正面提示词 · 每张图最前", c.fixedPrefix, 3, naiTokenEstimate(c.fixedPrefix)) { update(c.copy(fixedPrefix = it)) }
                    NaiTextField("后置固定正面提示词 · 画师串之后", c.fixedSuffix, 2, naiTokenEstimate(c.fixedSuffix)) { update(c.copy(fixedSuffix = it)) }
                    NaiTextField("固定负面提示词", c.negative, 3, naiTokenEstimate(c.negative)) { update(c.copy(negative = it)) }
                }

                NaiSection("提示词替换", "每行一条「原词=新词」，出图前对固定词、主体、画师串与负面词统一替换") {
                    NaiTextField("替换规则", c.promptReplace, 3, null, placeholder = "例如：猫娘=cat girl, animal ears") { update(c.copy(promptReplace = it)) }
                }

                NaiSection("质量预设", "按所选模型版本注入官方质量词，关闭即不追加") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("正面质量预设", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            DropdownField(c.quality, listOf("standard", "off"), onSelect = { update(c.copy(quality = it)) }, menuHeight = 160)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("负面质量预设", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            DropdownField(c.uc, listOf("light", "heavy", "off"), onSelect = { update(c.copy(uc = it)) }, menuHeight = 200)
                        }
                    }
                    NaiSwitchRow("福瑞数据集", "在提示词最前追加 fur dataset", c.furryDataset) { update(c.copy(furryDataset = it)) }
                }
            }

            // ===== 角色：角色与服装 =====
            "character" -> {
                val card = c.characterCards.getOrNull(cardIndex)
                // 下拉标签（空名显示「角色 N」，重名追加序号）：选中、选项、回填共用同一套标签，
                // 避免空名/重名角色选不中而静默跳到第 0 张——那会让「启用该角色」切错卡。
                val rawLabels = c.characterCards.mapIndexed { i, ch -> ch.name.ifBlank { "角色 ${i + 1}" } }
                val cardLabels = rawLabels.mapIndexed { i, label -> if (rawLabels.count { it == label } > 1) "$label (${i + 1})" else label }
                var detailExpanded by remember { mutableStateOf(false) }

                // 智绘姬 (st-chatu8) / NovelAI 角色 JSON 导入器
                val jsonPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.GetContent()
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    scope.launch {
                        val text = withContext(Dispatchers.IO) {
                            runCatching {
                                context.contentResolver.openInputStream(uri)?.use {
                                    it.bufferedReader(Charsets.UTF_8).readText()
                                }
                            }.getOrNull()
                        }
                        if (text.isNullOrBlank()) {
                            NaiWorkspaceState.error = "无法读取所选文件或文件内容为空"
                            return@launch
                        }
                        val imported = Chatu8CharacterImporter.parseJson(text)
                        if (imported.isEmpty()) {
                            NaiWorkspaceState.error = "未能识别出智绘姬角色数据，请检查 JSON 格式"
                            return@launch
                        }
                        val current = NaiWorkspaceState.config.characterCards.toMutableList()
                        var addedCount = 0
                        for (newChar in imported) {
                            if (current.size < 6) {
                                current.add(newChar)
                                addedCount++
                            }
                        }
                        update(NaiWorkspaceState.config.copy(characterCards = current))
                        cardIndex = (current.size - addedCount).coerceAtLeast(0)
                        android.widget.Toast.makeText(context, "成功导入 $addedCount 个角色（最多 6 个）", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }

                NaiSection("角色与服装") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            DropdownField(
                                selected = cardLabels.getOrNull(cardIndex) ?: "＋ 新增角色",
                                options = cardLabels + "＋ 新增角色",
                                onSelect = { picked ->
                                    if (picked == "＋ 新增角色") {
                                        val live = NaiWorkspaceState.config
                                        if (live.characterCards.size < 6) {
                                            update(live.copy(characterCards = live.characterCards + NaiCharacterPrompt(name = "角色 ${live.characterCards.size + 1}")))
                                            cardIndex = live.characterCards.size
                                        }
                                    } else {
                                        cardIndex = cardLabels.indexOf(picked).coerceAtLeast(0)
                                    }
                                },
                                menuHeight = 240
                            )
                        }
                        OutlinedButton(
                            onClick = { jsonPicker.launch("*/*") },
                            modifier = Modifier.height(44.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            PIcon(PopDownload, contentDescription = "导入角色", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("导入", style = MaterialTheme.typography.labelMedium)
                        }
                        PopIconButton(PopTrash, "删除该角色", onClick = {
                            if (card != null) {
                                val live = NaiWorkspaceState.config
                                update(live.copy(characterCards = live.characterCards.filterIndexed { i, _ -> i != cardIndex }))
                                cardIndex = 0
                            }
                        }, enabled = card != null, modifier = Modifier.size(44.dp), iconTint = MaterialTheme.colorScheme.error)
                    }
                    if (card == null) {
                        Text("还没有角色卡。上面下拉选「＋ 新增角色」开始。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        NaiSwitchRow("启用该角色", if (card.enabled && card.caption.isBlank()) "已启用，但提示词为空——补上特征/服装才会写入请求" else "关闭后不写入本次请求", card.enabled) { on -> updateCard(cardIndex) { it.copy(enabled = on) } }
                        
                        // 视角与状态切换行
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) {
                                NaiDropdown("视角", if (card.viewAngle == "back") "背面 (Back)" else "正面 (Front)", listOf("正面 (Front)", "背面 (Back)")) { picked ->
                                    updateCard(cardIndex) { it.copy(viewAngle = if (picked.startsWith("背面")) "back" else "front") }
                                }
                            }
                            Box(Modifier.weight(1f)) {
                                NaiDropdown("状态", when (card.bodyMode) { "nsfw" -> "NSFW"; "custom" -> "仅服装/自定义"; else -> "SFW" }, listOf("SFW", "NSFW", "仅服装/自定义")) { picked ->
                                    updateCard(cardIndex) { it.copy(bodyMode = when (picked) { "NSFW" -> "nsfw"; "仅服装/自定义" -> "custom"; else -> "sfw" }) }
                                }
                            }
                        }

                        NaiTextField("中文名", card.name, 1, null) { v -> updateCard(cardIndex) { it.copy(name = v) } }
                        NaiTextField("英文名", card.nameEn, 1, null) { v -> updateCard(cardIndex) { it.copy(nameEn = v) } }
                        NaiTextField("角色特征", card.traits, 3, naiTokenEstimate(card.traits), placeholder = "1girl, silver hair, red eyes") { v -> updateCard(cardIndex) { it.copy(traits = v) } }
                        
                        // 常用五官与当前服装
                        NaiTextField("五官外貌 (正面)", card.face, 2, naiTokenEstimate(card.face), placeholder = "sharp eyes, small mouth") { v -> updateCard(cardIndex) { it.copy(face = v) } }
                        NaiTextField("服装", card.outfit, 3, naiTokenEstimate(card.outfit), placeholder = "black coat, white shirt, leather boots") { v -> updateCard(cardIndex) { it.copy(outfit = v) } }

                        // 折叠组：详细参数（正背面与 SFW / NSFW 身体矩阵）
                        Surface(
                            shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "详细身体与正背面参数",
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { detailExpanded = !detailExpanded }) {
                                        Text(if (detailExpanded) "收起" else "展开详细参数")
                                    }
                                }
                                if (detailExpanded) {
                                    NaiTextField("五官外貌 (背面)", card.faceBack, 2, naiTokenEstimate(card.faceBack), placeholder = "short hair nape, hair ribbon from behind") { v -> updateCard(cardIndex) { it.copy(faceBack = v) } }
                                    NaiTextField("上半身 SFW", card.upperSfw, 2, naiTokenEstimate(card.upperSfw), placeholder = "cleavage cutout, fitted bodice") { v -> updateCard(cardIndex) { it.copy(upperSfw = v) } }
                                    NaiTextField("上半身 SFW (背面)", card.upperSfwBack, 2, naiTokenEstimate(card.upperSfwBack), placeholder = "backless dress, shoulder blades") { v -> updateCard(cardIndex) { it.copy(upperSfwBack = v) } }
                                    NaiTextField("下半身 SFW", card.lowerSfw, 2, naiTokenEstimate(card.lowerSfw), placeholder = "pleated skirt, thighhighs") { v -> updateCard(cardIndex) { it.copy(lowerSfw = v) } }
                                    NaiTextField("下半身 SFW (背面)", card.lowerSfwBack, 2, naiTokenEstimate(card.lowerSfwBack), placeholder = "skirt from behind") { v -> updateCard(cardIndex) { it.copy(lowerSfwBack = v) } }
                                    NaiTextField("上半身 NSFW", card.upperNsfw, 2, naiTokenEstimate(card.upperNsfw), placeholder = "bare breasts, nipples") { v -> updateCard(cardIndex) { it.copy(upperNsfw = v) } }
                                    NaiTextField("上半身 NSFW (背面)", card.upperNsfwBack, 2, naiTokenEstimate(card.upperNsfwBack), placeholder = "bare back") { v -> updateCard(cardIndex) { it.copy(upperNsfwBack = v) } }
                                    NaiTextField("下半身 NSFW", card.lowerNsfw, 2, naiTokenEstimate(card.lowerNsfw), placeholder = "pussy, thighs") { v -> updateCard(cardIndex) { it.copy(lowerNsfw = v) } }
                                    NaiTextField("下半身 NSFW (背面)", card.lowerNsfwBack, 2, naiTokenEstimate(card.lowerNsfwBack), placeholder = "ass, buttocks from behind") { v -> updateCard(cardIndex) { it.copy(lowerNsfwBack = v) } }
                                }
                            }
                        }

                        NaiTextField("补充（姿态/画风等）", card.prompt, 2, null) { v -> updateCard(cardIndex) { it.copy(prompt = v) } }
                        NaiTextField("角色负向 (UC)", card.negative, 1, null) { v -> updateCard(cardIndex) { it.copy(negative = v) } }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) {
                                NaiNum("X 0–1", card.x.toString()) { v -> v.toDoubleOrNull()?.coerceIn(0.0, 1.0)?.let { x -> updateCard(cardIndex) { it.copy(x = x) } } }
                            }
                            Box(Modifier.weight(1f)) {
                                NaiNum("Y 0–1", card.y.toString()) { v -> v.toDoubleOrNull()?.coerceIn(0.0, 1.0)?.let { y -> updateCard(cardIndex) { it.copy(y = y) } } }
                            }
                        }
                        Text("拼接结果：${card.caption.ifBlank { "（空）" }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ===== 更多：连接（跟随后台）+ 高级开关 + LLM =====
            else -> {
                NaiSection("连接", "两条通道各自持有连接：选预设即用预设的地址与 Key；打开跟随则借用首页接口") {
                    NaiSwitchRow("跟随后台 API 连接", "使用设置页（首页通道）的 API 地址与 Key；关闭则用本页预设/独立端点", c.useBackendApi) { update(c.copy(useBackendApi = it)) }
                    if (c.useBackendApi) {
                        Text(
                            "当前：${settings.baseUrl.ifBlank { "（设置页未配置）" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        PopTextField(
                            value = c.token,
                            onValueChange = { update(c.copy(token = it)) },
                            label = "NAI Token",
                            placeholder = "pst-...",
                            singleLine = true,
                            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingContent = {
                                PopIconButton(
                                    if (showToken) PopClose else PopKey,
                                    if (showToken) "隐藏 Token" else "显示 Token",
                                    onClick = { showToken = !showToken },
                                    modifier = Modifier.size(34.dp),
                                    iconSize = 15.dp
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        PopTextField(c.endpoint, { update(c.copy(endpoint = it)) }, label = "生图 Endpoint", singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    NaiDropdown("模型", c.model, NAI_NATIVE_MODELS) { update(c.copy(model = it)) }
                    NaiSwitchRow("AI 默认角色位置", "由模型自动排布多角色，忽略手写坐标", c.useCoords) { update(c.copy(useCoords = it)) }
                    NaiSwitchRow("SMEA", "旧版平滑采样，通常保持关闭", c.smea) { update(c.copy(smea = it)) }
                    NaiSwitchRow("SMEA DYN", "需先开启 SMEA", c.smeaDyn, enabled = c.smea) { update(c.copy(smeaDyn = it)) }
                    NaiSwitchRow("多样性 (Variety)", "跳过高 sigma 阶段的 CFG，降低画面被 CFG 压死", c.variety) { update(c.copy(variety = it)) }
                    NaiSwitchRow("减少伪影 (Decrisp)", "开启动态阈值", c.decrisp) { update(c.copy(decrisp = it)) }
                    if (c.model.contains("diffusion-5")) {
                        NaiSwitchRow("透明图 (straight_alpha)", "仅 5 系模型支持", c.straightAlpha) { update(c.copy(straightAlpha = it)) }
                    }
                }

                NaiSection("配置档案", "接口、模型、采样与生成参数整体存取；与提示词预设互不影响") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            DropdownField(
                                selected = c.profileName,
                                options = listOf("当前") + c.profiles.map { it.name },
                                onSelect = { name -> c.profiles.firstOrNull { it.name == name }?.let { update(applyNaiConfigProfile(c, it)) } },
                                menuHeight = 240
                            )
                        }
                        PopIconButton(PopRefresh, "读取选中配置", onClick = {
                            c.profiles.firstOrNull { it.name == c.profileName }?.let { update(applyNaiConfigProfile(c, it)) }
                        }, modifier = Modifier.size(44.dp))
                        PopIconButton(PopEdit, "新建配置", onClick = { saveName = ""; saveKind = "config" }, modifier = Modifier.size(44.dp))
                        PopIconButton(PopCheck, "保存当前配置", onClick = {
                            val n = c.profileName
                            update(c.copy(profiles = c.profiles.filterNot { it.name == n } + snapshotNaiConfig(c, n)))
                        }, modifier = Modifier.size(44.dp))
                        PopIconButton(PopTrash, "删除当前配置", onClick = {
                            update(c.copy(profiles = c.profiles.filterNot { it.name == c.profileName }, profileName = "默认"))
                        }, modifier = Modifier.size(44.dp), iconTint = MaterialTheme.colorScheme.error)
                    }
                }

                NaiSection("LLM 提示词工程", "反推 / 润色的 system 可在「设置 → 提示词模板」里改；这里填的任务 system 是工作台自己的附加要求") {
                    val preChars = 0
                    Text(
                        "通道：${settings.llmModel.ifBlank { "未配置" }} @ ${settings.llmBaseUrl.substringAfter("://").take(30)}" +
                            " · 润色模板${if (settings.polishPromptTemplate.isNotBlank()) "已自定义（${settings.polishPromptTemplate.length} 字）" else "用内置默认"}" +
                            " · 反推模板${if (settings.reversePromptTemplate.isNotBlank()) "已自定义（${settings.reversePromptTemplate.length} 字）" else "用内置默认"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    NaiDropdown("任务模板", c.llmTemplateName, listOf("当前") + c.llmTemplates.map { it.name }) { n ->
                        c.llmTemplates.firstOrNull { it.name == n }?.let { update(applyNaiLlmTemplate(c, it)) }
                    }
                    NaiTextField("任务 system", c.llmInstruction, 6, null) { update(c.copy(llmInstruction = it)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { NaiNum("Temperature", c.temperature) { update(c.copy(temperature = it)) } }
                        Box(Modifier.weight(1f)) { NaiNum("Max tokens", c.maxTokens) { update(c.copy(maxTokens = it)) } }
                    }
                    OutlinedButton(onClick = { saveName = c.llmTemplateName; saveKind = "llm" }, modifier = Modifier.fillMaxWidth()) { Text("保存任务模板") }
                    Text(
                        "润色与翻译在输入框右下角的四角星里；反推与润色的 system 请到「设置 → 提示词模板」修改。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    c.llmTemplates.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { update(applyNaiLlmTemplate(c, t)) }, modifier = Modifier.weight(1f)) { Text(t.name) }
                            PopIconButton(PopTrash, "删除模板", onClick = { update(c.copy(llmTemplates = c.llmTemplates.filterNot { it.name == t.name })) }, modifier = Modifier.size(34.dp), iconSize = 15.dp)
                        }
                    }
                }

                OutlinedButton(onClick = { showRequest = !showRequest }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showRequest) "收起请求检查" else "检查最终请求 · 不消耗额度")
                }
                if (showRequest) {
                    val payload = runCatching { naiNativePayload(c, if (c.seed == "-1") 0 else requireNotNull(c.seed.toLongOrNull()) { "种子格式错误" }) }
                    Text("随机种子在检查中以 0 占位；请求体不包含 Token。", style = MaterialTheme.typography.bodySmall)
                    SelectionContainer {
                        Text(
                            "实际请求地址：${naiEffectiveEndpoint(c, settings)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    SelectionContainer {
                        Text(
                            payload.fold({ com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(it) }, { it.message.orEmpty() }),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                NaiWorkspaceState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    // ===== 命名弹窗 =====
    saveKind?.let { kind ->
        PopAlertDialog(
            title = when (kind) { "config" -> "新建配置档案"; "prompt" -> "新建提示词预设"; else -> "保存任务模板" },
            onDismissRequest = { saveKind = null },
            confirmLabel = "保存",
            confirmEnabled = saveName.isNotBlank(),
            onConfirm = {
                val n = saveName.trim()
                update(
                    when (kind) {
                        "config" -> c.copy(profileName = n, profiles = c.profiles.filterNot { it.name == n } + snapshotNaiConfig(c, n))
                        "prompt" -> c.copy(promptPresetName = n, promptPresets = c.promptPresets.filterNot { it.name == n } + NaiPromptPreset(n, c.fixedPrefix, c.fixedSuffix, c.negative, c.artists))
                        else -> c.copy(llmTemplateName = n, llmTemplates = c.llmTemplates.filterNot { it.name == n } + NaiLlmTemplate(n, c.llmInstruction, c.temperature, c.maxTokens))
                    }
                )
                saveName = ""
                saveKind = null
            },
            dismissLabel = "取消",
            onDismiss = { saveKind = null },
            text = { PopTextField(saveName, { saveName = it }, label = "名称", singleLine = true, modifier = Modifier.fillMaxWidth()) }
        )
    }

    // ===== 画师串预设弹窗 =====
    if (artistDialogOpen) {
        PopAlertDialog(
            title = if (editingArtistOriginal == null) "新增画师串" else "编辑画师串",
            onDismissRequest = { artistDialogOpen = false },
            confirmLabel = "保存",
            confirmEnabled = artistDialogName.isNotBlank() && artistDialogContent.isNotBlank(),
            onConfirm = {
                val name = artistDialogName.trim()
                val content = artistDialogContent.trim()
                if (name.isBlank() || content.isBlank()) return@PopAlertDialog
                val next = artistPresets
                    .filterNot { it.name == name || it.name == editingArtistOriginal }
                    .plus(ArtistPreset(name, content))
                scope.launch { settingsRepository.saveArtistPresets(next) }
                // 如果当前选中的就是该画师串，同步更新当前值
                if (editingArtistOriginal != null && c.artists == artistPresets.firstOrNull { it.name == editingArtistOriginal }?.content) {
                    update(c.copy(artists = content))
                }
                artistDialogOpen = false
            },
            dismissLabel = "取消",
            onDismiss = { artistDialogOpen = false },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    PopTextField(artistDialogName, { artistDialogName = it }, label = "名称", singleLine = true, modifier = Modifier.fillMaxWidth())
                    PopTextField(artistDialogContent, { artistDialogContent = it }, label = "画师串内容", minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth())
                    if (artistPresets.any { it.name == artistDialogName.trim() && it.name != editingArtistOriginal }) {
                        Text("该名称已被使用，保存将覆盖同名预设", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }

    if (reverseOpen) {
        // 反推：从相册选图 → vision LLM 写提示词 → 直接填进画面提示词（按 NAI 模型选模板）
        ReversePromptDialog(
            settings = settings,
            repository = imageRepository,
            targetModel = c.model,
            onApply = { text -> applyPrompt(text) },
            onDismiss = { reverseOpen = false }
        )
    }

    NaiWorkspaceState.polishCandidate?.let { text ->
        PopAlertDialog(
            title = "润色结果",
            onDismissRequest = { NaiWorkspaceState.polishCandidate = null },
            confirmLabel = "应用",
            onConfirm = { applyPrompt(text); NaiWorkspaceState.polishCandidate = null },
            dismissLabel = "取消",
            onDismiss = { NaiWorkspaceState.polishCandidate = null },
            text = { SelectionContainer { Text(text, modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) } }
        )
    }
}

@Composable
private fun NaiSection(title: String, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
    SectionTitle(title, hint)
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun NaiTextField(
    label: String,
    value: String,
    lines: Int,
    tokens: Int?,
    placeholder: String? = null,
    change: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        PopTextField(
            value = value,
            onValueChange = change,
            label = label,
            placeholder = placeholder,
            minLines = lines,
            maxLines = if (lines == 1) 1 else 12,
            modifier = Modifier.fillMaxWidth()
        )
        if (tokens != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                "估算 ${tokens} / 512 tokens" + if (tokens > 512) " · 超出会被截断" else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (tokens > 512) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NaiDropdown(label: String, value: String, options: List<String>, change: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        DropdownField(selected = value, options = options, onSelect = change, menuHeight = 260)
    }
}

@Composable
private fun NaiNum(label: String, value: String, change: (String) -> Unit) {
    PopTextField(
        value = value,
        onValueChange = change,
        label = label,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun NaiSwitchRow(label: String, hint: String, checked: Boolean, enabled: Boolean = true, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        PopSwitch(checked = checked, onCheckedChange = change, enabled = enabled)
    }
}
