package com.lo.imagine.data

/**
 * 创作辅助数据：风格预设、快捷修图指令、灵感库、画幅。
 * 全部本地计算，不额外消耗 API 额度。
 */

data class StylePreset(
    val id: String,
    val label: String,
    val emoji: String,
    /** 追加到正向提示词后的修饰语 */
    val suffix: String,
    val negative: String = ""
)

/** 润色深度：轻=微调措辞；中=适度扩写；深=重组为分组式标签提示词 */
enum class PolishDepth(val id: String, val label: String, val desc: String) {
    LIGHT("light", "轻", "微调措辞，不改结构"),
    MEDIUM("medium", "中", "适度扩写细节"),
    DEEP("deep", "深", "按目标模型最佳实践重组")
}

/** 润色目标模型模板：不同生图模型的提示词最佳实践不同，润色时按模板改写输出形态。 */
enum class PolishTemplate(val id: String, val label: String, val desc: String) {
    AUTO("auto", "自动", "跟随当前生成模型"),
    BANANA("banana", "Banana", "Google · 自然语言简报"),
    IMAGE2("image2", "Image 2", "OpenAI · 场景→主体→约束"),
    NOVELAI("novelai", "NovelAI", "Danbooru 标签流"),
    GROK("grok", "Grok", "xAI · 简报式·图内文字原样")
}

/** 从生成模型名推断润色模板；无法识别返回 null（走通用模板）。 */
fun detectPolishTemplate(modelName: String): PolishTemplate? {
    val m = modelName.trim().lowercase()
    return when {
        m.contains("banana") -> PolishTemplate.BANANA
        m.contains("grok") || m.contains("aurora") -> PolishTemplate.GROK
        m.contains("novelai") || m.contains("nai-diff") || m.startsWith("nai") -> PolishTemplate.NOVELAI
        m.contains("image") || m.contains("dall") -> PolishTemplate.IMAGE2
        else -> null
    }
}

