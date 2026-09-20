package com.lo.imagine.ui.director


import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.lo.imagine.data.DirectorEngine
import com.lo.imagine.data.DirectorInterviewState
import com.lo.imagine.data.DIRECTOR_STAGES
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.luminance
import androidx.compose.material.icons.automirrored.outlined.Send
import com.lo.imagine.ui.theme.PopRadius
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableIntStateOf
import com.lo.imagine.ui.PIcon
import com.lo.imagine.data.ChatMessage
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.fillMaxHeight
import com.lo.imagine.ui.PopCheck
import com.lo.imagine.ui.PopClock
import com.lo.imagine.ui.PopFrame
import androidx.compose.foundation.text.BasicTextField
import com.lo.imagine.ui.PopChevronDown
import com.lo.imagine.ui.PopChevronUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import coil.compose.AsyncImage
import com.lo.imagine.data.AppSettings
import com.lo.imagine.data.ApiResult
import com.lo.imagine.data.ASPECT_OPTIONS
import com.lo.imagine.data.DirectorAsset
import com.lo.imagine.data.ImageRepository
import com.lo.imagine.data.SettingsRepository
import com.lo.imagine.ui.AspectGeometry
import com.lo.imagine.ui.DropdownField
import com.lo.imagine.ui.Panel
// popBackdrop 已由壳层 ArkPageBackdrop 接管
import com.lo.imagine.ui.PopAlertDialog
import com.lo.imagine.ui.PopBusySpinner
import com.lo.imagine.ui.PopChip
import com.lo.imagine.ui.PopFilm
import androidx.compose.foundation.shape.CircleShape
import com.lo.imagine.ui.celInk
import com.lo.imagine.ui.PopChipRow
import com.lo.imagine.ui.celShadow
import com.lo.imagine.ui.theme.LocalPopAccents
import com.lo.imagine.ui.theme.Ink
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.lo.imagine.ui.PopGallery
import com.lo.imagine.ui.PopImageAdd
import com.lo.imagine.ui.PopInspect
import com.lo.imagine.ui.PopPerson
import com.lo.imagine.ui.PopSpark
import com.lo.imagine.ui.PopTextField
import com.lo.imagine.ui.PopTrash
import com.lo.imagine.ui.ScreenHeader
import com.lo.imagine.ui.TinyBadge
import com.lo.imagine.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** 运镜预设：Seedance 视频提示词的镜头运动 */

private val VIDEO_ASPECTS = listOf("16:9", "9:16", "1:1", "4:3", "3:4", "21:9", "3:2", "2:3")

private const val KIND_CHARACTER = "character"
private const val KIND_SCENE = "scene"

private fun DirectorAsset.kindOrDefault(): String = kind ?: KIND_CHARACTER

/** 分镜条目：首帧参考图（注入或生成）、本镜秒数、本镜画面提示词 */
private data class Shot(
    val id: Long = 0,
    val imagePath: String? = null,
    val endImagePath: String? = null,
    val seconds: String = "3",
    val prompt: String = ""
)

private data class ChatBubble(val mine: Boolean, val text: String)

private val shotGson = com.google.gson.Gson()

private val chatGson = com.google.gson.Gson()
/** 访谈气泡随导航栈持久化（Gson JSON）：切换页面、进程重建都不丢对话 */
private val chatBubbleSaver: androidx.compose.runtime.saveable.Saver<List<ChatBubble>, String> =
    androidx.compose.runtime.saveable.Saver(
        save = { chatGson.toJson(it) },
        restore = { json ->
            runCatching {
                val type = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, ChatBubble::class.java).type
                chatGson.fromJson<List<ChatBubble>>(json, type)
            }.getOrNull() ?: emptyList()
        }
    )

private val interviewSaver: androidx.compose.runtime.saveable.Saver<DirectorInterviewState, String> =
    androidx.compose.runtime.saveable.Saver(
        save = { chatGson.toJson(it) },
        restore = { chatGson.fromJson(it, DirectorInterviewState::class.java) }
    )

