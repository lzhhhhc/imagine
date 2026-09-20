package com.lo.imagine.data

/**
 * 可持久化的界面主题。只保留罗德岛终端的明暗双版。
 * id 是 DataStore 中唯一保存值。
 */
enum class ThemeMode(
    val id: String,
    val label: String,
    val description: String
) {
    ARKNIGHTS_LIGHT("ark_light", "罗德岛 · 日间", "清爽 明亮 高效"),
    ARKNIGHTS_DARK("ark_dark", "罗德岛 · 夜间", "沉浸 专注 静谧");

    companion object {
        /**
         * 旧存档兼容：paper/midnight/pixel/bauhaus/anime/arknights 全部迁移到日间版。
         * 只有明确存了 "ark_dark" 的才走暗色。
         */
        fun fromId(id: String): ThemeMode = when (id) {
            "ark_dark" -> ARKNIGHTS_DARK
            else -> ARKNIGHTS_LIGHT  // 包含旧 "arknights" 与其他五个主题
        }
    }
}