val STYLE_PRESETS = listOf(
    StylePreset("none", "无风格", "🚫", ""),
    StylePreset(
        "cinematic", "电影感", "🎬",
        "cinematic lighting, film grain, shallow depth of field, dramatic composition, 35mm photography, color graded",
        "flat lighting, snapshot"
    ),
    StylePreset(
        "anime", "日系动漫", "🌸",
        "anime style, clean line art, vibrant cel shading, detailed eyes, studio anime key visual",
        "photorealistic, 3d render"
    ),
    StylePreset(
        "watercolor", "水彩插画", "🎨",
        "watercolor painting, soft washes, textured paper, delicate ink outlines, artistic bleed",
        "harsh edges, digital noise"
    ),
    StylePreset(
        "3d", "3D 渲染", "🧊",
        "3d render, octane render, soft global illumination, subsurface scattering, clay material, product shot",
        "flat 2d, sketch"
    ),
    StylePreset(
        "product", "产品摄影", "📦",
        "professional product photography, seamless studio backdrop, softbox lighting, crisp reflections, commercial quality",
        "cluttered background, harsh shadows"
    ),
    StylePreset(
        "portrait", "人像写真", "👤",
        "portrait photography, 85mm lens, creamy bokeh, natural skin texture, rim light, editorial retouch",
        "deformed hands, extra fingers, plastic skin"
    ),
    StylePreset(
        "ink", "国风水墨", "🖌️",
        "chinese ink painting, xuan paper texture, negative space composition, calligraphic brush strokes, elegant restraint",
        "western cartoon, neon"
    ),
    StylePreset(
        "cyberpunk", "赛博朋克", "🌃",
        "cyberpunk aesthetic, neon signage, rain slicked streets, volumetric haze, high contrast teal and magenta",
        "daylight, pastoral"
    ),
    StylePreset(
        "minimal", "极简设计", "⚪",
        "minimalist design, generous negative space, limited palette, geometric balance, swiss poster aesthetic",
        "busy detail, ornate"
    ),
    StylePreset(
        "pixel", "像素艺术", "👾",
        "pixel art, 16-bit sprite aesthetic, limited palette, crisp pixel edges, retro game art",
        "smooth gradient, blur"
    ),
    StylePreset(
        "figure", "手办模型", "🧸",
        "collectible figure, pvc figurine, glossy paint finish, display base, studio turntable lighting",
        "flat illustration"
    ),
    StylePreset(
        "oil", "油画", "🖌️",
        "oil painting, visible brush strokes, rich impasto texture, classical color palette, canvas texture",
        "digital flat, thin lines"
    ),
    StylePreset(
        "sketch", "铅笔素描", "✏️",
        "pencil sketch, hand drawn line work, graphite shading, paper texture, monochrome",
        "color, painting"
    ),
    StylePreset(
        "steam", "蒸汽朋克", "⚙️",
        "steampunk aesthetic, brass gears, victorian machinery, steam, warm workshop lighting, intricate mechanical details",
        "modern, plastic"
    ),
    StylePreset(
        "ukiyoe", "浮世绘", "🌊",
        "ukiyo-e woodblock print, japanese traditional art, bold outlines, flat color blocks, wave patterns, washi paper texture",
        "photorealistic, 3d render"
    ),
    StylePreset(
        "scifi", "科幻概念", "🚀",
        "sci-fi concept art, futuristic technology, cinematic lighting, highly detailed worldbuilding, epic scale",
        "vintage, low tech"
    ),
    StylePreset(
        "dark", "暗黑奇幻", "🧛",
        "dark fantasy art, moody atmosphere, dramatic chiaroscuro, intricate gothic details, epic composition",
        "bright, cheerful, pastel"
    ),
    StylePreset(
        "cute", "萌系 Q 版", "🍡",
        "kawaii chibi style, adorable proportions, pastel colors, soft shading, clean lines",
        "realistic proportions, dark tones"
    ),
    StylePreset(
        "retro", "复古胶片", "📷",
        "retro film photography, kodak portra color, warm tones, film grain, analog aesthetic, soft halation",
        "digital clean, hdr"
    ),
    StylePreset(
        "papercut", "剪纸艺术", "✂️",
        "paper cutout art, layered paper craft, delicate shadows, vibrant flat colors, intricate cut details",
        "photorealistic, 3d"
    ),
    StylePreset(
        "neon", "霓虹灯牌", "💡",
        "neon light sign aesthetic, glowing neon tubes, dark background, vibrant colors, reflections, night scene",
        "daylight, flat lighting"
    ),
    StylePreset(
        "ceramic", "陶瓷手办", "🏺",
        "ceramic figurine, glazed porcelain texture, studio product lighting, glossy finish, display piece",
        "sketch, rough texture"
    ),
    StylePreset(
        "gothic", "哥特建筑", "⛪",
        "gothic architecture, ornate stone carvings, dramatic light rays, misty atmosphere, cathedral interior",
        "minimal, modern glass"
    )
)

/** 画质增强档位：目标长边像素 + 质量提示词 + 防劣化词 */
data class QualityTier(
    val id: String,
    val label: String,
    val suffix: String,
    val negative: String = "",
    /** 目标长边像素：上游忽略尺寸时由 App 端放大兜底到该值 */
    val longEdge: Int = 1024,
    /** UI 说明文案 */
    val desc: String = ""
)

val QUALITY_TIERS = listOf(
    QualityTier(
        id = "off", label = "1K",
        suffix = "",
        longEdge = 1024,
        desc = "长边 1024"
    ),
    QualityTier(
        id = "high", label = "1.5K",
        suffix = "highly detailed, sharp focus, intricate details, professional quality",
        longEdge = 1536,
        desc = "长边 1536"
    ),
    QualityTier(
        id = "master", label = "2K",
        suffix = "masterpiece, best quality, ultra detailed, intricate details, award winning, perfect composition, sharp focus",
        negative = "worst quality, low quality, jpeg artifacts, blurry",
        longEdge = 2048,
        desc = "长边 2048"
    ),
    QualityTier(
        id = "ultra4k", label = "4K",
        suffix = "masterpiece, best quality, 4k uhd, extremely detailed, intricate micro details, razor sharp focus, professional color grading",
        negative = "worst quality, low quality, jpeg artifacts, blurry, soft focus",
        longEdge = 4096,
        desc = "长边 4096 · 上游不足时保留真实输出"
    )
)

