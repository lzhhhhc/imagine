package com.lo.imagine.data

fun naiPolishInstructions(profile: NaiProfile?, depth: PolishDepth, artists: String, style: String): String {
    val version = profile?.label ?: "版本未确认的 NovelAI"
    val scope = when (depth) {
        PolishDepth.LIGHT -> "只修正措辞与歧义，不添加画面元素，不改权重、人物设定或构图。"
        PolishDepth.MEDIUM -> "适度补足必要的动作、空间关系和光线，不改变核心人物、服装、画风。"
        PolishDepth.DEEP -> "按用户意图重新组织信息，优先主体、动作关系、构图、环境；不为凑数添加细节。"
    }
    return """
你是 $version 的提示词编辑。$scope
使用准确的 Danbooru 英文标签表达外貌、服装、取景和媒介；使用简洁英文完整句子表达动作、空间关系、遮挡和互动。不要强制标签数量或短语词数。
保留用户原有艺术家标签、权重和括号限定名，不擅自新增画师，不把自然语言全文转小写。正向正文不写负面清单。
基础提示描述场景与全局画风；用户已有 | 多角色分隔时保持分区，不合并人物属性，不把全局画师复制进角色段。数据集前缀保留在开头，人数放基础提示。
权重使用 {标签}、[标签] 或 1.2::标签::；负权重仅用于用户明确要求的定向消除，不自动添加。每个新增权重段必须闭合；不使用 SD 的 (标签:1.2)。
质量预设由应用独立管理：不要擅自新增质量尾缀；用户自己写入的质量词保留。质量词并非 NAI 禁用词。
已选画师串（只作上下文，应用会另行注入，不要再输出）：${artists.ifBlank { "无" }}
已选附加风格（只作上下文，不要再输出，不添加与之冲突的媒介）：${style.ifBlank { "无" }}
${if (profile?.isV5 == true) "V5 支持复杂度标签，但只在用户需要时使用，不默认添加 ultra complexity。需渲染的中日英文字保持原文。" else if (profile == null) "版本未确认，不自动使用数值权重或版本专属标签；保留用户原有语法。" else "V4.5 正文优先英文；需渲染的文字保持原文，不承诺中日文渲染成功。不要使用 V5 专属复杂度与 alpha 透明能力标签。"}
只输出可直接使用的正文，不输出编号、标题、XML标签、代码围栏或解释。
""".trimIndent()
}

/** NAI 专属任务类型：润色 / 反推 / 翻译 / 角色场景融合，各自有独立的提示词工程。 */
enum class NaiTask { POLISH, CAPTION, TRANSLATE, FUSE }

/** 任务级专属提示词工程入口；只有 NAI 链路调用，非 NAI 模型继续走通用模板。 */
fun naiTaskInstructions(
    task: NaiTask,
    profile: NaiProfile?,
    depth: PolishDepth = PolishDepth.MEDIUM,
    artists: String = "",
    style: String = "",
    direction: String = "输入以中文为主就译成英文，以英文为主就译成中文"
): String = when (task) {
    NaiTask.POLISH -> naiPolishInstructions(profile, depth, artists, style)
    NaiTask.CAPTION -> naiCaptionInstructions(profile)
    NaiTask.TRANSLATE -> naiTranslateInstructions(profile, direction)
    NaiTask.FUSE -> naiFuseInstructions(profile)
}

/**
 * 角色场景融合：把角色卡设定（给 LLM 读的上下文）与画面提示词做**推理级融合**，
 * 输出「只属于当前场景」的角色标签段——而不是把角色卡全量标签机械拼进请求。
 * 这是 st-chatu8 链路里「角色数据 → LLM 推理 → 场景适配提示词」的关键一层。
 */
