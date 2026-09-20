package com.lo.imagine.data

/** 视频连接预设独立于绘图、语言模型和 ComfyUI，不预设服务商或请求协议。 */
data class CustomVideoPreset(
    val name: String,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = ""
)

/** 一次读取/保存整组视频配置，保证当前连接、预设内容和选择始终一致。 */
data class VideoApiSettings(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val presets: List<CustomVideoPreset> = emptyList(),
    val activePresetName: String? = null
) {
    fun normalized(): VideoApiSettings = copy(
        baseUrl = baseUrl.trim(), apiKey = apiKey.trim(), model = model.trim()
    )

    fun syncActivePreset(): VideoApiSettings {
        val index = presets.indexOfFirst { it.name == activePresetName }
        if (index < 0) return this
        val connection = normalized()
        return copy(presets = presets.mapIndexed { i, preset ->
            if (i == index) preset.copy(baseUrl = connection.baseUrl,
                apiKey = connection.apiKey, model = connection.model) else preset
        })
    }

    /** 切换前保存原预设草稿，防止用户在自动保存的防抖期间切换而丢失输入。 */
    fun selectPreset(name: String): VideoApiSettings {
        val synced = syncActivePreset()
        val target = synced.presets.first { it.name == name }
        return synced.copy(baseUrl = target.baseUrl, apiKey = target.apiKey,
            model = target.model, activePresetName = target.name)
    }

    fun createBlankPreset(): VideoApiSettings {
        val synced = syncActivePreset()
        val names = presets.map { it.name }.toSet()
        var name = "新视频预设"
        var index = 2
        while (name in names) name = "新视频预设 ${index++}"
        return synced.copy(baseUrl = "", apiKey = "", model = "",
            presets = synced.presets + CustomVideoPreset(name), activePresetName = name)
    }

    fun renameActivePreset(rawName: String): VideoApiSettings {
        val name = rawName.trim()
        require(name.isNotEmpty()) { "请输入预设名称" }
        require(presets.none { it.name == name && it.name != activePresetName }) { "已有同名预设，请换一个名称" }
        if (activePresetName == null) return this
        val synced = syncActivePreset()
        return synced.copy(presets = synced.presets.map {
            if (it.name == activePresetName) it.copy(name = name) else it
        }, activePresetName = name)
    }

    /** 删除预设后保留当前连接为自定义连接，不自动切到其他服务。 */
    fun deleteActivePreset(): VideoApiSettings = copy(
        presets = presets.filterNot { it.name == activePresetName }, activePresetName = null
    )
}