/** 按画幅与画质档位计算最终输出分辨率：以「长边」为目标，短边按比例、对齐到 64 的倍数 */
fun resolveSize(aspect: AspectOption, quality: QualityTier): String =
    aspect.sizeFor(quality.longEdge)

/** 通用负向词，一键开启 */
const val SAFE_NEGATIVE =
    "lowres, blurry, jpeg artifacts, watermark, text, signature, bad anatomy, deformed hands, extra limbs, worst quality"

/** 画幅：size 为标准档（长边1024）基准；任意档位分辨率经 sizeFor() 计算 */
data class AspectOption(
    val label: String,
    val ratioW: Float,
    val ratioH: Float
) {
    /** 按给定长边计算比例精确、对齐 64 的宽高 */
    fun sizeFor(longEdge: Int): String {
        val ratio = (ratioW / ratioH).toDouble()
        fun align(v: Double) = (((v / 64.0) + 0.5).toInt() * 64).coerceAtLeast(256)
        return if (ratio >= 1.0) {
            val w = longEdge
            "${w}x${align(w / ratio)}"
        } else {
            val h = longEdge
            "${align(h * ratio)}x$h"
        }
    }

    /** 标准档基准尺寸（旧字段兼容，长边1024） */
    val size: String get() = sizeFor(1024)
}

val ASPECT_OPTIONS = listOf(
    AspectOption("1:1", 1f, 1f),
    AspectOption("3:4", 3f, 4f),
    AspectOption("9:16", 9f, 16f),
    AspectOption("4:3", 4f, 3f),
    AspectOption("16:9", 16f, 9f),
    AspectOption("21:9", 21f, 9f)
)

/** 修图请求尺寸只由界面上的画幅和画质决定，不从原图或旧的像素字符串反推。 */
fun editOutputPixels(aspectLabel: String, qualityId: String): String {
    val aspect = ASPECT_OPTIONS.firstOrNull { it.label == aspectLabel } ?: ASPECT_OPTIONS.first()
    val quality = QUALITY_TIERS.firstOrNull { it.id == qualityId } ?: QUALITY_TIERS.first { it.id == "high" }
    return aspect.sizeFor(quality.longEdge)
}

/** 根据原图宽高匹配最接近的画幅选项，用于修图时读取图片画幅 */
fun matchAspect(width: Int, height: Int): AspectOption {
    if (width <= 0 || height <= 0) return ASPECT_OPTIONS.first()
    val ratio = width.toFloat() / height.toFloat()
    return ASPECT_OPTIONS.minByOrNull { kotlin.math.abs(it.ratioW / it.ratioH - ratio) }
        ?: ASPECT_OPTIONS.first()
}

/** 从最终输出尺寸（如 2048x1152）反查画幅选项。
 *  修图页的画幅/画质选择会生成任意档位尺寸（长边 2048/4096 等），
 *  不能再用「标准档 1024 基准 size」精确匹配，改为按比例容差反查。 */
fun aspectBySize(size: String): AspectOption? {
    val parts = size.split("x")
    val w = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val h = parts.getOrNull(1)?.toIntOrNull() ?: return null
    if (w <= 0 || h <= 0) return null
    val ratio = w.toFloat() / h.toFloat()
    return ASPECT_OPTIONS.minByOrNull { kotlin.math.abs(it.ratioW / it.ratioH - ratio) }
        ?.takeIf { kotlin.math.abs(it.ratioW / it.ratioH - ratio) <= 0.02f }
}

// 修图快捷指令：「预设」即用户口中的“洗图/局部修”等场景化动作。
// 改 / 留 / 画 三段式让上游模型更易遵循；副描述用于 UI 提示用户效果。
data class EditAction(
    val id: String,
    val category: String,   // 修复 | 风格 | 场景 | 局部
    val label: String,
    val emoji: String,
    val short: String,      // 短副标题（1 行）
    val prompt: String
)

