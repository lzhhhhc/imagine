package com.lo.imagine.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/** 用户保存的自定义 API 预设 */
data class CustomPreset(
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val editMode: String,
    /** 爱心收藏：收藏的预设排在列表最前 */
    val fav: Boolean = false
)

/** 用户保存的润色 LLM 预设 */
data class CustomLlmPreset(
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val fav: Boolean = false
)

/** 导演页素材：人物三视图等参考图 + 名称 + 外观描述，写 Seedance 提示词时一键插入 */
data class DirectorAsset(
    val id: String,
    val name: String,
    val desc: String,
    /** 图片副本的本地绝对路径（filesDir/director_assets/ 下） */
    val imagePath: String,
    /** 素材类型：character（人物）/ scene（环境）；旧数据缺省按人物处理 */
    val kind: String? = null
)

/** 创作页画师串预设：名称用于在下拉中区分，内容在生成时拼到提示词最前。 */
data class ArtistPreset(
    val name: String,
    val content: String
)

/** 创作页参数快照：杀进程重进后自动恢复，避免每次重新调参。 */
data class StudioPersist(
    val prompt: String = "",
    val negative: String = "",
    val styleId: String = "none",
    val qualityId: String = "high",
    val size: String = "1024x1024",
    /** 创作页画幅标签：空值表示旧版本存档，恢复时按 size 反推一次做迁移 */
    val aspectLabel: String = "",
    val count: Int = 1,
    val safeNegative: Boolean = true,
    val polishDepthId: String = "medium",
    val polishTemplateId: String = "auto",
    val naiOptions: NaiOptions? = null,
    val naiMode: Boolean = false
)

data class AppSettings(
    val baseUrl: String = "",
    val apiKey: String = "",
    /** 生图与修图共用的唯一模型 */
    val model: String = "",
    /** 修图协议：generations_image（JSON 带图）或 edits_multipart；默认走多数中转的 JSON 路径 */
    val editMode: String = "generations_image",
    // 提示词润色 LLM：与绘图接口完全独立
    val llmBaseUrl: String = "",
    val llmApiKey: String = "",
    val llmModel: String = "",
    /** 同时跑的后台任务上限：1/2/3/4。影响所有生成/修图请求的并发度。 */
    val maxParallel: Int = 1,
    /** 外观主题：只保留罗德岛终端明暗双版，旧主题自动迁移到日间版。 */
    val themeMode: String = ThemeMode.ARKNIGHTS_LIGHT.id,
    /** 画质补齐放大：默认关闭——只保留/展示模型真实输出；开启后上游分辨率不足时插值放大到目标尺寸 */
    val upscaleEnabled: Boolean = false,
        /** 出图后自动写入系统相册（Pictures/Imagine）：默认开启，作品库不受影响 */
    val autoSaveGallery: Boolean = true,
    /** 导入图片的来源：local 本地作品 / ask 每次询问 / gallery 系统相册。 */
    val imageImportSource: String = "ask",
    /** 「界面气质」选择：cool_white / dark_tactic / soft_illust；空 = 默认冷白科技 */
    val moodKey: String = "",
    /**
     * 反推 system 覆盖：留空用内置（[PromptTemplates.defaultReversePromptText]）。
     * 设置页「提示词模板」弹窗可改，运行时会把它当整段 system 发出去。
     */
    val reversePromptTemplate: String = "",
    /** 润色 system 覆盖：留空用内置（[PromptTemplates.defaultPolishPromptText]）。 */
    val polishPromptTemplate: String = "",
    /** 视频服务连接：独立配置，不借用绘图或 LLM 的地址、Key、模型。 */
    val videoBaseUrl: String = "",
    val videoApiKey: String = "",
    val videoModel: String = ""
) {
    /** 兼容旧读取点：genModel 与 editModel 均指向同一模型 */
    val genModel: String get() = model
    val editModel: String get() = model
    val llmReady: Boolean get() = llmBaseUrl.isNotBlank() && llmApiKey.isNotBlank() && llmModel.isNotBlank()
}

