package com.lo.imagine.data

/** 气质独立于日／夜，沿用 ui_mood_key，空值按默认冷白科技呈现。 */
enum class UiMood(val id: String, val label: String, val description: String) {
    COOL_WHITE("cool_white", "冷白科技", "简洁 · 现代"),
    DARK_TACTIC("dark_tactic", "深色战术", "沉稳 · 专业"),
    SOFT_ILLUST("soft_illust", "柔和插画", "温暖 · 治愈");

    companion object {
        fun fromId(id: String): UiMood = entries.firstOrNull { it.id == id } ?: COOL_WHITE
    }
}
