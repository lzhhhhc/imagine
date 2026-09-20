package com.lo.imagine.data

/** Keeps weighted groups and qualified artist names intact. */
fun naiSegments(text: String): List<String> {
    val parts = mutableListOf<String>()
    val chunk = StringBuilder()
    var depth = 0
    var weighted = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == ':' && text.getOrNull(i + 1) == ':') {
            weighted = Regex("""(?:^|[\s,，])[-+]?\d+(?:\.\d+)?$""").containsMatchIn(chunk.toString())
            if (!weighted) depth = 0
            chunk.append("::")
            i += 2
            continue
        }
        if (c in "{[(") depth++
        if (c in "}])") depth--
        if (c in ",，\n" && depth == 0 && !weighted) {
            chunk.toString().trim().takeIf(String::isNotEmpty)?.let(parts::add)
            chunk.clear()
        } else chunk.append(c)
        i++
    }
    chunk.toString().trim().takeIf(String::isNotEmpty)?.let(parts::add)
    return parts
}

fun naiSyntaxErrors(text: String, artist: Boolean = false): List<String> {
    val errors = mutableListOf<String>()
    if (artist && '|' in text) errors += "画师串不能使用 |，它是多角色分隔符。"
    if (Regex("""\([^\n]*:\s*[-+]?\d+(?:\.\d+)?\)""").containsMatchIn(text))
        errors += "SD 权重 (标签:1.2) 不适用，请改成 1.2::标签::。"
    val stack = mutableListOf<Char>()
    var weighted = false
    var i = 0
    var start = 0
    while (i < text.length) {
        val c = text[i]
        if (c == ':' && text.getOrNull(i + 1) == ':') {
            weighted = Regex("""(?:^|[\s,，])[-+]?\d+(?:\.\d+)?$""").containsMatchIn(text.substring(start, i))
            if (!weighted) stack.removeAll { it == '{' || it == '[' }
            i += 2
            start = i
            continue
        }
        if (c in "{[(") stack += c
        if (c in "}])") {
            val expected = when (c) { '}' -> '{'; ']' -> '['; else -> '(' }
            if (stack.lastOrNull() == expected) stack.removeAt(stack.lastIndex)
            else errors += "括号不匹配：$c"
        }
        if (c == '|') {
            if (weighted || stack.isNotEmpty()) errors += "角色分隔前请先关闭权重和括号。"
            start = i + 1
        }
        i++
    }
    if (weighted) errors += "数值权重未关闭：请补上 ::，避免影响后续内容。"
    if (stack.isNotEmpty()) errors += "括号未闭合，请配对或用 :: 结束强调段。"
    return errors.distinct()
}

data class NaiPromptResult(val positive: String, val negative: String?, val errors: List<String>, val warnings: List<String>)

fun activeNaiArtists(artists: String, options: NaiOptions): String =
    if (!options.artistsEnabled) "" else naiSegments(artists).filterNot { it in options.disabledArtists }.joinToString(", ")

fun assembleNaiPrompt(
    raw: String, artists: String, userNegative: String, style: StylePreset,
    profile: NaiProfile, options: NaiOptions
): NaiPromptResult {
    val artist = activeNaiArtists(artists, options)
    val errors = naiSyntaxErrors(raw) + naiSyntaxErrors(artist, true) + naiSyntaxErrors(userNegative)
    val sections = raw.split('|')
    val base = naiSegments(sections.first())
    val dataset = base.filter { it == "fur dataset" || it == "background dataset" }
    // 正文保持用户书写顺序（固定前置 → 主体 → 固定后置），不再把 1girl / solo 这类身份词抽出来提前，
    // 否则画师串会被挤到身份词之后，用户在「检查最终请求」里看到的就是画师串排在后面。
    val content = base.filterNot { it in dataset }
    val artistParts = naiSegments(artist)
    // 注入顺序：数据集前缀 → 画师串（对齐参考项目「前置前」注入位）→ 用户书写的固定前置/主体/固定后置 → 质量词
    // Dedup identical complete segments only, never flatten scopes or change weights.
    val positiveBase = (dataset + artistParts + content +
        (if (options.styleEnabled) naiSegments(style.suffix) else emptyList()) +
        naiSegments(naiQualityTags(profile, options.quality))).filter(String::isNotBlank).distinct().joinToString(", ")
    val positive = (listOf(positiveBase) + sections.drop(1)).joinToString(" | ")
    val negative = (naiSegments(userNegative) +
        (if (options.styleEnabled) naiSegments(style.negative) else emptyList()) +
        naiSegments(naiNegativeTags(profile, options.negative))).distinct().joinToString(", ").ifBlank { null }
    val warnings = mutableListOf<String>()
    if (options.quality != "off") warnings += "质量预设可能改变画风；请确认中转未再次自动注入。包含 no text。"
    if (profile == NaiProfile.V45_CURATED && options.quality != "off") warnings += "4.5 Curated 官方质量预设包含 -0.8::feet:: 与 rating:general。"
    if ('|' in raw) warnings += "保留多角色 | 语法；需中转支持解析，本应用不宣称已支持原生角色字段。"
    if (artist.isNotBlank() && options.styleEnabled && style.suffix.isNotBlank()) warnings += "画师串与通用风格同时启用，可能互相影响。"
    val positiveTags = naiSegments(sections.first()).map(String::lowercase)
    val negativeTags = naiSegments(negative.orEmpty()).map(String::lowercase)
    val conflicts = positiveTags.intersect(negativeTags.toSet())
    if (conflicts.isNotEmpty()) warnings += "正负向重复：${conflicts.joinToString()}（未自动删除）"
    if ("no text" in naiSegments(positiveBase) && Regex("text|lettering|sign|文字|招牌", RegexOption.IGNORE_CASE).containsMatchIn(raw)) warnings += "画面文字需求可能与 no text 冲突。"
    if (raw.contains("soft focus", true) && negative.orEmpty().contains("blurry")) warnings += "柔焦可能受 blurry 负面词抑制。"
    if (artistParts.size > 3) warnings += "画师较多，建议用少量画师逐个对照；权重不是混合百分比。"
    return NaiPromptResult(positive, negative, errors.distinct(), warnings.distinct())
}