class SettingsRepository internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.settingsDataStore)
    fun studioModeFlow(): Flow<String> = store.data.map { it[stringPreferencesKey("studio_mode")] ?: "studio" }
    suspend fun saveStudioMode(route: String) {
        require(route in setOf("studio", "nai", "comfy"))
        store.edit { it[stringPreferencesKey("studio_mode")] = route }
    }
    fun naiWorkspaceFlow(): Flow<NaiWorkspaceConfig> = store.data.map { p ->
        p[stringPreferencesKey("nai_workspace_json")]?.let { com.google.gson.Gson().fromJson(it, NaiWorkspaceConfig::class.java) } ?: NaiWorkspaceConfig()
    }
    suspend fun saveNaiWorkspace(value: NaiWorkspaceConfig) {
        store.edit { it[stringPreferencesKey("nai_workspace_json")] = com.google.gson.Gson().toJson(value) }
    }


    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        @Deprecated("合并进 MODEL，仅用于一次性迁移")
        val GEN_MODEL = stringPreferencesKey("gen_model")
        @Deprecated("合并进 MODEL，仅用于一次性迁移")
        val EDIT_MODEL = stringPreferencesKey("edit_model")
        val EDIT_MODE = stringPreferencesKey("edit_mode")
        val LLM_BASE_URL = stringPreferencesKey("llm_base_url")
        val LLM_API_KEY = stringPreferencesKey("llm_api_key")
        val LLM_MODEL = stringPreferencesKey("llm_model")
        val VIDEO_BASE_URL = stringPreferencesKey("video_base_url")
        val VIDEO_API_KEY = stringPreferencesKey("video_api_key")
        val VIDEO_MODEL = stringPreferencesKey("video_model")
        val VIDEO_PROTOCOL = stringPreferencesKey("video_protocol")
        val VIDEO_PRESETS = stringPreferencesKey("custom_video_presets_json")
        val ACTIVE_VIDEO_PRESET = stringPreferencesKey("active_video_preset_name")
        val MAX_PARALLEL = androidx.datastore.preferences.core.intPreferencesKey("max_parallel")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        /** v2：默认策略改为「保留模型真实输出」；旧键（upscale_enabled，曾默认 true）不再读取 */
        val UPSCALE_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("upscale_enabled_v2")
        val AUTO_SAVE_GALLERY = androidx.datastore.preferences.core.booleanPreferencesKey("auto_save_gallery")
        val IMAGE_IMPORT_SOURCE = stringPreferencesKey("image_import_source")
        val CUSTOM_PRESETS = stringPreferencesKey("custom_presets_json")
        val LLM_CUSTOM_PRESETS = stringPreferencesKey("custom_llm_presets_json")
        val DIRECTOR_ASSETS = stringPreferencesKey("director_assets_json")
        val ACTIVE_PRESET = stringPreferencesKey("active_preset_name")
        /** NAI 通道独立记一份「当前预设」：与首页各选各的，互不影响 */
        val ACTIVE_NAI_PRESET = stringPreferencesKey("active_nai_preset_name")
        val ACTIVE_LLM_PRESET = stringPreferencesKey("active_llm_preset_name")
        val ARTIST_STRING = stringPreferencesKey("artist_string")
        val ARTIST_PRESETS = stringPreferencesKey("artist_presets")
        val NEGATIVE_PRESETS = stringPreferencesKey("negative_presets")
        val STUDIO_STATE = stringPreferencesKey("studio_state_json")
        val STP_PRESETS = stringPreferencesKey("stp_presets_json")
        val ACTIVE_STP = stringPreferencesKey("active_stp_name")
        val PRE_PROMPT = stringPreferencesKey("pre_prompt_json")
        /** 反推 / 润色 system 覆盖（留空 = 内置默认） */
        val REVERSE_PROMPT = stringPreferencesKey("reverse_prompt_template")
        val POLISH_PROMPT = stringPreferencesKey("polish_prompt_template")
        /** 「界面气质」选择：冷白科技 / 深色战术 / 柔和插画（配色与卡片形状，独立于明暗） */
        val MOOD_KEY = stringPreferencesKey("ui_mood_key")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            baseUrl = p[Keys.BASE_URL] ?: "",
            apiKey = p[Keys.API_KEY] ?: "",
            // 一次性迁移：优先新键；老用户取原 gen_model，再退 edit_model（仅迁移路径）
            model = p[Keys.MODEL]
                ?: p[Keys.GEN_MODEL]
                ?: p[Keys.EDIT_MODEL]
                ?: "",
            // 旧版本曾存过 generations_image（当时该分支漏传 image 导致参考图无效），
            // 现已修复为「JSON 图生图（image 字段必传）」，对不支持 /images/edits 的
            // 中转端（如企鹅）这本来就是唯一正确路径，因此不再强制改写存量值。
            editMode = p[Keys.EDIT_MODE] ?: "generations_image",
            llmBaseUrl = p[Keys.LLM_BASE_URL] ?: "",
            llmApiKey = p[Keys.LLM_API_KEY] ?: "",
            llmModel = p[Keys.LLM_MODEL] ?: "",
            videoBaseUrl = p[Keys.VIDEO_BASE_URL] ?: "",
            videoApiKey = p[Keys.VIDEO_API_KEY] ?: "",
            videoModel = p[Keys.VIDEO_MODEL] ?: "",
            maxParallel = (p[Keys.MAX_PARALLEL] ?: 1).coerceIn(1, 4),
            themeMode = ThemeMode.fromId(p[Keys.THEME_MODE] ?: ThemeMode.ARKNIGHTS_LIGHT.id).id,
            upscaleEnabled = p[Keys.UPSCALE_ENABLED] ?: false,
            autoSaveGallery = p[Keys.AUTO_SAVE_GALLERY] ?: true,
            imageImportSource = p[Keys.IMAGE_IMPORT_SOURCE] ?: "ask",
            reversePromptTemplate = p[Keys.REVERSE_PROMPT] ?: "",
            polishPromptTemplate = p[Keys.POLISH_PROMPT] ?: "",
            moodKey = p[Keys.MOOD_KEY] ?: "",
        )
    }

    /** Image/LLM form ownership only: never rewrite video, appearance, output or templates.
     * Video has a single independent writer, saveVideoSettings, so an older image draft
     * cannot clear a newly configured video connection.
     */
    suspend fun saveConnections(settings: AppSettings) {
        store.edit { p ->
            p[Keys.BASE_URL] = settings.baseUrl
            p[Keys.API_KEY] = settings.apiKey
            p[Keys.MODEL] = settings.model
            p[Keys.EDIT_MODE] = settings.editMode
            p[Keys.LLM_BASE_URL] = settings.llmBaseUrl
            p[Keys.LLM_API_KEY] = settings.llmApiKey
            p[Keys.LLM_MODEL] = settings.llmModel
        }
    }

    /** The studio preset picker owns only the image connection. */
    suspend fun saveImageConnection(preset: CustomPreset) {
        store.edit { p ->
            p[Keys.BASE_URL] = preset.baseUrl
            p[Keys.API_KEY] = preset.apiKey
            p[Keys.MODEL] = preset.model
            p[Keys.EDIT_MODE] = preset.editMode
            p[Keys.ACTIVE_PRESET] = preset.name
        }
    }

    suspend fun saveMaxParallel(value: Int) {
        store.edit { it[Keys.MAX_PARALLEL] = value.coerceIn(1, 4) }
    }

    val videoSettings: Flow<VideoApiSettings> = store.data.map { p ->
        VideoApiSettings(
            baseUrl = p[Keys.VIDEO_BASE_URL] ?: "",
            apiKey = p[Keys.VIDEO_API_KEY] ?: "",
            model = p[Keys.VIDEO_MODEL] ?: "",
            presets = p[Keys.VIDEO_PRESETS]?.let {
                gson.fromJson(it, Array<CustomVideoPreset>::class.java).toList()
            } ?: emptyList(),
            activePresetName = p[Keys.ACTIVE_VIDEO_PRESET],
            protocolId = p[Keys.VIDEO_PROTOCOL]
        )
    }

    /** Atomically persist only video keys; never substitute another channel's connection. */
    suspend fun saveVideoSettings(settings: VideoApiSettings) {
        val video = settings.normalized().syncActivePreset()
        store.edit { p ->
            p[Keys.VIDEO_BASE_URL] = video.baseUrl
            p[Keys.VIDEO_API_KEY] = video.apiKey
            p[Keys.VIDEO_MODEL] = video.model
            if (video.protocolId == null) p.remove(Keys.VIDEO_PROTOCOL) else p[Keys.VIDEO_PROTOCOL] = video.protocolId
            p[Keys.VIDEO_PRESETS] = gson.toJson(video.presets)
            val active = video.activePresetName
            if (active == null) p.remove(Keys.ACTIVE_VIDEO_PRESET) else p[Keys.ACTIVE_VIDEO_PRESET] = active
        }
    }

    /** Each control updates only its existing key, so rapid independent selections compose safely. */
    suspend fun saveInterface(themeMode: String? = null, moodKey: String? = null,
        upscaleEnabled: Boolean? = null, autoSaveGallery: Boolean? = null,
        imageImportSource: String? = null) {
        store.edit { p ->
            themeMode?.let { p[Keys.THEME_MODE] = ThemeMode.fromId(it).id }
            moodKey?.let { p[Keys.MOOD_KEY] = UiMood.fromId(it).id }
            upscaleEnabled?.let { p[Keys.UPSCALE_ENABLED] = it }
            autoSaveGallery?.let { p[Keys.AUTO_SAVE_GALLERY] = it }
            imageImportSource?.let { p[Keys.IMAGE_IMPORT_SOURCE] = it }
        }
    }

    suspend fun savePromptTemplates(reverse: String, polish: String) {
        store.edit { p ->
            p[Keys.REVERSE_PROMPT] = reverse
            p[Keys.POLISH_PROMPT] = polish
        }
    }

    suspend fun save(settings: AppSettings) {
        store.edit { p ->
            p[Keys.BASE_URL] = settings.baseUrl
            p[Keys.API_KEY] = settings.apiKey
            p[Keys.MODEL] = settings.model
            p[Keys.EDIT_MODE] = settings.editMode
            p[Keys.LLM_BASE_URL] = settings.llmBaseUrl
            p[Keys.LLM_API_KEY] = settings.llmApiKey
            p[Keys.LLM_MODEL] = settings.llmModel
            p[Keys.VIDEO_BASE_URL] = settings.videoBaseUrl
            p[Keys.VIDEO_API_KEY] = settings.videoApiKey
            p[Keys.VIDEO_MODEL] = settings.videoModel
            p[Keys.MAX_PARALLEL] = settings.maxParallel.coerceIn(1, 4)
            p[Keys.THEME_MODE] = ThemeMode.fromId(settings.themeMode).id
            p[Keys.UPSCALE_ENABLED] = settings.upscaleEnabled
            p[Keys.AUTO_SAVE_GALLERY] = settings.autoSaveGallery
            p[Keys.IMAGE_IMPORT_SOURCE] = settings.imageImportSource
            p[Keys.REVERSE_PROMPT] = settings.reversePromptTemplate
            p[Keys.POLISH_PROMPT] = settings.polishPromptTemplate
            p[Keys.MOOD_KEY] = settings.moodKey
        }
    }

    /** 一次性清理：旧版「前置提示词」已被移除，把存储里那坨文本一并删掉，别留着被误读。 */
    suspend fun clearLegacyPrePrompt() {
        store.edit { it.remove(Keys.PRE_PROMPT) }
    }

    /**
     * 一次性清理：旧版首页画师串（首页已无编辑入口，残留值曾污染每次生成）。
     * 画师串只属于 NAI 工作台自己的配置，首页存储这份文本没有任何合法用途。
     */
    suspend fun clearLegacyArtistString() {
        store.edit { it.remove(Keys.ARTIST_STRING) }
    }

    /** 画师串预设（NAI 工作台与首页旧版共用存储；首页已无入口，读取仅服务 NAI 页）。 */
    fun artistPresetsFlow(): Flow<List<ArtistPreset>> =
        store.data.map { p ->
            val raw = p[Keys.ARTIST_PRESETS]
            if (raw.isNullOrBlank()) {
                emptyList()
            } else {
                runCatching {
                    gson.fromJson(raw, Array<ArtistPreset>::class.java).toList()
                }.getOrElse {
                    // 兼容短暂上线过的「按行分隔」旧格式：整行当内容，名称取前 12 字
                    raw.split("\n").filter { it.isNotBlank() }
                        .map { ArtistPreset(name = it.take(12), content = it) }
                }
            }
        }

    suspend fun saveArtistPresets(list: List<ArtistPreset>) {
        store.edit { it[Keys.ARTIST_PRESETS] = gson.toJson(list) }
    }

    /** 创作页负向词预设（与画师串同构：名称+内容，JSON 存储）。 */
    fun negativePresetsFlow(): Flow<List<ArtistPreset>> =
        store.data.map { p ->
            val raw = p[Keys.NEGATIVE_PRESETS]
            if (raw.isNullOrBlank()) {
                emptyList()
            } else {
                runCatching {
                    gson.fromJson(raw, Array<ArtistPreset>::class.java).toList()
                }.getOrElse { emptyList() }
            }
        }

    suspend fun saveNegativePresets(list: List<ArtistPreset>) {
        store.edit { it[Keys.NEGATIVE_PRESETS] = gson.toJson(list) }
    }

    /** 创作页参数快照：杀进程重进后自动恢复，避免每次重新调参。 */
    fun studioStateFlow(): Flow<StudioPersist?> =
        store.data.map { p ->
            p[Keys.STUDIO_STATE]?.let { raw ->
                runCatching { gson.fromJson(raw, StudioPersist::class.java) }.getOrNull()
            }
        }

    suspend fun saveStudioState(state: StudioPersist) {
        store.edit { it[Keys.STUDIO_STATE] = gson.toJson(state) }
    }

    private val gson = com.google.gson.Gson()
    fun customPresetsFlow(): Flow<List<CustomPreset>> =
        store.data.map { p ->
            val raw = p[Keys.CUSTOM_PRESETS]
            if (raw.isNullOrBlank()) {
                emptyList()
            } else {
                runCatching {
                    gson.fromJson(raw, Array<CustomPreset>::class.java).toList()
                }.getOrDefault(emptyList())
            }
        }

    suspend fun loadCustomPresets(): List<CustomPreset> {
        val raw = store.data.map { it[Keys.CUSTOM_PRESETS] }.firstOrNull() ?: return emptyList()
        return runCatching {
            gson.fromJson(raw, Array<CustomPreset>::class.java).toList()
        }.getOrDefault(emptyList())
    }


    suspend fun saveCustomPresets(list: List<CustomPreset>) {
        store.edit { p ->
            p[Keys.CUSTOM_PRESETS] = gson.toJson(list)
        }
    }

    /** 导演页素材库：人物三视图等参考图（挂起读，调用方给 IO 调度，不再阻塞主线程） */
    suspend fun loadDirectorAssets(): List<DirectorAsset> {
        val raw = store.data.map { it[Keys.DIRECTOR_ASSETS] }.firstOrNull() ?: return emptyList()
        return runCatching {
            gson.fromJson(raw, Array<DirectorAsset>::class.java).toList()
        }.getOrDefault(emptyList())
    }

    suspend fun saveDirectorAssets(list: List<DirectorAsset>) {
        store.edit { p ->
            p[Keys.DIRECTOR_ASSETS] = gson.toJson(list)
        }
    }

    suspend fun loadCustomLlmPresets(): List<CustomLlmPreset> {
        val raw = store.data.map { it[Keys.LLM_CUSTOM_PRESETS] }.firstOrNull() ?: return emptyList()
        return runCatching {
            gson.fromJson(raw, Array<CustomLlmPreset>::class.java).toList()
        }.getOrDefault(emptyList())
    }

    suspend fun saveCustomLlmPresets(list: List<CustomLlmPreset>) {
        store.edit { p ->
            p[Keys.LLM_CUSTOM_PRESETS] = gson.toJson(list)
        }
    }

    /** 当前应用的绘图预设：用于档案列表的“保存修改到「X」” */
    suspend fun saveActivePreset(name: String?) {
        store.edit { p ->
            if (name == null) p.remove(Keys.ACTIVE_PRESET) else p[Keys.ACTIVE_PRESET] = name
        }
    }

    suspend fun loadActivePreset(): String? =
        store.data.map { it[Keys.ACTIVE_PRESET] }.firstOrNull()

    /** NAI 通道的预设选择：与首页的 ACTIVE_PRESET 分开存，两边可各选各的 */
    suspend fun saveActiveNaiPreset(name: String?) {
        store.edit { p ->
            if (name == null) p.remove(Keys.ACTIVE_NAI_PRESET) else p[Keys.ACTIVE_NAI_PRESET] = name
        }
    }

    suspend fun loadActiveNaiPreset(): String? =
        store.data.map { it[Keys.ACTIVE_NAI_PRESET] }.firstOrNull()

    fun activePresetFlow(): Flow<String?> =
        store.data.map { it[Keys.ACTIVE_PRESET] }

    suspend fun saveActiveLlmPreset(name: String?) {
        store.edit { p ->
            if (name == null) p.remove(Keys.ACTIVE_LLM_PRESET) else p[Keys.ACTIVE_LLM_PRESET] = name
        }
    }

    suspend fun loadActiveLlmPreset(): String? =
        store.data.map { it[Keys.ACTIVE_LLM_PRESET] }.firstOrNull()
}