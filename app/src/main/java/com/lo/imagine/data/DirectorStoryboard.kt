package com.lo.imagine.data

import com.google.gson.JsonParser

/** Shared by editable cards, structured LLM output and video submission. */
data class DirectorShot(
    val id: Long = 0,
    val imagePath: String? = null,
    val endImagePath: String? = null,
    val seconds: String = "3",
    val prompt: String = ""
)

data class DirectorStoryboard(val summary: String, val shots: List<DirectorShot>) {
    fun script(): String = summary + "\n\n" + shots.mapIndexed { i, shot ->
        "【分镜${i + 1} · ${shot.seconds}秒】\n${shot.prompt}"
    }.joinToString("\n\n")
}

fun parseDirectorStoryboard(raw: String, totalSeconds: Int, aspect: String): DirectorStoryboard {
    val obj = JsonParser.parseString(raw.trim().removePrefix("```json").removePrefix("```")
        .removeSuffix("```").trim()).asJsonObject
    fun text(key: String): String {
        val value = obj.get(key)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString && value.asString.isNotBlank()) {
            "分镜缺少 $key，请重试"
        }
        return value.asString.trim()
    }
    require(text("aspect_ratio") == aspect) { "分镜画幅与确认参数不一致，请重试" }
    val summary = text("summary")
    val array = obj.getAsJsonArray("shots") ?: error("导演未返回分镜列表")
    require(array.size() in 1..30) { "分镜数量须为 1–30" }
    val shots = array.mapIndexed { index, item ->
        val shot = item.asJsonObject
        val duration = shot.get("seconds")
        require(duration != null && duration.isJsonPrimitive && duration.asJsonPrimitive.isNumber &&
            Regex("[1-9][0-9]*").matches(duration.asString)) { "分镜${index + 1}时长须为整数秒" }
        val seconds = duration.asString.toIntOrNull()
        require(seconds != null && seconds in 1..120) { "分镜${index + 1}时长无效" }
        val prompt = shot.get("prompt")
        require(prompt != null && prompt.isJsonPrimitive && prompt.asJsonPrimitive.isString && prompt.asString.isNotBlank()) {
            "分镜${index + 1}缺少画面提示词"
        }
        DirectorShot(id = index + 1L, seconds = seconds.toString(), prompt = prompt.asString.trim())
    }
    require(shots.sumOf { it.seconds.toInt() } == totalSeconds) { "分镜时长之和与确认的 ${totalSeconds} 秒不一致，请重试" }
    return DirectorStoryboard(summary, shots)
}

fun directorProductionSystem(engine: DirectorEngine, totalSeconds: Int, aspect: String): String = """
你是 ${engine.fullName} 的分镜导演。根据已确认框架和实际参考图生成可编辑、可逐镜制作的分镜。
${engine.direction}
只输出 JSON 对象：{"summary":"整体视觉与声音设定","aspect_ratio":"$aspect","shots":[{"seconds":$totalSeconds,"prompt":"可直接提交的视频提示词"}]}。
shots 为 1–30 个分镜，每镜 seconds 必须为正整数，之和必须恰好等于 $totalSeconds 秒，画幅必须为 $aspect。
优先使用用户选择的分镜节奏；未要求切镜时用一个完整镜头，不把简单动作拆成过短片段。
每镜 prompt 独立写全主体身份、动作起止、场景光线、景别与运镜、音效或对白、结尾状态及连续性要求。
有参考图时按提供的标签理解其用途；在 prompt 中用素材名称描述身份或环境，不写图片序号占位符，不把人物图或环境图自动当作首尾帧。
只使用实际附带的图片；没有图片时不声称看见或绑定了图片。不伪造用户的选择。
如输入提供手动分镜，严格保持其数量、顺序和每镜秒数，只完善提示词。
JSON 字符串中的换行和引号须正确转义，不输出代码围栏、分析或其它字段。
""".trimIndent()

/** Identity is exclusively the stable ID, never a substring of a description. */
fun selectedDirectorAssets(ids: List<String>, assets: List<DirectorAsset>): List<DirectorAsset> {
    val byId = assets.associateBy { it.id }
    return ids.distinct().map { byId[it] ?: error("已选素材已删除，请重新选择人物或环境图") }
}

fun directorFrameSize(aspect: String): String = when (aspect) {
    "16:9" -> "1536x864"
    "9:16" -> "864x1536"
    "1:1" -> "1024x1024"
    "4:3" -> "1344x1008"
    "3:4" -> "1008x1344"
    "21:9" -> "1512x648"
    "3:2" -> "1440x960"
    "2:3" -> "960x1440"
    else -> error("不支持的画幅：$aspect")
}

/** The image order here is the same as the labels and the eventual wire payload. */
fun directorMultimodalContent(text: String, images: List<String>): Any = if (images.isEmpty()) text else buildList {
    add(mapOf("type" to "text", "text" to text))
    images.forEach { add(mapOf("type" to "image_url", "image_url" to mapOf("url" to "data:image/jpeg;base64,$it"))) }
}