/** Each engine owns a saveable workspace; switching retains its answers and draft. */
@Composable
fun DirectorScreen(settings: AppSettings, repository: ImageRepository, settingsRepository: SettingsRepository) {
    var engine by rememberSaveable { mutableStateOf(DirectorEngine.SEEDANCE) }
    val workspaces = rememberSaveableStateHolder()
    DirectorInlineInput {
        workspaces.SaveableStateProvider("director-steps-v2-${engine.id}") {
            DirectorWorkspace(settings, repository, settingsRepository, engine, onEngineChange = { engine = it })
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DirectorWorkspace(
    settings: AppSettings,
    repository: ImageRepository,
    settingsRepository: SettingsRepository,
    engine: DirectorEngine,
    onEngineChange: (DirectorEngine) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // ===== 分镜要素：人物与环境置顶 =====
    var characterDesc by rememberSaveable { mutableStateOf("") }
    var envDesc by rememberSaveable { mutableStateOf("") }
    // ===== Engine-specific staged interview =====
    var chatMessages by rememberSaveable(stateSaver = chatBubbleSaver) { mutableStateOf(listOf(ChatBubble(false, com.lo.imagine.data.directorOpening()))) }
    var chatInput by rememberSaveable { mutableStateOf("") }
    var chatThinking by remember { mutableStateOf(false) }
    var interview by rememberSaveable(stateSaver = interviewSaver) { mutableStateOf(DirectorInterviewState()) }
    var generationError by rememberSaveable { mutableStateOf<String?>(null) }
    var chatDone by rememberSaveable { mutableStateOf(false) }
    var durationSec by rememberSaveable { mutableStateOf("5") }
    var videoAspect by rememberSaveable { mutableStateOf(VIDEO_ASPECTS.first()) }

    LaunchedEffect(Unit) {
        if (interview.stageIndex == 0 && chatMessages.size == 1 && !chatMessages.first().mine) {
            chatMessages = listOf(ChatBubble(false, com.lo.imagine.data.directorOpening()))
        }
    }

    // ===== 输出 =====
    var draft by rememberSaveable { mutableStateOf("") }
    var polished by rememberSaveable { mutableStateOf("") }
    var polishing by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var copiedKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(copiedKey) {
        if (copiedKey != null) { delay(1800); copiedKey = null }
    }

    // ===== 素材库（弹窗承载） =====
    var assets by remember { mutableStateOf<List<DirectorAsset>>(emptyList()) }
    LaunchedEffect(Unit) {
        assets = withContext(Dispatchers.IO) { settingsRepository.loadDirectorAssets() }
    }
    /** 当前打开的素材弹窗类型；null=关闭 */
    var assetPickerKind by remember { mutableStateOf<String?>(null) }
    var showAddAsset by remember { mutableStateOf(false) }
    var addAssetKind by rememberSaveable { mutableStateOf(KIND_CHARACTER) }
    var assetToDelete by remember { mutableStateOf<DirectorAsset?>(null) }
fun persistAssets(next: List<DirectorAsset>) {
        assets = next
        scope.launch(Dispatchers.IO) { settingsRepository.saveDirectorAssets(next) }
    }

    // ===== 分镜头列表（Gson JSON 持久化，进程重建自动恢复） =====
    var shotsJson by rememberSaveable { mutableStateOf("[]") }
    val shots: List<Shot> = remember(shotsJson) {
        runCatching {
            val type = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, Shot::class.java).type
            shotGson.fromJson<List<Shot>>(shotsJson, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }
    fun replaceShots(next: List<Shot>) {
        shotsJson = shotGson.toJson(next)
    }
    var nextShotId by rememberSaveable { mutableStateOf(1L) }
    var generatingShotId by remember { mutableStateOf<Long?>(null) }
    var pendingInjectTarget by remember { mutableStateOf<Pair<Long, Boolean>?>(null) }
    var durationDialog by remember { mutableStateOf(false) }
    var aspectDialog by remember { mutableStateOf(false) }
    var composing by remember { mutableStateOf(false) }
    var polishedTitle by rememberSaveable { mutableStateOf("润色结果") }

    fun buildDraft(): String {
        val lines = mutableListOf("【目标工程】${engine.fullName}", interview.brief())
        if (characterDesc.isNotBlank()) lines += "【人物】${characterDesc.trim()}"
        if (envDesc.isNotBlank()) lines += "【环境】${envDesc.trim()}"
        lines += "【参数】${durationSec.trim().ifEmpty { "5" }}s · $videoAspect"
        return lines.joinToString("\n")
    }

    fun copyText(key: String, text: String) {
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("${engine.fullName} 提示词", text))
        copiedKey = key
    }

    fun revisitStage(index: Int) {
        if (chatThinking || polishing || composing) return
        val previous = interview.summaries[index]
        interview = interview.revisit(index)
        chatDone = false
        polished = ""
        draft = ""
        error = null
        generationError = null
        chatMessages = chatMessages + ChatBubble(false,
            if (previous.isBlank()) engine.question(index, durationSec, videoAspect)
            else "之前的${DIRECTOR_STAGES[index].label}设定是：$previous\n\n这部分想改哪里？也可以回答“保留这些要点”。")
    }

    fun invalidateFramework(index: Int) {
        if (interview.confirmedCount > index || (interview.stageIndex == index && interview.stageReady)) revisitStage(index)
    }

    fun finishInterview() {
        if (chatThinking || !interview.canGenerate) return
        com.lo.imagine.data.llmApiConfigurationError(settings)?.let { generationError = it; return }
        chatThinking = true
        generationError = null
        error = null
        focusManager.clearFocus()
        keyboard?.hide()
        val brief = buildDraft() + "\n【已确认框架优先于历史讨论】\n" +
            chatMessages.joinToString("\n") { (if (it.mine) "用户：" else "导演：") + it.text }
        scope.launch {
            try {
                repository.composeDirectorStoryboard(settings, engine, brief, emptyList())
                    .onSuccess { script ->
                        polished = script
                        polishedTitle = "${engine.label} 分镜脚本"
                        chatMessages = chatMessages + ChatBubble(false, script)
                        chatDone = true
                    }
                    .onFailure { e -> generationError = "脚本生成失败：${e.message ?: "请求未完成"}。框架已保留，可直接重试。" }
            } finally { chatThinking = false }
        }
    }

    fun confirmAndContinue() {
        if (chatThinking || chatDone || interview.isReview) return
        val text = chatInput.trim().ifBlank {
            when (interview.stageIndex) {
                1 -> if (characterDesc.isNotBlank()) "采用已选人物素材：$characterDesc" else ""
                2 -> if (envDesc.isNotBlank()) "采用已选环境素材：$envDesc" else ""
                else -> ""
            }
        }
        if (text.isBlank()) return
        com.lo.imagine.data.llmApiConfigurationError(settings)?.let { error = it; return }
        chatThinking = true
        error = null
        focusManager.clearFocus()
        keyboard?.hide()
        if (chatMessages.lastOrNull() != ChatBubble(true, text)) chatMessages = chatMessages + ChatBubble(true, text)
        val submittedState = interview
        val transcript = chatMessages.joinToString("\n") { (if (it.mine) "用户：" else "导演：") + it.text }
        scope.launch {
            try {
                repository.directorStepTurn(settings, engine, submittedState, transcript,
                    characterDesc, envDesc, durationSec, videoAspect)
                    .onSuccess { turn ->
                        interview = submittedState.receive(turn)
                        turn.durationSec?.let { durationSec = it }
                        turn.videoAspect?.let { videoAspect = it }
                        chatInput = ""
                        chatMessages = chatMessages + ChatBubble(false,
                            com.lo.imagine.data.directorTurnMessage(turn))
                    }
                    .onFailure { e ->
                        chatInput = text
                        error = "本轮未完成：${e.message ?: "请求失败"}。回答已保留，重试后继续。"
                    }
            } finally { chatThinking = false }
        }
    }

    fun confirmStage() {
        if (chatThinking || !interview.stageReady || chatInput.isNotBlank() || error != null) return
        val nextQuestion = interview.nextQuestion
        interview = interview.confirm()
        focusManager.clearFocus()
        keyboard?.hide()
        if (!interview.isReview) {
            val title = DIRECTOR_STAGES[interview.stageIndex].label
            chatMessages = chatMessages + ChatBubble(false,
                "第 ${interview.stageIndex + 1}/5 阶段 · $title\n\n$nextQuestion")
        } else {
            chatMessages = chatMessages + ChatBubble(false,
                "拍摄想法已经整理好了。看看下面有没有想改的地方；确认后，就能生成 ${engine.label} 提示词。")
        }
    }

    fun restartInterview() {
        if (chatThinking || polishing || composing) return
        interview = DirectorInterviewState()
        chatMessages = listOf(ChatBubble(false, com.lo.imagine.data.directorOpening()))
        chatInput = ""; chatDone = false; error = null; generationError = null
        draft = ""; polished = ""; characterDesc = ""; envDesc = ""
        durationSec = "5"; videoAspect = "16:9"
        replaceShots(emptyList())
        focusManager.clearFocus()
        keyboard?.hide()
    }

    fun polish() {
        if (draft.isBlank() || polishing) return
        com.lo.imagine.data.llmApiConfigurationError(settings)?.let { error = it; return }
        polishing = true
        polishedTitle = "润色结果"
        error = null
        scope.launch {
            repository.polishDirectorPrompt(settings, engine, draft)
                .onSuccess { polished = it }
                .onFailure { error = it.message ?: "润色失败" }
            polishing = false
        }
    }

    /**
     * 素材在字段中的可匹配文本候选（整段子串匹配，绝不按逗号切段——
     * 素材描述本身就是逗号分隔的 tag 串，切段会把描述拆碎导致永远匹配不上）。
     * 兼容历史数据的三种形态：「名称：描述」、纯描述、纯名称。
     */
    fun assetTokens(a: DirectorAsset): List<String> {
        val name = a.name.trim()
        val desc = a.desc.trim()
        return listOfNotNull(
            if (name.isNotBlank() && desc.isNotBlank()) "$name：$desc" else null,
            desc.ifBlank { null },
            name.ifBlank { null }
        ).distinct()
    }

    /** 素材是否处于「启用」态：任一候选整段子串命中即可 */
    fun isAssetActive(a: DirectorAsset): Boolean {
        val field = if (a.kindOrDefault() == KIND_SCENE) envDesc else characterDesc
        return assetTokens(a).any { it.isNotBlank() && field.contains(it) }
    }

    /** 素材开关：点击启用（绿色包边，追加段），再点取消（移除子串并清理残留分隔符）；弹窗不关 */
    fun toggleAsset(a: DirectorAsset) {
        val isScene = a.kindOrDefault() == KIND_SCENE
        val current = if (isScene) envDesc else characterDesc
        val tokens = assetTokens(a)
        // 取消：优先移除最长（信息最全）的命中子串
        val hit = tokens.filter { it.isNotBlank() && current.contains(it) }
            .maxByOrNull { it.length }
        val next = if (hit != null) {
            current.replaceFirst(hit, "")
                .replace(Regex("\\s*[，,]\\s*[，,]\\s*"), "，")   // 合并残留双逗号
                .trim().trimStart('，', ',').trimEnd('，', ',').trim()
        } else {
            val seg = tokens.first()
            if (current.isBlank()) seg
            else current.trimEnd('，', ',', '。', '.', ';', '；', ' ', '\n') + "，" + seg
        }
        if (isScene) envDesc = next else characterDesc = next
        invalidateFramework(if (isScene) 2 else 1)
        // 卡片自身的绿边/✓ 态就是反馈，不再往对话流插噪音气泡
    }

    // ===== 分镜头操作 =====
    fun addShot() {
        replaceShots(
            shots + Shot(
                id = nextShotId,
                imagePath = null,
                seconds = durationSec.ifBlank { "3" },
                prompt = ""
            )
        )
        nextShotId += 1
    }

    fun updateShot(id: Long, transform: (Shot) -> Shot) {
        replaceShots(shots.map { if (it.id == id) transform(it) else it })
    }

    /** 从相册注入首帧参考图（缩到 1280 长边存 filesDir/director_shots/） */
    val shotPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val target = pendingInjectTarget
        pendingInjectTarget = null
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        val (targetId, isEnd) = target
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    val raw = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                        ?: return@withContext null
                    val maxSide = maxOf(raw.width, raw.height)
                    val bmp = if (maxSide > 1280) {
                        val scale = 1280f / maxSide
                        Bitmap.createScaledBitmap(
                            raw,
                            (raw.width * scale).toInt().coerceAtLeast(1),
                            (raw.height * scale).toInt().coerceAtLeast(1),
                            true
                        )
                    } else raw
                    val dir = File(context.filesDir, "director_shots").apply { mkdirs() }
                    val f = File(dir, if (isEnd) "shot_${targetId}_end.jpg" else "shot_$targetId.jpg")
                    FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    f.absolutePath
                } catch (e: Exception) {
                    null
                }
            }
            if (path == null) error = "图片读取失败，换一张试试"
            else updateShot(targetId) { if (isEnd) it.copy(endImagePath = path) else it.copy(imagePath = path) }
        }
    }

    /** 用本镜提示词 + 总体要素生成首帧（走文生图，画幅跟随全局） */
    fun generateFirstFrame(shot: Shot) {
        if (generatingShotId != null) return
        val p = shot.prompt.trim()
        if (p.isBlank()) {
            error = "先给这个分镜写一句画面提示，再生成首帧"
            return
        }
        com.lo.imagine.data.imageApiConfigurationError(settings)?.let {
            error = it
            return
        }
        generatingShotId = shot.id
        error = null
        val promptText = buildString {
            append(p)
            if (characterDesc.isNotBlank()) append("。人物：").append(characterDesc.trim())
            if (envDesc.isNotBlank()) append("。环境：").append(envDesc.trim())
        }
        val size = when (videoAspect) {
            "9:16" -> "864x1536"
            "1:1" -> "1024x1024"
            else -> "1536x864"
        }
        scope.launch {
            var failMsg: String? = null
            var savedPath: String? = null
            withContext(Dispatchers.IO) {
                try {
                    when (
                        val r = repository.generate(
                            settings = settings,
                            prompt = promptText,
                            negativePrompt = "lowres, blurry, watermark",
                            size = size,
                            count = 1
                        )
                    ) {
                        is ApiResult.Success -> {
                            val bmp = r.images.firstOrNull()?.let { repository.resolveBitmap(it) }
                            if (bmp == null) {
                                failMsg = "首帧返回了空数据（可能被截断）"
                            } else {
                                val dir = File(context.filesDir, "director_shots").apply { mkdirs() }
                                val f = File(dir, "shot_${shot.id}.jpg")
                                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                                savedPath = f.absolutePath
                            }
                        }
                        is ApiResult.Error -> failMsg = r.message ?: "首帧生成失败"
                    }
                } catch (e: Exception) {
                    failMsg = e.message ?: "首帧生成失败"
                }
            }
            generatingShotId = null
            if (failMsg != null) error = failMsg
            if (savedPath != null) updateShot(shot.id) { it.copy(imagePath = savedPath) }
        }
    }

    /** Manual storyboard uses the same selected prompt target, with explicit image-to-shot mapping. */
    fun composeStoryboard() {
        if (shots.isEmpty()) { error = "先添加至少一个分镜"; return }
        if (composing || chatThinking || polishing) return
        com.lo.imagine.data.llmApiConfigurationError(settings)?.let { error = it; return }
        composing = true
        error = null
        val brief = buildDraft()
        val submittedShots = shots.toList()
        scope.launch {
            try {
                val (refImages, shotLines) = withContext(Dispatchers.IO) {
                    val images = mutableListOf<String>()
                    fun reference(path: String?, label: String): String {
                        if (path == null) return ""
                        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 2 })
                            ?: throw IllegalStateException("$label 图片读取失败，请重新选择")
                        try {
                            val bytes = java.io.ByteArrayOutputStream()
                            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 82, bytes)) { "$label 图片编码失败" }
                            images += android.util.Base64.encodeToString(bytes.toByteArray(), android.util.Base64.NO_WRAP)
                            return "$label = 参考图${images.size}；"
                        } finally { bitmap.recycle() }
                    }
                    val lines = submittedShots.mapIndexed { index, shot ->
                        "分镜${index + 1} · ${shot.seconds.ifBlank { "3" }}s：" +
                            reference(shot.imagePath, "首帧") + reference(shot.endImagePath, "尾帧") +
                            "画面：${shot.prompt.ifBlank { "待补充" }}"
                    }
                    images.toList() to lines
                }
                repository.composeDirectorStoryboard(settings, engine, brief, shotLines, refImages)
                    .onSuccess { polished = it; polishedTitle = "${engine.label} 分镜脚本" }
                    .onFailure { error = it.message ?: "分镜脚本生成失败" }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "分镜脚本生成失败"
            } finally { composing = false }
        }
    }
    var restartDialog by remember { mutableStateOf(false) }
    var materialLibraryOpen by remember { mutableStateOf(false) }
    var showStoryboard by remember { mutableStateOf(false) }
    if (materialLibraryOpen) {
        MaterialLibraryDialog(
            settings = settings,
            repository = repository,
            assets = assets,
            onPersist = { persistAssets(it) },
            onDismiss = { materialLibraryOpen = false }
        )
    }
    val busy = chatThinking || polishing || composing || generatingShotId != null
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val listState = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        val compact = imeVisible || maxHeight < 580.dp || LocalDensity.current.fontScale > 1.3f
        Column(Modifier.fillMaxSize().padding(bottom = 8.dp)) {
            DirectorHeader(compact = compact,
                onStoryboard = { if (!busy) { focusManager.clearFocus(); keyboard?.hide(); showStoryboard = true } },
                onMaterials = { if (!busy) { focusManager.clearFocus(); keyboard?.hide(); materialLibraryOpen = true } })
            Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
                if (!imeVisible) {
                    Spacer(Modifier.height(2.dp))
                    DirectorEngineSwitch(engine, enabled = !busy, onSelect = {
                        focusManager.clearFocus(); keyboard?.hide(); onEngineChange(it)
                    })
                    if (!compact) {
                        Text(engine.description, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DirectorStepProgress(interview, enabled = !busy, onRevisit = ::revisitStage)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                        Text(when {
                            chatDone -> "${engine.label} · 提示词已完成"
                            interview.isReview -> "${engine.label} · 确认完整框架"
                            else -> "${if (compact) engine.label + " · " else ""}第 ${interview.stageIndex + 1}/5 阶段 · ${interview.currentStage?.label.orEmpty()}"
                        }, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    if (interview.stageIndex > 0) TextButton(onClick = {
                        revisitStage((interview.stageIndex - 1).coerceAtLeast(0))
                    }, enabled = !busy, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("上一步", fontSize = 12.sp) }
                    TextButton(onClick = { restartDialog = true }, enabled = !busy,
                        contentPadding = PaddingValues(horizontal = 6.dp)) { Text("重开", fontSize = 12.sp) }
                }
                Surface(shape = com.lo.imagine.ui.theme.themedShape(PopRadius.field),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(.7.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(chatMessages) { mi, msg ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.mine) Arrangement.End else Arrangement.Start) {
                                Column(Modifier.fillMaxWidth(if (msg.mine) .88f else 1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (msg.mine) "你" else "导演 · ${engine.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Surface(shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip),
                                        color = if (msg.mine) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainerLow,
                                        contentColor = if (msg.mine) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurface) {
                                        androidx.compose.foundation.text.selection.SelectionContainer {
                                            Text(msg.text, style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(12.dp))
                                        }
                                    }
                                    if (chatDone && !msg.mine && mi == chatMessages.lastIndex) {
                                        TextButton(onClick = { copyText("chat-script", msg.text) }) {
                                            Text(if (copiedKey == "chat-script") "已复制" else "复制 ${engine.label} 提示词")
                                        }
                                    }
                                }
                            }
                        }
                        if (chatThinking) item(key = "thinking") {
                            Row(Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PopBusySpinner(Modifier.size(16.dp))
                                Text(if (interview.isReview) "正在整理成片提示词…" else "正在梳理你的回答…",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (error != null || generationError != null) item(key = "request-error") {
                            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                                Text(error ?: generationError.orEmpty(), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = { if (interview.isReview) finishInterview() else confirmAndContinue() }, enabled = !busy) {
                                    Text("重试本轮")
                                }
                            }
                        }
                        if (interview.stageReady && !interview.isReview && !chatThinking) {
                            // 要点与下一阶段名在组合期定格，避免旧列表项在阶段推进后重算时越界。
                            val pendingSummary = interview.currentSummary
                            val nextStageLabel = interview.nextStageLabel
                            item(key = "stage-summary") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DirectorSummaryCard("本阶段要点 · 等你确认", pendingSummary)
                                    Text("可以在下方补充或纠正，确认后进入下一阶段。",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    DirectorPrimaryAction(
                                        text = nextStageLabel?.let { "确认 · 进入${it}阶段" } ?: "确认本阶段 · 查看完整框架",
                                        enabled = !busy && chatInput.isBlank() && error == null,
                                        onClick = ::confirmStage, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                        if (interview.isReview && !chatDone && !chatThinking) {
                            DIRECTOR_STAGES.forEachIndexed { i, stage ->
                                item(key = "review-${stage.key}") {
                                    DirectorSummaryCard("0${i + 1} · ${stage.label}", interview.summaries[i],
                                        action = if (busy) null else "修改", onAction = { revisitStage(i) })
                                }
                            }
                        }
                    }
                }
                LaunchedEffect(chatMessages.size, chatThinking, interview.stageReady, interview.isReview, error, generationError, compact) {
                    if (chatMessages.isNotEmpty()) {
                        val target = when {
                            chatThinking || error != null || generationError != null -> chatMessages.size
                            interview.stageReady -> chatMessages.size
                            else -> chatMessages.lastIndex
                        }
                        listState.animateScrollToItem(target)
                    }
                }
                if (!chatDone && !interview.isReview) {
                    if (!compact && !busy) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AssetPill(label = "人物", icon = PopPerson, tint = MaterialTheme.colorScheme.primary,
                                selected = characterDesc.isNotBlank(), modifier = Modifier.weight(1f),
                                onClick = { focusManager.clearFocus(); keyboard?.hide(); assetPickerKind = KIND_CHARACTER })
                            AssetPill(label = "环境", icon = PopGallery, tint = MaterialTheme.colorScheme.primary,
                                selected = envDesc.isNotBlank(), modifier = Modifier.weight(1f),
                                onClick = { focusManager.clearFocus(); keyboard?.hide(); assetPickerKind = KIND_SCENE })
                            AssetPill(label = "${durationSec}s", icon = PopClock, tint = MaterialTheme.colorScheme.primary,
                                selected = false, modifier = Modifier.weight(1f),
                                onClick = { focusManager.clearFocus(); keyboard?.hide(); durationDialog = true })
                            AssetPill(label = videoAspect, icon = PopFrame, tint = MaterialTheme.colorScheme.primary,
                                selected = false, modifier = Modifier.weight(1f),
                                onClick = { focusManager.clearFocus(); keyboard?.hide(); aspectDialog = true })
                        }
                    }
                    val useAsset = (interview.stageIndex == 1 && characterDesc.isNotBlank()) ||
                        (interview.stageIndex == 2 && envDesc.isNotBlank())
                    // 无障碍描述在组合期定格成普通字符串：semantics 块是快照通知后才执行的延迟 lambda，
                    // 若在其中直接读 interview.stageIndex 下标，确认最后一阶段进入复核态时会越界崩溃。
                    val answerHint = interview.currentStage?.label
                        ?.let { "回答当前${it}阶段的问题" } ?: "回答导演的问题"
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PopTextField(value = chatInput, onValueChange = { chatInput = it },
                            placeholder = if (interview.stageReady) "补充或纠正本阶段…" else "回答导演的当前问题…",
                            singleLine = false, minLines = 1, maxLines = if (compact) 3 else 4,
                            enabled = !busy,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { confirmAndContinue() }),
                            modifier = Modifier.weight(1f).semantics { contentDescription = answerHint })
                        androidx.compose.material3.FilledIconButton(
                            onClick = ::confirmAndContinue, enabled = !busy && (chatInput.isNotBlank() || useAsset),
                            shape = com.lo.imagine.ui.theme.themedShape(PopRadius.field), modifier = Modifier.size(48.dp)) {
                            if (chatThinking) PopBusySpinner(Modifier.size(18.dp))
                            else PIcon(Icons.AutoMirrored.Outlined.Send,
                                if (chatInput.isBlank() && useAsset) "用已选素材回答" else "发送回答", modifier = Modifier.size(22.dp))
                        }
                    }
                } else if (interview.isReview && !chatDone) {
                    DirectorPrimaryAction(if (chatThinking) "正在生成…" else if (generationError != null) "重试生成 ${engine.label} 提示词" else "确认框架 · 生成 ${engine.label} 提示词",
                        enabled = !busy && interview.canGenerate, onClick = ::finishInterview, modifier = Modifier.fillMaxWidth())
                } else if (chatDone) {
                    DirectorPrimaryAction(if (copiedKey == "polished") "已复制" else "复制 ${engine.label} 提示词",
                        onClick = { copyText("polished", polished) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    if (restartDialog) {
        PopAlertDialog(title = "重新开始 ${engine.label} 工程", onDismissRequest = { restartDialog = false },
            text = { Text("清除当前工程的问答与分镜草稿，从故事阶段重新开始。素材库和其他工程的进度会保留。") },
            confirmLabel = "重新开始", onConfirm = { restartDialog = false; restartInterview() },
            dismissLabel = "继续编辑", onDismiss = { restartDialog = false })
    }

    // ===== 分镜头弹窗（右上角入口） =====
    if (showStoryboard) {
        Dialog(onDismissRequest = { showStoryboard = false }) {
            Surface(
                shape = com.lo.imagine.ui.theme.themedShape(PopRadius.sheet),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.5.dp, celInk()),
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.86f)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "分镜头",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TinyBadge(
                            text = if (composing) "生成中…" else "一键脚本",
                            onClick = { if (!composing) composeStoryboard() }
                        )
                        Spacer(Modifier.width(6.dp))
                        TinyBadge(text = "+分镜", accent = true, onClick = { addShot() })
                        Spacer(Modifier.width(6.dp))
                        TinyBadge(text = "完成", onClick = { showStoryboard = false })
                    }
                    if (shots.isEmpty()) {
                        Text(
                            "把脚本拆成一格格分镜：点左侧上图从相册选首帧、下图选尾帧（可选），写画面定秒数，定稿一键生成脚本——润色模型会读取这些参考图。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .85f)
                        )
                    }
                    shots.forEachIndexed { index, shot ->
                        Surface(
                            shape = com.lo.imagine.ui.theme.themedShape(PopRadius.field),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(com.lo.imagine.ui.theme.themedShape(PopRadius.chip))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                pendingInjectTarget = shot.id to false
                                                shotPicker.launch("image/*")
                                            }
                                    ) {
                                        val path = shot.imagePath
                                        if (path != null) {
                                            AsyncImage(
                                                model = File(path),
                                                contentDescription = "首帧参考",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            PIcon(
                                                PopFilm,
                                                contentDescription = "点选首帧",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f),
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .size(22.dp)
                                            )
                                        }
                                        if (generatingShotId == shot.id) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .45f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                PopBusySpinner(
                                                    modifier = Modifier.size(20.dp),
                                                    tint = androidx.compose.ui.graphics.Color.White
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(com.lo.imagine.ui.theme.themedShape(PopRadius.chip))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                pendingInjectTarget = shot.id to true
                                                shotPicker.launch("image/*")
                                            }
                                    ) {
                                        val ep = shot.endImagePath
                                        if (ep != null) {
                                            AsyncImage(
                                                model = File(ep),
                                                contentDescription = "尾帧参考",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            PIcon(
                                                PopGallery,
                                                contentDescription = "点选尾帧",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .35f),
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .size(19.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "分镜${index + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        PopTextField(
                                            value = shot.seconds,
                                            onValueChange = { v ->
                                                updateShot(shot.id) {
                                                    it.copy(seconds = v.filter { c -> c.isDigit() }.take(3))
                                                }
                                            },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.width(44.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "s",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .clickable { replaceShots(shots.filterNot { it.id == shot.id }) }
                                        ) {
                                            PIcon(
                                                PopTrash,
                                                contentDescription = "删除分镜",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TinyBadge(
                                            text = if (generatingShotId == shot.id) "生成中…" else "生成首帧",
                                            accent = generatingShotId != shot.id,
                                            onClick = {
                                                if (generatingShotId == null) generateFirstFrame(shot)
                                            }
                                        )
                                    }
                                    PopTextField(
                                        value = shot.prompt,
                                        onValueChange = { v -> updateShot(shot.id) { it.copy(prompt = v) } },
                                        placeholder = "本镜画面：发生了什么（动作要具体）",
                                        minLines = 1,
                                        maxLines = 2,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        TinyBadge(
                            text = if (copiedKey == "draft") "已复制" else "生成提示词",
                            accent = copiedKey == "draft",
                            onClick = {
                                draft = buildDraft()
                                copyText("draft", buildDraft())
                            }
                        )
                        TinyBadge(
                            text = when {
                                polishing -> "润色中…"
                                polished.isNotBlank() -> "重新润色"
                                else -> "润色"
                            },
                            onClick = { if (!polishing && draft.isNotBlank()) polish() }
                        )
                        if (polishing) PopBusySpinner(modifier = Modifier.size(17.dp))
                    }
                    error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    if (draft.isNotBlank()) {
                        Text(
                            draft,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (polished.isNotBlank()) {
                        Text(
                            polishedTitle,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            polished,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TinyBadge(
                            text = if (copiedKey == "polished") "已复制" else "复制脚本",
                            accent = copiedKey == "polished",
                            onClick = { copyText("polished", polished) }
                        )
                    }
                }
            }
        }
    }

    // ===== 时长选择弹窗 =====
    if (durationDialog) {
        PopAlertDialog(
            title = "视频时长",
            onDismissRequest = { durationDialog = false },
            icon = PopClock,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    engine.durationSuggestions.forEach { v ->
                        val active = durationSec.trim() == v
                        Surface(
                            onClick = {
                                if (durationSec != v) { durationSec = v; invalidateFramework(4) }
                                durationDialog = false
                            },
                            shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip),
                            color = if (active) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = if (active) BorderStroke(1.5.dp, celInk()) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "$v 秒",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
                            )
                        }
                    }
                }
            },
            confirmLabel = "关闭",
            onConfirm = { durationDialog = false }
        )
    }

    // ===== 画幅选择弹窗 =====
    if (aspectDialog) {
        PopAlertDialog(
            title = "视频画幅",
            onDismissRequest = { aspectDialog = false },
            icon = PopFrame,
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VIDEO_ASPECTS.forEach { a ->
                        val active = videoAspect == a
                        Surface(
                            onClick = {
                                if (videoAspect != a) { videoAspect = a; invalidateFramework(4) }
                                aspectDialog = false
                            },
                            shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip),
                            color = if (active) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = if (active) BorderStroke(1.5.dp, celInk()) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                // 画幅图示：小框按真实比例预览（9:16 高框 / 16:9 宽框 / 1:1 方框）
                                val frameH = 22.dp
                                val frameW = when (a) {
                                    "9:16" -> frameH * 9f / 16f
                                    "1:1" -> frameH
                                    else -> frameH * 16f / 9f
                                }.coerceAtMost(40.dp)
                                Box(
                                    modifier = Modifier
                                        .width(frameW)
                                        .height(frameH)
                                        .clip(com.lo.imagine.ui.theme.themedShape(PopRadius.chip))
                                        .background(
                                            if (active) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f)
                                        )
                                ) {}
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        when (a) {
                                            "9:16" -> "竖屏"
                                            "1:1" -> "方形"
                                            else -> "横屏"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        when (a) {
                                            "9:16" -> "适合手机全屏短视频"
                                            "1:1" -> "适合信息流配图"
                                            else -> "适合宽银幕横构图"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    a,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmLabel = "关闭",
            onConfirm = { aspectDialog = false }
        )
    }

    // ===== 素材选择弹窗（人物/环境） =====
    assetPickerKind?.let { kind ->
        val kindAssets = assets.filter { it.kindOrDefault() == kind }
        PopAlertDialog(
            title = if (kind == KIND_SCENE) "环境素材" else "人物素材",
            onDismissRequest = { assetPickerKind = null },
            icon = if (kind == KIND_SCENE) PopGallery else PopPerson,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (kindAssets.isEmpty()) {
                        Text(
                            "还没有${if (kind == KIND_SCENE) "环境" else "人物"}素材，点击下方添加。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)
                        ) {
                            items(kindAssets, key = { it.id }) { asset ->
                                // 启用态 = 主题色高亮包边；点击切换启用/取消，弹窗保持打开
                                val active = isAssetActive(asset)
                                Surface(
                                    onClick = { toggleAsset(asset) },
                                    shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip),
                                    color = if (active) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(
                                        width = if (active) 2.5.dp else 1.5.dp,
                                        color = if (active) MaterialTheme.colorScheme.primary else celInk()
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.padding(10.dp)
                                    ) {
                                        AsyncImage(
                                            model = File(asset.imagePath),
                                            contentDescription = asset.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(com.lo.imagine.ui.theme.themedShape(PopRadius.chip))
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                asset.name.ifBlank { "未命名" },
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (asset.desc.isNotBlank()) {
                                                Text(
                                                    asset.desc,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Text(
                                            if (active) "✓ 已启用" else "点击启用",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                            color = if (active) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        PIcon(
                                            PopTrash,
                                            contentDescription = "删除素材",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(com.lo.imagine.ui.theme.themedShape(PopRadius.card, pill = true))
                                                .clickable { assetToDelete = asset }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Surface(
                        onClick = {
                            addAssetKind = kind
                            showAddAsset = true
                        },
                        shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.5.dp, celInk()),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                        ) {
                            PIcon(PopImageAdd, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(
                                "添加素材",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            },
            confirmLabel = "关闭",
            onConfirm = { assetPickerKind = null }
        )
    }

    // ===== 添加素材弹窗（叠在素材弹窗之上） =====
    if (showAddAsset) {
        AddAssetDialog(
            settings = settings,
            repository = repository,
            kind = addAssetKind,
            onKindChange = { addAssetKind = it },
            onDismiss = { showAddAsset = false },
            onSave = { name, desc, kind, bytes ->
                scope.launch(Dispatchers.IO) {
                    val id = java.util.UUID.randomUUID().toString()
                    val dir = File(context.filesDir, "director_assets").apply { mkdirs() }
                    val file = File(dir, "$id.jpg")
                    runCatching { file.outputStream().use { it.write(bytes) } }
                        .onSuccess {
                            withContext(Dispatchers.Main) {
                                persistAssets(
                                    assets + DirectorAsset(id, name, desc, file.absolutePath, kind)
                                )
                                showAddAsset = false
                            }
                        }
                        .onFailure { withContext(Dispatchers.Main) { error = "素材图片保存失败" } }
                }
            }
        )
    }

    // ===== 删除素材确认 =====
    assetToDelete?.let { target ->
        PopAlertDialog(
            title = "删除素材",
            onDismissRequest = { assetToDelete = null },
            text = { Text("确定删除“${target.name.ifBlank { "未命名素材" }}”吗？") },
            confirmLabel = "删除",
            confirmContainer = MaterialTheme.colorScheme.error,
            confirmContentColor = MaterialTheme.colorScheme.onError,
            onConfirm = {
                runCatching { File(target.imagePath).delete() }
                persistAssets(assets.filterNot { it.id == target.id })
                assetToDelete = null
            },
            dismissLabel = "取消",
            onDismiss = { assetToDelete = null }
        )
    }
}

/** 添加素材弹窗：选图 + 类型 + 名称 + 外观描述（可 AI 反推） */
@Composable
private fun AddAssetDialog(
    settings: AppSettings,
    repository: ImageRepository,
    kind: String,
    onKindChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (name: String, desc: String, kind: String, bytes: ByteArray) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var desc by rememberSaveable { mutableStateOf("") }
    var pickedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pickedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var reversing by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val bmp = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
                if (bmp != null) {
                    val (scaled, bytes) = ImageUtils.prepareForEdit(bmp, 1600)
                    withContext(Dispatchers.Main) {
                        pickedBitmap = scaled
                        pickedBytes = bytes
                    }
                }
            }
        }
    }

    PopAlertDialog(
        title = "添加素材",
        onDismissRequest = onDismiss,
        icon = PopImageAdd,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = com.lo.imagine.ui.theme.themedShape(PopRadius.field),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.5.dp, celInk()),
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                ) {
                    if (pickedBitmap != null) {
                        AsyncImage(
                            model = pickedBitmap,
                            contentDescription = "素材预览",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(com.lo.imagine.ui.theme.themedShape(PopRadius.field))
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            PIcon(
                                PopImageAdd, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "选择人物三视图 / 场景参考图",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PopChip(
                        selected = kind == KIND_CHARACTER,
                        onClick = { onKindChange(KIND_CHARACTER) },
                        label = "人物",
                        modifier = Modifier.weight(1f)
                    )
                    PopChip(
                        selected = kind == KIND_SCENE,
                        onClick = { onKindChange(KIND_SCENE) },
                        label = "环境",
                        modifier = Modifier.weight(1f)
                    )
                }
                PopTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "名称",
                    placeholder = if (kind == KIND_SCENE) "例如：雨夜霓虹街头" else "例如：白发少女·三视图",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                PopTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = "外观描述",
                    placeholder = if (kind == KIND_SCENE) "场景元素、光线、氛围……" else "发型、瞳色、服装、配饰……",
                    minLines = 3,
                    maxLines = 5,
                    trailingContent = if (pickedBitmap != null) {
                        {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(com.lo.imagine.ui.theme.themedShape(PopRadius.chip))
                                    .clickable(enabled = !reversing) {
                                        reversing = true
                                        scope.launch {
                                            val b64 = android.util.Base64.encodeToString(
                                                ImageUtils.bitmapToJpegBytes(pickedBitmap!!, 85),
                                                android.util.Base64.NO_WRAP
                                            )
                                            repository.reversePrompt(settings, b64, settings.genModel)
                                                .onSuccess { desc = it }
                                                .onFailure { }
                                            reversing = false
                                        }
                                    }
                                    .padding(6.dp)
                            ) {
                                if (reversing) PopBusySpinner(modifier = Modifier.size(14.dp))
                                else PIcon(
                                    PopInspect, contentDescription = "AI 反推外观描述",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (pickedBitmap != null) {
                    Text(
                        "点描述框右上角可让 AI 看图反推外观（需配置润色 LLM）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmLabel = "保存素材",
        confirmEnabled = pickedBytes != null && name.isNotBlank(),
        onConfirm = { onSave(name.trim(), desc.trim(), kind, pickedBytes!!) },
        dismissLabel = "取消",
        onDismiss = onDismiss
    )
}


/**
 * 素材库管理弹窗：人物/环境两个 tab 分开展示，支持添加（复用 AddAssetDialog）与删除。
 * 与导演页「人物/环境」选择弹窗共享同一份素材快照，增删即时互通。
 */
@Composable
fun MaterialLibraryDialog(
    settings: AppSettings,
    repository: ImageRepository,
    assets: List<DirectorAsset>,
    onPersist: (List<DirectorAsset>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(KIND_CHARACTER) }
    var showAdd by remember { mutableStateOf(false) }
    var addKind by rememberSaveable { mutableStateOf(KIND_CHARACTER) }
    var assetToDelete by remember { mutableStateOf<DirectorAsset?>(null) }

    // 与页面共享同一份素材快照：读取由页面统一完成，这里只负责写回，
    // 素材库里的增删立即反映到「人物/环境」选择弹窗，反之亦然
    fun persist(next: List<DirectorAsset>) = onPersist(next)

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
                        "素材库",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                Spacer(Modifier.height(10.dp))
                // ===== 人物 / 环境 Tab =====
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(KIND_CHARACTER to "人物", KIND_SCENE to "环境").forEach { (k, label) ->
                        val sel = tab == k
                        Surface(
                            onClick = { tab = k },
                            shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip),
                            color = if (sel) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (sel) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            border = BorderStroke(1.5.dp, celInk()),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 9.dp)
                            ) {
                                PIcon(
                                    if (k == KIND_CHARACTER) PopPerson else PopGallery,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(label, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                val filtered = assets.filter { it.kindOrDefault() == tab }
                if (filtered.isEmpty()) {
                    Text(
                        "还没有${if (tab == KIND_SCENE) "环境" else "人物"}素材，点下方按钮添加。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                        modifier = Modifier.fillMaxWidth().height(360.dp)
                    ) {
                        items(filtered) { a ->
                            Surface(
                                shape = com.lo.imagine.ui.theme.themedShape(PopRadius.field),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)
                                )
                            ) {
                                Column {
                                    AsyncImage(
                                        model = File(a.imagePath),
                                        contentDescription = a.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(86.dp)
                                            .clip(com.lo.imagine.ui.theme.themedShape(PopRadius.field))
                                    )
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            a.name.ifBlank { "未命名素材" },
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (a.desc.isNotBlank()) {
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                a.desc,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TinyBadge(if (a.kindOrDefault() == KIND_SCENE) "环境" else "人物")
                                            Spacer(Modifier.weight(1f))
                                            com.lo.imagine.ui.PopIconButton(
                                                icon = PopTrash,
                                                contentDescription = "删除素材",
                                                onClick = { assetToDelete = a },
                                                modifier = Modifier.size(24.dp),
                                                iconSize = 13.dp,
                                                iconTint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Surface(
                    onClick = { addKind = tab; showAdd = true },
                    shape = com.lo.imagine.ui.theme.themedShape(PopRadius.field),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    border = BorderStroke(1.5.dp, celInk()),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp)
                    ) {
                        PIcon(PopImageAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "添加${if (tab == KIND_SCENE) "环境" else "人物"}素材",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddAssetDialog(
            settings = settings,
            repository = repository,
            kind = addKind,
            onKindChange = { addKind = it },
            onDismiss = { showAdd = false },
            onSave = { name, desc, kind, bytes ->
                scope.launch(Dispatchers.IO) {
                    val id = java.util.UUID.randomUUID().toString()
                    val dir = File(context.filesDir, "director_assets").apply { mkdirs() }
                    val file = File(dir, "$id.jpg")
                    runCatching { file.outputStream().use { it.write(bytes) } }
                        .onSuccess {
                            withContext(Dispatchers.Main) {
                                persist(assets + DirectorAsset(id, name, desc, file.absolutePath, kind))
                                showAdd = false
                            }
                        }
                }
            }
        )
    }

    assetToDelete?.let { target ->
        PopAlertDialog(
            title = "删除素材",
            onDismissRequest = { assetToDelete = null },
            icon = PopTrash,
            text = { Text("确定删除“${target.name.ifBlank { "未命名素材" }}”吗？") },
            confirmLabel = "删除",
            onConfirm = {
                persist(assets.filterNot { it.id == target.id })
                assetToDelete = null
            },
            dismissLabel = "取消",
            onDismiss = { assetToDelete = null }
        )
    }
}

/** 导演台人物/环境快捷按钮：点击打开对应素材库选择，已选内容单行摘要展示 */
@Composable
private fun AssetPill(
    label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color,
    selected: Boolean, modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val c = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip),
        color = if (selected) c.primaryContainer else c.surface,
        border = BorderStroke(.8.dp, if (selected) c.primary else c.outlineVariant),
        modifier = modifier.heightIn(min = 48.dp)) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            PIcon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = c.onSurface)
        }
    }
}
