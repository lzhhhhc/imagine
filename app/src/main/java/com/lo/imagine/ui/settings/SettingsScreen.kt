package com.lo.imagine.ui.settings


import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.CropFree
import com.lo.imagine.ui.theme.PopRadius
import com.lo.imagine.ui.PIcon
import com.lo.imagine.ui.PopGallery
import com.lo.imagine.ui.PopPencil
import com.lo.imagine.ui.PopKey
import com.lo.imagine.ui.PopPlug
import com.lo.imagine.ui.PopBusySpinner
import com.lo.imagine.ui.PopChip
import com.lo.imagine.ui.PopRefresh
import com.lo.imagine.ui.PopTextField
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lo.imagine.data.AppSettings
import com.lo.imagine.data.CustomPreset
import com.lo.imagine.data.ImageRepository
import com.lo.imagine.data.ModelItem
import com.lo.imagine.data.SettingsRepository
import com.lo.imagine.ui.DropdownField
import com.lo.imagine.ui.ErrorPanel
import com.lo.imagine.ui.InfoHint
import com.lo.imagine.ui.ModelCache
import com.lo.imagine.ui.celInk
import com.lo.imagine.ui.TinyBadge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 当前应用预设的显示名：有名用名；历史遗留的纯换行空名用端点主机；未应用预设返回 null */
private fun apiPresetDisplayName(activeName: String?, baseUrl: String): String? {
    val n = activeName?.trim()
    return when {
        n == null -> null
        n.isNotEmpty() -> n
        else -> baseUrl.trim()
            .removePrefix("https://").removePrefix("http://")
            .substringBefore('/').ifEmpty { null }
    }
}

/** 新建空白预设用的唯一名：被占用就自动追加序号，绝不与已有名冲突（更不会覆盖） */
private fun nextBlankPresetName(existing: Collection<String>, base: String): String {
    val taken = existing.map { it.trim() }.toSet()
    if (base !in taken) return base
    var i = 2
    while ("$base $i" in taken) i++
    return "$base $i"
}

/**
 * 绘图连接参数指纹：URL / Key / 模型 / 协议。
 *
 * 用途是**区分「存储里的连接被本页之外改过」和「本页自己刚保存的回显」**：
 * 创作页右上角可以切换预设，那会把新参数写进存储；本页若还捧着旧草稿，
 * 它的自动保存随后就会把旧值写回去（等于把用户刚切的预设偷偷改回）。
 * 统一 trim 后再拼：只差首尾空格的写入不算外部改动，避免来回互相覆盖。
 */
internal fun connectionFingerprint(
    baseUrl: String,
    apiKey: String,
    model: String,
    editMode: String
): String = listOf(baseUrl.trim(), apiKey.trim(), model.trim(), editMode.trim()).joinToString("\u0000")

/**
 * 「完成并返回」要同步进哪个预设：按当前应用中的预设名（trim 后）匹配。
 *
 * 与「保存修改到「X」」用的是同一套匹配语义——历史数据里存在纯换行的空名，
 * 精确比较会匹配失败，表现为「改了模型但首页还是旧的」。
 * 返回 null 表示没有当前预设（用户走的是未存为预设的自定义连接），此时不做任何写回。
 */
internal fun <T> activePresetForSync(presets: List<T>, activeName: String?, nameOf: (T) -> String): T? {
    val key = activeName?.trim() ?: return null
    return presets.firstOrNull { nameOf(it).trim() == key }
}

/** 预设条目与本页草稿是否已经不一致（不一致才需要写回；只差首尾空白算一致）。 */
internal fun presetDiffersFromDraft(
    storedUrl: String, storedKey: String, storedModel: String,
    draftUrl: String, draftKey: String, draftModel: String,
    storedEditMode: String? = null, draftEditMode: String? = null
): Boolean = storedUrl.trim() != draftUrl.trim() || storedKey.trim() != draftKey.trim() ||
    storedModel.trim() != draftModel.trim() ||
    (storedEditMode != null && draftEditMode != null && storedEditMode != draftEditMode)