val EDIT_ACTIONS = listOf(
    // ===== 修复（洗图/修瑕） =====
    EditAction(
        id = "denoise", category = "修复", label = "一键洗图", emoji = "🧼",
        short = "去噪点/压糊，保留构图",
        prompt = "clean up this photo: remove noise, JPEG artifacts, mild blur and dust, keep the composition, subject identity, lighting direction, colors and overall mood completely unchanged, only improve clarity and cleanliness"
    ),
    EditAction(
        id = "upscale", category = "修复", label = "提画质", emoji = "✨",
        short = "保留一切只更清晰",
        prompt = "enhance this image to high resolution: sharpen details, improve lighting and clarity, remove noise, upscale micro-textures (skin, hair, fabric, foliage), keep everything else completely identical"
    ),
    EditAction(
        id = "face_fix", category = "修复", label = "修脸部", emoji = "💧",
        short = "磨皮/对称，不改长相",
        prompt = "subtly retouch the face: even out skin tone while keeping natural texture, soften under-eye darkness, fix small asymmetries, do not change identity, age, gender or face shape"
    ),
    EditAction(
        id = "color_fix", category = "修复", label = "校色", emoji = "🎨",
        short = "白平衡/对比/饱和",
        prompt = "correct the color: fix white balance, restore natural skin tone, improve global contrast and saturation, do not change composition, subject or background content"
    ),
    EditAction(
        id = "deblur", category = "修复", label = "去模糊", emoji = "🔍",
        short = "提升对焦感",
        prompt = "deblur this image as if it was shot with a steadier hand and proper focus: recover edges and micro-detail, keep colors, composition and subject identity identical"
    ),
    EditAction(
        id = "old_photo", category = "修复", label = "老照片修复", emoji = "📜",
        short = "刮痕/泛黄/褪色",
        prompt = "restore this old/damaged photo: remove scratches, dust, yellowing, fading and minor creases, reconstruct missing details, keep the original scene and people, output looks like a modern clean photograph of the same moment"
    ),
    EditAction(
        id = "shadow", category = "修复", label = "去阴影", emoji = "☀️",
        short = "降暗部提细节",
        prompt = "lift harsh shadows and recover detail in dark areas, mildly increase exposure on the subject, keep highlights from clipping, do not change the scene"
    ),
    EditAction(
        id = "watermark", category = "修复", label = "去水印", emoji = "🧽",
        short = "清除文字/角标",
        prompt = "remove text, watermark, logo, timestamp and UI artifacts from the image, reconstruct the underlying texture, keep the rest of the image completely intact"
    ),

    // ===== 风格（转绘/画风） =====
    EditAction(
        id = "anime", category = "风格", label = "动漫化", emoji = "🌸",
        short = "日系动漫重绘",
        prompt = "turn this image into anime illustration style, clean line art, vibrant cel shading, detailed eyes, keep the original composition, pose, color palette and subject identity"
    ),
    EditAction(
        id = "sketch", category = "风格", label = "转手绘", emoji = "✏️",
        short = "铅笔素描",
        prompt = "convert this photo into a delicate pencil sketch, hand drawn line work, paper texture, monochrome, keep composition and subject"
    ),
    EditAction(
        id = "oil", category = "风格", label = "油画风", emoji = "🖼️",
        short = "古典油画",
        prompt = "repaint this image as an oil painting, visible brush strokes, rich impasto texture, classical color palette, keep the composition and subject"
    ),
    EditAction(
        id = "watercolor", category = "风格", label = "水彩风", emoji = "💧",
        short = "透明水彩",
        prompt = "restyle this image as a watercolor painting, soft transparent washes, paper grain visible, delicate ink outlines, keep composition and subject"
    ),
    EditAction(
        id = "cyberpunk", category = "风格", label = "赛博朋克", emoji = "🌃",
        short = "霓虹/雨夜",
        prompt = "restyle this image with cyberpunk aesthetic, neon lighting, rain reflections, teal and magenta contrast, futuristic atmosphere, keep the subject"
    ),
    EditAction(
        id = "ink", category = "风格", label = "国风水墨", emoji = "🖌️",
        short = "水墨/留白",
        prompt = "restyle this image as traditional Chinese ink painting, xuan paper texture, generous negative space, calligraphic brush strokes, elegant restraint, keep the main subject"
    ),
    EditAction(
        id = "3d", category = "风格", label = "3D 渲染", emoji = "🧊",
        short = "Octane/质感",
        prompt = "restyle this image as a 3D render, octane-style soft global illumination, subsurface scattering, clay-like material, keep composition and subject"
    ),
    EditAction(
        id = "three_view", category = "风格", label = "制作三视图", emoji = "📐",
        short = "角色设定图（正面/侧面/背面）",
        prompt = "turn this character into a clean character turnaround model sheet: three full-body views of the same character side by side (front view, side view, back view), same design, same proportions, same outfit and color palette, neutral standing pose, simple plain background, uniform lighting, orthographic style, high detail, game art reference sheet"
    ),

    // ===== 场景（环境/光影） =====
    EditAction(
        id = "bg_swap", category = "场景", label = "换背景", emoji = "🏞️",
        short = "换为樱花林",
        prompt = "replace the background with a beautiful cherry blossom garden, soft bokeh, natural light matching the subject, keep the main subject completely unchanged (identity, pose, expression, clothing, edges)"
    ),
    EditAction(
        id = "bg_remove", category = "场景", label = "去背景", emoji = "✂️",
        short = "纯白/干净抠图",
        prompt = "remove the background completely, keep only the main subject on a clean pure white background, sharp clean cutout edges, no halo, keep subject identity"
    ),
    EditAction(
        id = "morning", category = "场景", label = "变清晨", emoji = "🌅",
        short = "暖光/长影",
        prompt = "change the lighting to soft golden morning sunlight, warm color temperature, gentle long shadows, keep composition, subject identity and overall mood"
    ),
    EditAction(
        id = "night", category = "场景", label = "变夜景", emoji = "🌙",
        short = "月光/冷调",
        prompt = "change the scene to night time, moonlight and ambient city lights, cool blue tones, stars or bokeh, keep composition, subject identity and pose"
    ),
    EditAction(
        id = "snow", category = "场景", label = "下雪", emoji = "❄️",
        short = "飘雪/冬日",
        prompt = "add gentle falling snow and a wintry atmosphere, soft cool lighting, breath-like mist from the subject's mouth if relevant, keep composition and subject"
    ),
    EditAction(
        id = "rain", category = "场景", label = "下雨", emoji = "🌧️",
        short = "雨丝/水花",
        prompt = "add rain atmosphere, visible rain streaks, wet surface reflections, slightly darker sky, keep composition, subject identity and pose"
    ),
    EditAction(
        id = "dof", category = "场景", label = "加景深", emoji = "📷",
        short = "背景虚化",
        prompt = "add shallow depth of field, blur the background with creamy bokeh, keep the main subject tack sharp, preserve colors"
    ),
    EditAction(
        id = "outpaint", category = "场景", label = "扩展画面", emoji = "🔭",
        short = "画幅外延",
        prompt = "extend and outpaint the scene naturally beyond current borders in all four sides, keep style, lighting, subject and overall mood consistent, no obvious seams"
    ),
    EditAction(
        id = "season_summer", category = "场景", label = "改夏季", emoji = "☀️",
        short = "明亮/绿荫",
        prompt = "change the scene to mid-summer: bright sunlight, lush green foliage, blue sky, vivid warm colors, keep composition and subject identity"
    ),
    EditAction(
        id = "season_winter", category = "场景", label = "改冬季", emoji = "🌨️",
        short = "雪地/枯枝",
        prompt = "change the scene to deep winter: snow on the ground and branches, cool white-blue light, the subject's breath slightly visible, keep composition and identity"
    ),

    // ===== 局部（遮罩/修小细节，配合涂抹的遮罩一起用效果最佳） =====
    EditAction(
        id = "fix_scratch", category = "局部", label = "修小瑕疵", emoji = "🩹",
        short = "去掉脸/皮肤上的瑕疵",
        prompt = "only in the masked area, remove blemishes, pimples, spots, scratches, and small skin imperfections, reconstruct natural skin texture, keep the surrounding area completely unchanged"
    ),
    EditAction(
        id = "inpaint_obj", category = "局部", label = "去物体", emoji = "🧹",
        short = "抹掉遮罩里的杂物",
        prompt = "only in the masked area, erase the unwanted object and reconstruct the natural background (ground, wall, sky, foliage, etc.) as if the object was never there, keep everything outside the mask identical"
    ),
    EditAction(
        id = "recolor", category = "局部", label = "改色", emoji = "🖌️",
        short = "仅改颜色不改形状",
        prompt = "only in the masked area, change the color to the natural color requested (e.g. change the T-shirt from red to deep navy), keep texture, shape, material, lighting and surrounding area unchanged"
    ),
    EditAction(
        id = "replace_obj", category = "局部", label = "替换物体", emoji = "🔁",
        short = "换为指定物体",
        prompt = "only in the masked area, replace the current object with a new object described in the user prompt, match perspective, scale, lighting and edges seamlessly with the surrounding scene"
    ),
    EditAction(
        id = "repaint", category = "局部", label = "重画这块", emoji = "🎨",
        short = "重画遮罩区域",
        prompt = "only in the masked area, completely repaint with new content following the user prompt, blend perspective, lighting, depth of field and color grading naturally with the rest of the image"
    )
)