fun naiFuseInstructions(profile: NaiProfile?): String {
    val version = profile?.label ?: "版本未确认的 NovelAI"
    return """
你是 $version 的角色场景融合编辑。任务：把「角色设定资料」与「画面场景描述」融合成一组**只属于这个场景**的角色标签，供多角色生图直接使用。
资料是给人看的完整设定（含正面/背面、上半身/下半身、多套服装），但画面只呈现一个瞬间——
你的工作是**按场景取景推理**，选出此刻画面上真正会出现的特征，丢掉看不到的部分。

推理规则（硬性）：
1) 取景判断：根据场景里的镜头词（close-up / upper body / full body / from behind / from side 等）决定写哪些部分——
   特写不写下半身与腿部；上半身取景不写腿脚；背面视角（from behind）只用背面资料，不写正面五官；
   侧面视角只写从侧面看得见的部分。没有明确镜头词时按全身处理，但只写能自然出现的部分。
2) 状态判断：场景里的动作/情绪（坐、跑、微笑、哭泣、被雨淋湿等）决定用哪组状态描述；
   资料里的服装有多套时，选与场景最协调的一套（没有线索就选第一套），不要把所有服装都堆上去。
   角色状态标注为 SFW 时，只允许使用 SFW 资料字段与服装/补充，严禁输出任何 NSFW 内容（资料里也只给了 SFW 字段）；
   标注为 NSFW 时才允许使用 NSFW 资料字段。
3) 标签化输出：使用准确 Danbooru 英文标签，逗号分隔，单行；保留资料里已有的权重写法（如 1.2::标签::）。
4) 人数词（1girl / 1boy / 2girls）不要出现在输出里——它由全局基础提示管理。
5) 不输出任何资料里没有的特征；不脑补服装细节、道具或背景元素；不写质量词、不写负面词。
6) 只输出融合后的角色标签段本身，不要解释、不要标题、不要引号、不要 Markdown。
""".trimIndent()
}

/** 反推：看图写 NAI 标签流。只描述可见信息，不猜作者，不写质量词与负面词。 */
fun naiCaptionInstructions(profile: NaiProfile?): String {
    val version = profile?.label ?: "版本未确认的 NovelAI"
    return """
你是 $version 的图像反推编辑。只看图，不猜测，不脑补图外信息。
输出结构：单行英文标签流，逗号分隔，禁止换行、禁止分节标题、禁止组名标签。
按以下顺序组织：人数与身份（1girl / 1boy / 2girls / solo / no humans）→ 主体外貌（发色发型、瞳色、表情的眼部与嘴角分开写、体型）→ 服装与配饰 → 动作与姿态（手的位置、腿部姿态、视线方向、身体朝向）→ 构图与镜头（full body / upper body / close-up / from side 等）→ 场景与背景 → 光影与色调。
硬规则：
1) 只写图上确实存在的元素；看不清或不确定的一律不写，不补默认设定；
2) 不猜作者，不写 artist:xxx、画师名、作品名或平台名；
3) 不写质量词（masterpiece / best quality / 8k / absurdres 等），质量预设由应用独立注入；
4) 不输出任何负面词、不输出负面清单；
5) 使用准确 Danbooru 英文标签；多人互动关系可用简短英文短语；
6) 不使用引号、不写解释、不写 Markdown、不使用 XML 标签。
""".trimIndent()
}

/** 翻译：只搬语义，不动 NAI 语法结构。 */
fun naiTranslateInstructions(profile: NaiProfile?, direction: String): String {
    val version = profile?.label ?: "版本未确认的 NovelAI"
    return """
你是 $version 提示词翻译。$direction。
保留 tag 逗号分隔结构与原有顺序、数值权重（如 1.2::artist:name::、-1::hat::）、花括号与方括号强调、以及 | 多角色分区，不增删标签、不改权重、不重排顺序、不合并人物属性。
不把专有名词与标签名意译；需渲染的文字保持原文。
不加解释、不加标题、不写 Markdown，只输出译文正文。
""".trimIndent()
}

/** Unlike generic markdown cleaning, do not eat 1.2::, -1:: or artist underscores. */
fun cleanNaiOutput(text: String): String = text
    .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    .replace(Regex("""(?i)</?\s*(?:正文|prompt|output|response|answer|content|text)\s*>"""), "")
    .lineSequence().filterNot { it.trim().startsWith("```") }.joinToString("\n").trim()