@Composable
fun SettingsScreen(
    current: AppSettings,
    repository: SettingsRepository,
    imageRepository: ImageRepository
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val comfyRuntime = remember { com.lo.imagine.data.comfy.ComfyRuntime.get(context) }
    val comfyState by comfyRuntime.repository.state.collectAsStateWithLifecycle()
    var comfyOpen by remember { mutableStateOf(false) }
    if (comfyOpen) com.lo.imagine.ui.studio.comfy.ComfyConnectionDialog { comfyOpen = false }
    // 预设全部由用户自己维护：没有内置平台列表，选择只能从「我保存的预设」里来。
    var baseUrl by rememberSaveable(current.baseUrl) { mutableStateOf(current.baseUrl) }
    var apiKey by rememberSaveable(current.apiKey) { mutableStateOf(current.apiKey) }
    var model by rememberSaveable(current.model) { mutableStateOf(current.model) }
    var editMode by rememberSaveable(current.editMode) { mutableStateOf(current.editMode) }
    var llmBaseUrl by rememberSaveable(current.llmBaseUrl) { mutableStateOf(current.llmBaseUrl) }
    var llmApiKey by rememberSaveable(current.llmApiKey) { mutableStateOf(current.llmApiKey) }
    var llmModel by rememberSaveable(current.llmModel) { mutableStateOf(current.llmModel) }
    var models by remember { mutableStateOf<List<ModelItem>>(emptyList()) }
    var fetching by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    val latestSettings by rememberUpdatedState(current)
    var showLlmKey by remember { mutableStateOf(false) }

    /**
     * 本页最后一次写进存储的「连接参数指纹」。
     * 创作页右上角也能切预设并直接改存储，设置页若只靠自己的草稿，
     * 它的自动保存就会把旧 URL/Key/模型写回去。用它来区分
     * 「存储被别处改过」与「本页自己刚保存的回显」。
     */
    var lastWrittenFingerprint by rememberSaveable {
        mutableStateOf(connectionFingerprint(current.baseUrl, current.apiKey, current.model, current.editMode))
    }

    // ===== 自定义预设（随设置持久化）=====
    var customPresets by remember { mutableStateOf<List<CustomPreset>>(emptyList()) }
    /** 当前应用的预设：档案列表里可把改动一键存回该预设 */
    var activePresetName by remember { mutableStateOf<String?>(null) }
    var presetSaveHint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        customPresets = repository.loadCustomPresets()
            .sortedWith(compareByDescending<CustomPreset> { it.fav }.thenBy { it.name })
        activePresetName = repository.loadActivePreset()
    }

    // ===== LLM 连接档案（与绘图预设同一套交互，独立持久化）=====
    var customLlmPresets by remember { mutableStateOf<List<com.lo.imagine.data.CustomLlmPreset>>(emptyList()) }
    var activeLlmPresetName by remember { mutableStateOf<String?>(null) }
    var llmPresetSaveHint by remember { mutableStateOf<String?>(null) }
    /** 同 lastWrittenFingerprint：语言模型连接的记账，防止旧草稿覆盖别处的改动。 */
    var lastWrittenLlmFingerprint by rememberSaveable {
        mutableStateOf(connectionFingerprint(current.llmBaseUrl, current.llmApiKey, current.llmModel, ""))
    }
    LaunchedEffect(Unit) {
        customLlmPresets = repository.loadCustomLlmPresets()
            .sortedWith(compareByDescending<com.lo.imagine.data.CustomLlmPreset> { it.fav }.thenBy { it.name })
        activeLlmPresetName = repository.loadActiveLlmPreset()
    }
    LaunchedEffect(presetSaveHint) {
        if (presetSaveHint != null) { delay(2200); presetSaveHint = null }
    }
    LaunchedEffect(llmPresetSaveHint) {
        if (llmPresetSaveHint != null) { delay(2200); llmPresetSaveHint = null }
    }

    fun draftSettings() = latestSettings.copy(
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        model = model.trim(),
        editMode = editMode,
        // 润色/翻译 LLM 独立配置：地址、密钥、模型都单独填（与绘图接口互不影响）
        llmBaseUrl = llmBaseUrl.trim(),
        llmApiKey = llmApiKey.trim(),
        llmModel = llmModel.trim(),
        // Only connection fields belong to this draft. UI preferences and templates
        // are saved independently and must never be reset by debounced form saves.
    )

    fun applyCustom(preset: CustomPreset) {
        baseUrl = preset.baseUrl
        apiKey = preset.apiKey
        model = preset.model
        editMode = preset.editMode
        models = emptyList()
        fetchError = null
        saved = false
        // 本页主动写入：记账，免得紧接着被「外部改动」逻辑当成别处改的
        lastWrittenFingerprint = connectionFingerprint(preset.baseUrl, preset.apiKey, preset.model, preset.editMode)
        activePresetName = preset.name
        scope.launch { repository.saveActivePreset(preset.name) }
    }

    /**
     * 存储里的连接参数被本页之外改过时（如创作页右上角切预设），
     * 用新值替换本页草稿——否则本页的自动保存会把旧 URL/Key/模型写回去，
     * 表现就是「预设名变了，URL 还是上次在设置页看到的那份」。
     * 本页自己刚保存的回显指纹一致，不会触发，光标不会被打断。
     */
    LaunchedEffect(
        current.baseUrl, current.apiKey, current.model, current.editMode
    ) {
        val incoming = connectionFingerprint(current.baseUrl, current.apiKey, current.model, current.editMode)
        if (incoming == lastWrittenFingerprint) return@LaunchedEffect
        lastWrittenFingerprint = incoming
        baseUrl = current.baseUrl
        apiKey = current.apiKey
        model = current.model
        editMode = current.editMode
        models = emptyList()
        fetchError = null
        activePresetName = repository.loadActivePreset()
    }

    fun fetchModels() {
        fetching = true
        fetchError = null
        scope.launch {
            val result = imageRepository.fetchModels(draftSettings())
            result.onSuccess { list ->
                models = list
                ModelCache.models = list.mapNotNull { it.id }
                ModelCache.fetchedFor = baseUrl.trim()
            }.onFailure { fetchError = it.message ?: "拉取失败" }
            fetching = false
        }
    }

    var llmModels by remember { mutableStateOf<List<ModelItem>>(emptyList()) }
    var llmFetching by remember { mutableStateOf(false) }
    var llmFetchError by remember { mutableStateOf<String?>(null) }
    fun fetchLlmModels2() {
        llmFetching = true
        llmFetchError = null
        scope.launch {
            val result = imageRepository.fetchLlmModels(draftSettings())
            result.onSuccess { list -> llmModels = list }
                .onFailure { llmFetchError = it.message ?: "拉取失败" }
            llmFetching = false
        }
    }

    fun applyLlm(preset: com.lo.imagine.data.CustomLlmPreset) {
        llmBaseUrl = preset.baseUrl
        llmApiKey = preset.apiKey
        llmModel = preset.model
        llmModels = emptyList()
        llmFetchError = null
        saved = false
        lastWrittenLlmFingerprint = connectionFingerprint(preset.baseUrl, preset.apiKey, preset.model, "")
        activeLlmPresetName = preset.name
        scope.launch { repository.saveActiveLlmPreset(preset.name) }
    }

    /** 与绘图侧同因：语言模型连接被别处改过时，用新值替换本页草稿。 */
    LaunchedEffect(
        current.llmBaseUrl, current.llmApiKey, current.llmModel
    ) {
        val incoming = connectionFingerprint(current.llmBaseUrl, current.llmApiKey, current.llmModel, "")
        if (incoming == lastWrittenLlmFingerprint) return@LaunchedEffect
        lastWrittenLlmFingerprint = incoming
        llmBaseUrl = current.llmBaseUrl
        llmApiKey = current.llmApiKey
        llmModel = current.llmModel
        llmModels = emptyList()
        llmFetchError = null
        activeLlmPresetName = repository.loadActiveLlmPreset()
    }

    /** 把当前连接参数一键写回当前应用的预设（覆盖保存，不改名） */
    fun saveToActivePreset(name: String?) {
        if (name == null) return
        val key = name.trim()
        // 名字按 trim 匹配：历史数据存在纯换行的空名，精确比较会匹配失败，
        // 导致「保存修改到预设」静默失效、模型改动存不进预设、首页选择不生效
        val idx = customPresets.indexOfFirst { it.name.trim() == key }
        if (idx < 0) return
        customPresets = customPresets.mapIndexed { i, it ->
            if (i == idx) {
                it.copy(baseUrl = baseUrl.trim(), apiKey = apiKey.trim(), model = model.trim(), editMode = editMode)
            } else it
        }
        val target = customPresets[idx]
        activePresetName = target.name
        // 写回预设后存储里的连接就是这份，记账避免被判成外部改动
        lastWrittenFingerprint = connectionFingerprint(baseUrl, apiKey, model, editMode)
        scope.launch { repository.saveCustomPresets(customPresets) }
        scope.launch { repository.saveActivePreset(target.name) }
        presetSaveHint = "已保存到「${target.name.trim().ifEmpty { "未命名预设" }}」"
    }

    fun saveToActiveLlmPreset(name: String?) {
        if (name == null) return
        val key = name.trim()
        // 与 saveToActivePreset 同因：按 trim 匹配，避免空名/换行名静默失效
        val idx = customLlmPresets.indexOfFirst { it.name.trim() == key }
        if (idx < 0) return
        customLlmPresets = customLlmPresets.mapIndexed { i, it ->
            if (i == idx) {
                it.copy(baseUrl = llmBaseUrl.trim(), apiKey = llmApiKey.trim(), model = llmModel.trim())
            } else it
        }
        val target = customLlmPresets[idx]
        activeLlmPresetName = target.name
        lastWrittenLlmFingerprint = connectionFingerprint(llmBaseUrl, llmApiKey, llmModel, "")
        scope.launch { repository.saveCustomLlmPresets(customLlmPresets) }
        scope.launch { repository.saveActiveLlmPreset(target.name) }
        llmPresetSaveHint = "已保存到「${name.trim().ifEmpty { "未命名档案" }}」"
    }

    /**
     * 唯一的显式提交出口（弹窗「完成并返回」）：
     * 写全局连接参数，并把同一套地址/Key/模型/协议同步进当前预设。
     * 此前这两个动作是分开的——返回只写全局，预设条目要另外点「保存修改到「X」」，
     * 于是首页预设列表里显示的仍是旧模型，看起来就像没保存。
     */
    fun commit() {
        val s = draftSettings()
        lastWrittenFingerprint = connectionFingerprint(s.baseUrl, s.apiKey, s.model, s.editMode)
        lastWrittenLlmFingerprint = connectionFingerprint(s.llmBaseUrl, s.llmApiKey, s.llmModel, "")
        val imageTarget = activePresetForSync(customPresets, activePresetName) { it.name }
        val llmTarget = activePresetForSync(customLlmPresets, activeLlmPresetName) { it.name }
        // 弹窗随即关闭，页内提示看不到：用 Toast 说明这次返回把改动存到了哪
        val synced = mutableListOf<String>()
        scope.launch {
            repository.saveConnections(s)
            com.lo.imagine.data.TaskScheduler.configure(s.maxParallel)
            if (imageTarget != null && presetDiffersFromDraft(
                    imageTarget.baseUrl, imageTarget.apiKey, imageTarget.model,
                    s.baseUrl, s.apiKey, s.model, imageTarget.editMode, s.editMode)) {
                // 与「保存修改到「X」」一致：按 trim 后的名字定位，且只改第一条同名条目
                val imageKey = imageTarget.name.trim()
                val imageIdx = customPresets.indexOfFirst { it.name.trim() == imageKey }
                if (imageIdx >= 0) customPresets = customPresets.mapIndexed { i, it ->
                    if (i == imageIdx) it.copy(baseUrl = s.baseUrl, apiKey = s.apiKey,
                        model = s.model, editMode = s.editMode) else it
                }
                repository.saveCustomPresets(customPresets)
                repository.saveActivePreset(imageTarget.name)
                presetSaveHint = "已同步到「${imageTarget.name.trim().ifEmpty { "未命名预设" }}」"
                synced += "已同步到「${imageTarget.name.trim().ifEmpty { "未命名预设" }}」"
            }
            if (llmTarget != null && presetDiffersFromDraft(
                    llmTarget.baseUrl, llmTarget.apiKey, llmTarget.model,
                    s.llmBaseUrl, s.llmApiKey, s.llmModel)) {
                val llmKey = llmTarget.name.trim()
                val llmIdx = customLlmPresets.indexOfFirst { it.name.trim() == llmKey }
                if (llmIdx >= 0) customLlmPresets = customLlmPresets.mapIndexed { i, it ->
                    if (i == llmIdx) it.copy(baseUrl = s.llmBaseUrl, apiKey = s.llmApiKey,
                        model = s.llmModel) else it
                }
                repository.saveCustomLlmPresets(customLlmPresets)
                repository.saveActiveLlmPreset(llmTarget.name)
                llmPresetSaveHint = "已同步到「${llmTarget.name.trim().ifEmpty { "未命名档案" }}」"
                synced += "已同步到「${llmTarget.name.trim().ifEmpty { "未命名档案" }}」"
            }
            saved = true
            android.widget.Toast.makeText(context,
                if (synced.isEmpty()) "已保存" else synced.joinToString("；"),
                android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // 自动保存：字段变化后短暂防抖写全局连接参数，无需手动点保存。
    // 预设条目的同步只发生在弹窗「完成并返回」（commit），一条路径，不在此处写预设。
    LaunchedEffect(
        baseUrl, apiKey, model, editMode,
        llmBaseUrl, llmApiKey, llmModel
    ) {
        delay(600)
        val s = draftSettings()
        lastWrittenFingerprint = connectionFingerprint(s.baseUrl, s.apiKey, s.model, s.editMode)
        lastWrittenLlmFingerprint = connectionFingerprint(s.llmBaseUrl, s.llmApiKey, s.llmModel, "")
        repository.saveConnections(s)
        com.lo.imagine.data.TaskScheduler.configure(s.maxParallel)
        saved = true
    }

    // ===== 弹窗状态 =====
    var showApiDialog by remember { mutableStateOf(false) }
    var showLlmDialog by remember { mutableStateOf(false) }
    var promptTemplateOpen by remember { mutableStateOf(false) }

    if (promptTemplateOpen) {
        PromptTemplateDialog(
            settings = current,
            onSave = { updated ->
                scope.launch {
                    repository.savePromptTemplates(updated.reversePromptTemplate, updated.polishPromptTemplate)
                }
            },
            onClose = { promptTemplateOpen = false }
        )
    }

    if (showApiDialog) {
        val apiReady = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
        SettingsConsoleDialog(
            title = "绘图引擎",
            subtitle = "OpenAI 兼容图像接口",
            status = null,
            statusDetail = null,
            connected = apiReady,
            icon = {
                PIcon(
                    Icons.Outlined.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(23.dp)
                )
            },
            onDismiss = { commit(); showApiDialog = false },
            onComplete = { commit(); showApiDialog = false }
        ) {
            PresetDropdown(
                title = "连接预设",
                selectedLabel = activePresetName
                    ?.takeIf { name -> customPresets.any { it.name == name } }
                    ?.let { name -> name.trim().ifEmpty { "未命名预设" } }
                    ?: "自定义连接",
                selectedDetail = "${android.net.Uri.parse(baseUrl).host ?: baseUrl.trim().ifBlank { "未填写地址" }} · ${model.ifBlank { "未选模型" }}",
                options = customPresets.map { p ->
                    p.name.trim().ifEmpty { android.net.Uri.parse(p.baseUrl).host ?: "未命名预设" }
                },
                onPick = { index ->
                    customPresets.getOrNull(index)?.let { applyCustom(it) }
                    // 已填 Key 就顺手把该端点的模型列表拉下来，免得用户再去摸刷新按钮
                    if (apiKey.isNotBlank()) fetchModels()
                },
                newLabel = "＋新建空白预设…",
                onCreateBlank = {
                    val name = nextBlankPresetName(customPresets.map { it.name }, "新预设")
                    val p = CustomPreset(
                        name = name,
                        baseUrl = "",
                        apiKey = "",
                        model = "",
                        editMode = editMode
                    )
                    customPresets = (customPresets + p)
                        .sortedWith(compareByDescending<CustomPreset> { it.fav }.thenBy { it.name })
                    scope.launch { repository.saveCustomPresets(customPresets) }
                    activePresetName = name
                    scope.launch { repository.saveActivePreset(name) }
                    // 新建即空白：清空表单，后续填写只落在新预设上，不动任何已有预设
                    baseUrl = ""
                    apiKey = ""
                    model = ""
                    models = emptyList()
                    fetchError = null
                    saved = false
                    presetSaveHint = "已新建空白预设「$name」并切换过来"
                },
                activeName = activePresetName?.trim()?.ifEmpty { "未命名预设" },
                activeSaveLabel = activePresetName
                    ?.takeIf { name -> customPresets.any { it.name == name } }
                    ?.let { name -> "保存修改到「${name.trim().ifEmpty { "未命名预设" }}」" },
                onSaveToActive = { saveToActivePreset(activePresetName) },
                onRenameActive = { newName ->
                    val old = activePresetName
                    if (old != null) {
                        customPresets = customPresets
                            .filterNot { it.name == old || it.name == newName }
                            .plus(
                                CustomPreset(
                                    name = newName,
                                    baseUrl = baseUrl.trim(),
                                    apiKey = apiKey.trim(),
                                    model = model.trim(),
                                    editMode = editMode
                                )
                            )
                            .sortedWith(compareByDescending<CustomPreset> { it.fav }.thenBy { it.name })
                        scope.launch { repository.saveCustomPresets(customPresets) }
                        activePresetName = newName
                        scope.launch { repository.saveActivePreset(newName) }
                        presetSaveHint = "已重命名为「$newName」"
                    }
                },
                onDeleteActive = {
                    val old = activePresetName
                    if (old != null) {
                        customPresets = customPresets.filterNot { it.name == old }
                        scope.launch { repository.saveCustomPresets(customPresets) }
                        activePresetName = null
                        scope.launch { repository.saveActivePreset(null) }
                        presetSaveHint = "已删除「${old.trim().ifEmpty { "未命名预设" }}」"
                    }
                },
                hint = presetSaveHint
            )

            Spacer(Modifier.height(12.dp))
            SettingsConsoleGroup(title = "连接凭据") {
                PopTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it; saved = false; models = emptyList() },
                    label = "API Base URL",
                    leadingContent = {
                        PIcon(PopPlug, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                PopTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; saved = false },
                    label = "API Key",
                    leadingContent = {
                        PIcon(PopKey, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    },
                    trailingContent = {
                        TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "隐藏" else "显示") }
                    },
                    visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))
            SettingsConsoleGroup(
                title = "模型与协议",
                subtitle = "生图与修图共用当前模型"
            ) {
                ModelPickerField(
                    title = "工作模型",
                    value = model,
                    models = models,
                    onValueChange = { model = it; saved = false },
                    onRefresh = { fetchModels() },
                    refreshing = fetching
                )
                fetchError?.let { message ->
                    ErrorPanel(message) { fetchError = null }
                }
                run {
                    // 协议由用户掌握：不同中转端各不相同（OpenAI 官方=Multipart，多数中转=JSON 带图）
                    Text("修图协议", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        PopChip(
                            selected = editMode == "generations_image",
                            onClick = { editMode = "generations_image"; saved = false },
                            label = "JSON 带图",
                            modifier = Modifier.weight(1f)
                        )
                        PopChip(
                            selected = editMode == "edits_multipart",
                            onClick = { editMode = "edits_multipart"; saved = false },
                            label = "Multipart",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    InfoHint("多数中转/国内平台用「JSON 带图」；OpenAI 官方的 /images/edits 用「Multipart」。选错会 404。")
                }
            }
        }
    }

    if (showLlmDialog) {
        val llmReady = llmBaseUrl.isNotBlank() && llmApiKey.isNotBlank() && llmModel.isNotBlank()
        SettingsConsoleDialog(
            title = "语言工作台",
            subtitle = "独立的 Chat Completions 接口",
            status = if (llmReady) "润色引擎已就绪" else "尚未启用",
            statusDetail = if (llmReady) {
                llmModel.trim()
            } else {
                "配置后用于提示词润色与翻译，不改变绘图连接"
            },
            connected = llmReady,
            icon = {
                PIcon(
                    Icons.Outlined.AutoFixHigh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(23.dp)
                )
            },
            onDismiss = { commit(); showLlmDialog = false },
            onComplete = { commit(); showLlmDialog = false }
        ) {
PresetDropdown(
                title = "连接档案",
                selectedLabel = activeLlmPresetName
                    ?.takeIf { name -> customLlmPresets.any { it.name == name } }
                    ?.let { name -> name.trim().ifEmpty { "未命名档案" } }
                    ?: "自定义连接",
                selectedDetail = "${android.net.Uri.parse(llmBaseUrl).host ?: llmBaseUrl.trim().ifBlank { "未填写地址" }} · ${llmModel.ifBlank { "未选模型" }}",
                options = customLlmPresets.map { p ->
                    p.name.trim().ifEmpty { android.net.Uri.parse(p.baseUrl).host ?: "未命名档案" }
                },
                onPick = { index ->
                    applyLlm(customLlmPresets[index])
                    // 档案自带 Key：选中后直接拉模型列表
                    if (llmApiKey.isNotBlank()) fetchLlmModels2()
                },
                newLabel = "＋新建空白档案…",
                onCreateBlank = {
                    val name = nextBlankPresetName(customLlmPresets.map { it.name }, "新档案")
                    val p = com.lo.imagine.data.CustomLlmPreset(
                        name = name,
                        baseUrl = "",
                        apiKey = "",
                        model = ""
                    )
                    customLlmPresets = (customLlmPresets + p)
                        .sortedWith(compareByDescending<com.lo.imagine.data.CustomLlmPreset> { it.fav }.thenBy { it.name })
                    scope.launch { repository.saveCustomLlmPresets(customLlmPresets) }
                    activeLlmPresetName = name
                    scope.launch { repository.saveActiveLlmPreset(name) }
                    // 新建即空白：清空表单，改动只作用于新档案，不动任何已有档案
                    llmBaseUrl = ""
                    llmApiKey = ""
                    llmModel = ""
                    llmModels = emptyList()
                    llmFetchError = null
                    saved = false
                    llmPresetSaveHint = "已新建空白档案「$name」并切换过来"
                },
                activeName = activeLlmPresetName?.trim()?.ifEmpty { "未命名档案" },
                activeSaveLabel = activeLlmPresetName
                    ?.takeIf { name -> customLlmPresets.any { it.name == name } }
                    ?.let { name -> "保存修改到「${name.trim().ifEmpty { "未命名档案" }}」" },
                onSaveToActive = { saveToActiveLlmPreset(activeLlmPresetName) },
                onRenameActive = { newName ->
                    val old = activeLlmPresetName
                    if (old != null) {
                        customLlmPresets = customLlmPresets
                            .filterNot { it.name == old || it.name == newName }
                            .plus(
                                com.lo.imagine.data.CustomLlmPreset(
                                    name = newName,
                                    baseUrl = llmBaseUrl.trim(),
                                    apiKey = llmApiKey.trim(),
                                    model = llmModel.trim()
                                )
                            )
                            .sortedWith(compareByDescending<com.lo.imagine.data.CustomLlmPreset> { it.fav }.thenBy { it.name })
                        scope.launch { repository.saveCustomLlmPresets(customLlmPresets) }
                        activeLlmPresetName = newName
                        scope.launch { repository.saveActiveLlmPreset(newName) }
                        llmPresetSaveHint = "已重命名为「$newName」"
                    }
                },
                onDeleteActive = {
                    val old = activeLlmPresetName
                    if (old != null) {
                        customLlmPresets = customLlmPresets.filterNot { it.name == old }
                        scope.launch { repository.saveCustomLlmPresets(customLlmPresets) }
                        activeLlmPresetName = null
                        scope.launch { repository.saveActiveLlmPreset(null) }
                        llmPresetSaveHint = "已删除「${old.trim().ifEmpty { "未命名档案" }}」"
                    }
                },
                hint = llmPresetSaveHint
            )

            Spacer(Modifier.height(12.dp))
            SettingsConsoleGroup(
                title = "服务端点",
                subtitle = "语言模型使用独立地址"
            ) {
                PopTextField(
                    value = llmBaseUrl,
                    onValueChange = { llmBaseUrl = it; saved = false },
                    label = "LLM Base URL",
                    placeholder = "例如：https://api.openai.com/v1",
                    leadingContent = {
                        PIcon(PopPlug, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))
            SettingsConsoleGroup(
                title = "访问凭据",
                subtitle = "密钥仅保存在本机设置中"
            ) {
                PopTextField(
                    value = llmApiKey,
                    onValueChange = { llmApiKey = it; saved = false },
                    label = "LLM API Key",
                    leadingContent = {
                        PIcon(PopKey, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    },
                    trailingContent = {
                        TextButton(onClick = { showLlmKey = !showLlmKey }) {
                            Text(if (showLlmKey) "隐藏" else "显示")
                        }
                    },
                    visualTransformation = if (showLlmKey) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))
            SettingsConsoleGroup(
                title = "润色模型",
                subtitle = "从服务端读取模型，或直接输入模型 ID"
            ) {
                ModelPickerField(
                    title = "当前模型",
                    value = llmModel,
                    models = llmModels,
                    onValueChange = { llmModel = it; saved = false },
                    onRefresh = { fetchLlmModels2() },
                    refreshing = llmFetching
                )
                llmFetchError?.let { message ->
                    ErrorPanel(message) { llmFetchError = null }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TinyBadge("提示词润色", accent = true)
                    TinyBadge("翻译", accent = false)
                    TinyBadge("独立额度", accent = false)
                }
            }
        }
    }

    // UI preferences write immediately to their existing keys. API forms keep their own debounce.
    val apiReady = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
    val llmReady = llmBaseUrl.isNotBlank() && llmApiKey.isNotBlank() && llmModel.isNotBlank()
    val apiLabel = apiPresetDisplayName(activePresetName, baseUrl) ?: "自定义连接"
    val llmLabel = activeLlmPresetName?.trim()?.takeIf { it.isNotEmpty() } ?: "自定义连接"
    SettingsPoster(
        appearance = {
            ThemeChooser(current.themeMode) { mode ->
                scope.launch { repository.saveInterface(themeMode = mode) }
            }
            Spacer(Modifier.height(20.dp))
            MoodChooser(current.moodKey) { mood ->
                scope.launch { repository.saveInterface(moodKey = mood) }
            }
        },
        output = {
            SettingsToggleRow(
                icon = Icons.Outlined.CropFree, title = "画质补齐放大",
                description = "分辨率不足时插值放大至目标尺寸",
                checked = current.upscaleEnabled,
                onCheckedChange = { scope.launch { repository.saveInterface(upscaleEnabled = it) } }
            )
            Spacer(Modifier.height(8.dp))
            SettingsToggleRow(
                icon = PopGallery, title = "出图自动存相册",
                description = "完成后自动保存到本地相册",
                checked = current.autoSaveGallery,
                onCheckedChange = { scope.launch { repository.saveInterface(autoSaveGallery = it) } }
            )
            Spacer(Modifier.height(8.dp))
            SettingsActionRow(
                icon = PopPencil, title = "提示词模板",
                subtitle = "反推：${if (current.reversePromptTemplate.isNotBlank()) "自定义" else "内置"}" +
                    " · 润色：${if (current.polishPromptTemplate.isNotBlank()) "自定义" else "内置"}",
                status = "编辑", accent = false, onClick = { promptTemplateOpen = true }
            )
        },
        channels = {
            SettingsConnectionRow(
                icon = PopPlug, title = "ComfyUI",
                subtitle = comfyState.connection.baseUrl.ifBlank { "连接自己的工作流服务器" },
                status = if (!comfyState.ready) "未就绪" else if (comfyState.connection.baseUrl.isBlank()) "待配置" else "已配置",
                ready = comfyState.ready && comfyState.connection.baseUrl.isNotBlank(),
                onClick = { comfyOpen = true }
            )
            Spacer(Modifier.height(8.dp))
            SettingsConnectionRow(
                icon = Icons.Outlined.Image, title = "绘图 API",
                subtitle = "$apiLabel · ${model.ifBlank { "未选模型" }}",
                status = if (apiReady) "已配置" else "待配置", ready = apiReady,
                onClick = { showApiDialog = true }
            )
            Spacer(Modifier.height(8.dp))
            VideoConnectionSettings(repository)
            Spacer(Modifier.height(8.dp))
            SettingsConnectionRow(
                icon = Icons.Outlined.AutoFixHigh, title = "润色 LLM",
                subtitle = "$llmLabel · ${llmModel.ifBlank { "未选模型" }}",
                status = if (llmReady) "已配置" else "待配置", ready = llmReady,
                onClick = { showLlmDialog = true }
            )
        },
        about = {
            val c = MaterialTheme.colorScheme
            Text("绘世 · AI CREATIVE STUDIO", color = c.onSurface, fontSize = 13.sp,
                lineHeight = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("让创作，成为连接世界的方式。", color = c.onSurfaceVariant, fontSize = 11.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(12.dp))
            Text("作品与配置保存在本机", color = c.onSurfaceVariant, fontSize = 11.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(20.dp))
            UpdateCard()
            Spacer(Modifier.height(20.dp))
            ProducerCreditCard()
        }
    )
}

@Composable
private fun SectionHeaderWithAction(
    title: String,
    subtitle: String,
    action: (@Composable () -> Unit)?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 19.dp, bottom = 9.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.invoke()
    }
}

@Composable
private fun ModelPickerField(
    title: String,
    value: String,
    models: List<ModelItem>,
    onValueChange: (String) -> Unit,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false
) {
    val options = (models.mapNotNull { it.id } + value.trim())
        .filter { it.isNotBlank() }
        .distinct()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (options.isNotEmpty()) {
                DropdownField(
                    selected = value.ifBlank { "未选择模型" },
                    options = options,
                    onSelect = { onValueChange(it) },
                    menuHeight = 220,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Surface(
                    shape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "尚未拉取模型",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                    )
                }
            }
            if (onRefresh != null) {
                val refreshShape = com.lo.imagine.ui.theme.themedShape(PopRadius.chip)
                Surface(
                    onClick = onRefresh,
                    shape = refreshShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.5.dp, celInk()),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (refreshing) {
                            PopBusySpinner(modifier = Modifier.size(17.dp))
                        } else {
                            PIcon(
                                PopRefresh,
                                contentDescription = "拉取可用模型",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
        if (models.isEmpty() && value.isBlank()) {
            Text("点右侧刷新图标获取平台模型列表", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Connection rows are defined in SettingsRows.kt.