val EDIT_ACTIONS_CATEGORIES = listOf("全部", "修复", "风格", "场景", "局部")

/** 灵感提示词库 */
val INSPIRATION_PROMPTS = listOf(
    "雨夜的东京街头，霓虹灯牌倒映在湿滑的柏油路上，一只白猫蹲在便利店门口",
    "悬浮在云海之上的古老图书馆，藤蔓缠绕着大理石立柱，阳光穿过彩色玻璃",
    "极简白色房间里的一把红色椅子，一束光从高窗斜射进来",
    "深海中发光的水母群，蓝紫色渐变，光线从水面折射下来",
    "秋日京都的石板小巷，红叶铺满地面，远处是一座木质鸟居",
    "宇航员坐在月球表面看地球升起，头盔反射着蓝色星球",
    "森林深处的树屋咖啡馆，暖黄灯光，窗外飘着细雪",
    "赛博朋克机械龙盘绕在摩天大楼顶端，电流在鳞片间流动",
    "水墨风格的山水长卷，远山如烟，一叶扁舟停在江心",
    "微缩景观：一座建在西兰花上的童话小镇，柔和的微距景深",
    "沙漠中的镜面玻璃屋，映照出日落时的紫红色天空",
    "老式打字机上生长出花朵，纸张飞舞，复古暖色调",
    "北极光下的冰原，一头北极熊回头望向镜头",
    "蒸汽朋克风格的热气球舰队穿越峡谷，黄铜齿轮与蒸汽",
    "一杯冒着热气的咖啡放在木桌上，窗外是下雨的城市，胶片质感"
)

