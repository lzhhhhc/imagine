package com.lo.imagine.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 绘世视觉基色：暖纸张画布 + 冷调靛蓝行动色。
 * 中性色负责内容层级，品牌色只用于主行动、选中态和生成反馈。
 */
val Ink = Color(0xFF17191C)
val InkMuted = Color(0xFF59656A)
val Canvas = Color(0xFFF5F8F4)
val CanvasDark = Color(0xFF0D1417)
val CardLight = Color(0xFFFFFFFF)
val CardDark = Color(0xFF141D20)
val LineLight = Color(0xFFDCE6E0)
val LineDark = Color(0xFF35464B)

/** 冷调蓝靛行动色：保留品牌识别，但不再向淡紫偏移。 */
val Forest = Color(0xFF4657C9)
val ForestDeep = Color(0xFF202B72)
/** 深色主题的清透蓝阶，用于替代旧的薰衣草紫。 */
val Signal = Color(0xFFB8E6FF)
val SignalSoft = Color(0xFFE8F7FF)
val SignalDark = Color(0xFF86D7FF)
val Moss = Color(0xFF65A313)
val MossSoft = Color(0xFFE4F6C4)
val Amber = Color(0xFFE05645)
val AmberSoft = Color(0xFFFFE5DC)
/** 森空岛式提醒橙点：未读、活跃提示专用，不参与主配色。 */
val Tangerine = Color(0xFFFF8A1E)

// ===== 波普（Pop Art）点缀色：高饱和多色并存，制造灵动 =====
val PopSunny = Color(0xFFFFD93D)
val PopCoral = Color(0xFFFF5D5D)
val PopCyan = Color(0xFF4ECDC4)
val PopPink = Color(0xFFFF9CC5)

// ===== 主题强调色槽：每个主题自己的四色点缀系统，替代全局硬编码波普色 =====
data class PopAccents(
    val a: Color,   // 主强调（默认黄/主题最亮）
    val b: Color,   // 次强调（默认红）
    val c: Color,   // 三强调（默认青）
    val d: Color,   // 四强调（默认粉）
    val e: Color    // 五强调（导演 tab 专用，避免与四色撞色）
)

/** 波普纸面：利希滕斯坦原色系——柠檬黄主色 + 朱红/钴青/品红三撞色点缀。 */
val PopAccentsPaper = PopAccents(Color(0xFFFFD400), Color(0xFFFF3B30), Color(0xFF00C2CB), Color(0xFFFF2E88), Color(0xFFFF6B35))
/** 深夜：浅蓝青绿暖橙淡蓝长春花蓝，低调但有层次 */
val PopAccentsMidnight = PopAccents(
    SignalDark, Color(0xFF9ADCC8), Color(0xFFFFB4A1), Color(0xFFC8EDFF), Color(0xFFB9C6FF)
)
/** 8-bit：荧光绿、琥珀黄、亮青、深靛、品红——街机霓虹 */
val PopAccentsPixel = PopAccents(
    Color(0xFF8CFF66), Color(0xFFFFC857), Color(0xFF61D9FF), Color(0xFF4A5CFF), Color(0xFFFF6EC7)
)
/** 包豪斯：红黄蓝黑四原色 + 灰，构成主义 */
val PopAccentsBauhaus = PopAccents(
    Color(0xFFFFC800), Color(0xFFE63312), Color(0xFF1E4FD8), Color(0xFF121212), Color(0xFF8A8A8A)
)
/** 动漫：樱粉、天蓝、薄荷青、奶油黄、青草绿——糖果系 */
val PopAccentsAnime = PopAccents(
    Color(0xFFFF6E9C), Color(0xFF4A9DE2), Color(0xFF2EC9AE), Color(0xFFFFE066), Color(0xFF8FD14F)
)

// ===== 罗德岛终端（参照明日方舟官网 ak.hypergryph.com 实测样式值）=====
/** 罗德岛终端的近黑舱底与递进面板层级。 */
val ArkVoid = Color(0xFF070B0E)
val ArkPanel = Color(0xFF0D1419)
val ArkPanelHi = Color(0xFF15232B)
val ArkPanelUp = Color(0xFF1D3038)
val ArkPanelMax = Color(0xFF263943)
/** 官网 1px 细线主用 #585858，弱线取其 60% */
val ArkLine = Color(0xFF585858)
val ArkLineDim = Color(0xFF383A3B)
/** 文字：纯白正文 + #ababab / #d2d2d2 / #a4a4a4 灰阶 */
val ArkText = Color(0xFFFFFFFF)
val ArkTextMid = Color(0xFFD2D2D2)
val ArkTextDim = Color(0xFFABABAB)
val ArkTextFaint = Color(0xFF7A7A7A)
/** 标志青：罗德岛终端的冷调科技色（#2AC4E3 高饱和、不发飘；上一版 #3EDCFF 太浅已弃） */
val ArkCyan = Color(0xFF2AC4E3)
val ArkCyanDeep = Color(0xFF176F95)
val ArkIce = Color(0xFFA7ECF4)
val ArkMint = Color(0xFF00E0C7)
/** 罗德岛终端：全部取青的同族（冰蓝 / 深青 / 薄荷），不掺 #3387FB 那种偏紫的通用蓝 */
val PopAccentsArk = PopAccents(
    ArkCyan, ArkIce, ArkCyanDeep, ArkMint, ArkTextDim
)

val LocalPopAccents = staticCompositionLocalOf { PopAccentsPaper }

// 旧名称仅作为编译期别名；界面组件统一使用 MaterialTheme 语义色。
val Violet = Forest
val VioletLight = SignalSoft
val VioletDark = SignalDark
