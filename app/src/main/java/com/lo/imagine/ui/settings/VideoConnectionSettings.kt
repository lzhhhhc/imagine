package com.lo.imagine.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.Gson
import com.lo.imagine.data.SettingsRepository
import com.lo.imagine.data.VideoProtocol
import com.lo.imagine.ui.DropdownField
import com.lo.imagine.data.VideoApiSettings
import com.lo.imagine.data.apiEndpointError
import com.lo.imagine.data.apiKeyFormatError
import com.lo.imagine.data.videoApiConfigurationError
import com.lo.imagine.ui.InfoHint
import com.lo.imagine.ui.PIcon
import com.lo.imagine.ui.PopKey
import com.lo.imagine.ui.PopPlug
import com.lo.imagine.ui.PopTextField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val VideoDraftSaver = Saver<VideoApiSettings, String>(
    save = { Gson().toJson(it) },
    restore = { Gson().fromJson(it, VideoApiSettings::class.java) }
)

/** The video form owns only video keys, independent of the image/LLM draft and debounce. */
@Composable
internal fun VideoConnectionSettings(repository: SettingsRepository) {
    val context = LocalContext.current
    val flow = remember(repository) {
        repository.videoSettings.map<VideoApiSettings, Result<VideoApiSettings>?> { Result.success(it) }
            .catch { emit(Result.failure(it)) }
    }
    val result by flow.collectAsStateWithLifecycle(initialValue = null)
    val stored = result?.getOrNull()
    var open by rememberSaveable { mutableStateOf(false) }
    val ready = stored != null && videoApiConfigurationError(stored) == null && VideoProtocol.fromId(stored.protocolId) != null
    SettingsConnectionRow(
        icon = Icons.Outlined.Videocam,
        title = "视频 API",
        subtitle = if (stored == null) "独立的视频服务连接" else
            "${stored.activePresetName ?: "自定义连接"} · ${stored.model.ifBlank { "未填模型" }}",
        status = when {
            result == null -> "读取中"
            result?.isFailure == true -> "读取失败"
            ready -> "已配置"
            else -> "待配置"
        },
        ready = ready,
        onClick = {
            if (stored != null) open = true
            else Toast.makeText(context, if (result?.isFailure == true)
                "视频配置读取失败，请重新进入设置重试" else "正在读取视频配置", Toast.LENGTH_SHORT).show()
        }
    )
    if (open && stored != null) {
        VideoConnectionDialog(stored, repository) { open = false }
    }
}

@Composable
private fun VideoConnectionDialog(
    initial: VideoApiSettings,
    repository: SettingsRepository,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(stateSaver = VideoDraftSaver) { mutableStateOf(initial) }
    var showKey by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var presetError by remember { mutableStateOf<String?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    val writes = remember { Mutex() }
    val ready = videoApiConfigurationError(draft) == null && VideoProtocol.fromId(draft.protocolId) != null

    fun edit(value: VideoApiSettings) {
        if (saving) return
        draft = value
        hint = null
        presetError = null
    }

    // Load exactly once on opening. Storage echoes must not reset the caret or a newer draft.
    LaunchedEffect(draft, saving) {
        if (saving) return@LaunchedEffect
        delay(600)
        try {
            writes.withLock { repository.saveVideoSettings(draft) }
            saveError = null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            saveError = "视频配置保存失败，输入已保留，请点击完成重试"
        }
    }

    fun persist(close: Boolean) {
        if (saving) return
        val snapshot = draft
        saving = true
        saveError = null
        scope.launch {
            try {
                writes.withLock { repository.saveVideoSettings(snapshot) }
                if (close) {
                    Toast.makeText(context, "视频配置已保存", Toast.LENGTH_SHORT).show()
                    onClose()
                } else {
                    hint = "已保存到「${snapshot.activePresetName ?: "自定义连接"}」"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                saveError = "视频配置保存失败，输入已保留，请点击完成重试"
            } finally {
                saving = false
            }
        }
    }

    SettingsConsoleDialog(
        title = "视频 API",
        subtitle = "独立的视频服务连接",
        status = if (ready) "连接信息已填写" else "连接信息待完善",
        statusDetail = "导演台 → 分镜 → 开始视频制作；按所选协议提交并查询任务",
        connected = ready,
        icon = { PIcon(Icons.Outlined.Videocam, null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp)) },
        onDismiss = { persist(close = true) },
        onComplete = { persist(close = true) }
    ) {
        PresetDropdown(
            title = "连接预设",
            selectedLabel = draft.activePresetName ?: "自定义连接",
            selectedDetail = "视频连接单独保存，可切换不同服务与模型",
            options = draft.presets.map { it.name },
            onPick = { index -> draft.presets.getOrNull(index)?.let { edit(draft.selectPreset(it.name)) } },
            newLabel = "＋新建空白预设…",
            onCreateBlank = { edit(draft.createBlankPreset()) },
            activeName = draft.activePresetName,
            activeSaveLabel = draft.activePresetName?.let { "保存修改到「$it」" },
            onSaveToActive = { persist(close = false) },
            onRenameActive = { name ->
                try { edit(draft.renameActivePreset(name)) }
                catch (e: IllegalArgumentException) { presetError = e.message }
            },
            onDeleteActive = { edit(draft.deleteActivePreset()) },
            hint = hint
        )
        presetError?.let { VideoSettingsError(it) }
        Spacer(Modifier.height(12.dp))
        SettingsConsoleGroup(title = "视频协议", subtitle = "按服务商文档选择，与模型能力一致") {
            DropdownField(selected = VideoProtocol.fromId(draft.protocolId)?.label ?: "请选择协议",
                options = VideoProtocol.entries.map { it.label },
                onSelect = { label -> edit(draft.copy(protocolId = VideoProtocol.entries.first { it.label == label }.id)) })
        }
        Spacer(Modifier.height(12.dp))
        SettingsConsoleGroup(title = "服务端点", subtitle = "Grok 填根地址或 /v1；Seedance Ark 填根地址或 /api/v3") {
            PopTextField(
                value = draft.baseUrl,
                onValueChange = { edit(draft.copy(baseUrl = it)) },
                label = "视频 API Base URL",
                placeholder = "https://…",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                leadingContent = { PIcon(PopPlug, null, modifier = Modifier.size(18.dp)) },
                enabled = !saving, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            if (draft.baseUrl.isNotBlank()) apiEndpointError(draft.baseUrl)?.let { VideoSettingsError(it) }
        }
        Spacer(Modifier.height(12.dp))
        SettingsConsoleGroup(title = "访问凭据", subtitle = "使用视频服务对应的密钥") {
            PopTextField(
                value = draft.apiKey,
                onValueChange = { edit(draft.copy(apiKey = it)) },
                label = "视频 API Key",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                leadingContent = { PIcon(PopKey, null, modifier = Modifier.size(18.dp)) },
                trailingContent = {
                    TextButton(onClick = { showKey = !showKey }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (showKey) "隐藏" else "显示")
                    }
                },
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                enabled = !saving, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            apiKeyFormatError(draft.apiKey)?.let { VideoSettingsError(it) }
        }
        Spacer(Modifier.height(12.dp))
        SettingsConsoleGroup(title = "视频模型", subtitle = "直接输入服务商提供的模型 ID") {
            PopTextField(
                value = draft.model,
                onValueChange = { edit(draft.copy(model = it)) },
                label = "视频模型 ID",
                enabled = !saving, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            InfoHint("填写后自动保存；未填完也可以返回，稍后继续。")
        }
        saveError?.let { VideoSettingsError(it) }
        if (saving) Text("正在保存…", style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun VideoSettingsError(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
}