/** 本地提示词增强：补齐结构化描述，不消耗额度 */
fun enhancePrompt(raw: String): String {
    val base = raw.trim().trimEnd('，', ',', '。', '.')
    if (base.isEmpty()) return base
    val additions = mutableListOf<String>()
    val lower = base.lowercase()

    fun lacks(vararg keys: String) = keys.none { lower.contains(it) }

    if (lacks("光", "light", "lighting", "阳光", "灯")) {
        additions += "柔和的自然光线，光影层次分明"
    }
    if (lacks("构图", "composition", "视角", "镜头", "特写", "远景")) {
        additions += "考究的构图，主体突出"
    }
    if (lacks("细节", "detail", "质感", "texture")) {
        additions += "丰富的细节与真实材质质感"
    }
    if (lacks("色", "color", "调", "tone")) {
        additions += "和谐统一的色彩调性"
    }
    additions += "高分辨率，画面干净锐利"

    return base + "，" + additions.joinToString("，")
}

/** 组装最终提示词：按目标模型决定是否追加 tag 式质量词 */
fun composePrompt(
    prompt: String,
    style: StylePreset,
    quality: QualityTier,
    model: String = ""
): String {
    val m = model.trim().lowercase()
    // 自然语言系模型不吃 tag 堆砌：Gemini 官方指南明确 "stop using tag soups"，
    // NAI 4.5/5 的质量标签由专用管线按版本管理，与分辨率解耦。
    // 给它们拼 masterpiece/best quality/4k uhd 这类尾巴只会让输出劣化。
    val naturalLanguageModel =
        m.contains("banana") ||
            m.contains("grok") ||
            m.contains("image") ||
            m.contains("dall") ||
            m.contains("novelai") ||
            m.startsWith("nai")
    val parts = mutableListOf(prompt.trim())
    if (style.suffix.isNotBlank()) parts += style.suffix
    if (!naturalLanguageModel && quality.suffix.isNotBlank()) parts += quality.suffix
    return parts.filter { it.isNotBlank() }.joinToString(", ")
}

/** 组装最终负向词 */
fun composeNegative(
    negative: String,
    style: StylePreset,
    useSafeNegative: Boolean
): String? {
    val parts = mutableListOf<String>()
    if (negative.isNotBlank()) parts += negative.trim()
    if (style.negative.isNotBlank()) parts += style.negative
    if (useSafeNegative) parts += SAFE_NEGATIVE
    return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
}

/**
 * 目标模型是否支持 negative_prompt 参数：
 * - FLUX 系（flow-matching 架构）：模型层面就没有负引导机制，参数发过去直接被忽略；
 * - gpt-image / DALL-E / Imagen / Grok：OpenAI 官方 /images/generations 无此字段，宽松中转忽略、严格端点 400。
 * 这些模型上负面提示词"发了也白发"，需要转成正向对冲词。
 */
fun supportsNegativePrompt(model: String): Boolean {
    val m = model.trim().lowercase()
    if (m.isBlank()) return false
    val unsupported = listOf("flux", "gpt-image", "dall", "imagen", "grok")
    return unsupported.none { m.contains(it) }
}

/**
 * 把负面提示词转成正向对冲词（供不支持 negative_prompt 的模型使用）。
 * 映射表覆盖常见负面词；映射不到的词直接丢弃——
 * 因为对这类模型写 "no blurry" 反而可能被理解成 "blurry"（扩散模型对否定词不敏感）。
 */
fun negativeToPositive(negative: String): String {
    val mapping = mapOf(
        "lowres" to "high resolution, fine detail",
        "low quality" to "high quality",
        "worst quality" to "high quality",
        "bad quality" to "high quality",
        "blurry" to "sharp focus, crisp details",
        "out of focus" to "sharp focus",
        "jpeg artifacts" to "clean rendering",
        "noise" to "clean rendering",
        "watermark" to "clean image",
        "signature" to "clean image",
        "text" to "clean image",
        "logo" to "clean image",
        "bad anatomy" to "correct anatomy",
        "deformed" to "well-formed features",
        "deformed hands" to "well-formed hands",
        "bad hands" to "well-formed hands",
        "extra fingers" to "well-formed hands",
        "extra limbs" to "natural body proportions",
        "extra digits" to "well-formed hands",
        "mutated" to "natural features",
        "disfigured" to "natural features",
        "chibi" to "realistic proportions",
        "flat lighting" to "dimensional lighting, light and shadow depth",
        "harsh shadows" to "soft natural shadows",
        "overexposed" to "balanced exposure",
        "underexposed" to "balanced exposure",
        "washed out" to "rich colors",
        "oversaturated" to "balanced color grading",
        "rough sketch" to "polished finished artwork",
        "low detail" to "intricate details",
        "cropped" to "complete composition",
        "duplicate" to "single subject",
        "long neck" to "natural proportions"
    )
    val counters = linkedSetOf<String>()
    negative.split(',', '，', '\n').forEach { raw ->
        val token = raw.trim().lowercase()
        if (token.isBlank()) return@forEach
        // 精确匹配优先；包含匹配次之（如 "very blurry" 命中 "blurry"）
        val hit = mapping[token]
            ?: mapping.entries.firstOrNull { token.contains(it.key) }?.value
        if (hit != null) counters += hit
    }
    return counters.joinToString(", ")
}