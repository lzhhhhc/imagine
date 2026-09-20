package com.lo.imagine.ui.theme

import com.lo.imagine.ui.theme.PopRadius
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.lo.imagine.data.ThemeMode
import com.lo.imagine.data.UiMood

/**
 * 罗德岛终端·日间（浅色 HUD）：冷白画布 + 三档明度阶梯（#FCFDFD / #F0F4F6 / #E6EDF0），
 * 文字近黑、线条中灰；唯一强调色是正青深调 #176F95（浅底要够深才有对比）。
 */
private val ArknightLightColors = lightColorScheme(
    // 冷白画布 + 清晰的层级面板：内容区与交互区拉开明度，不靠粗描边制造层次
    primary = Color(0xFF1F7D9F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F3FA),
    onPrimaryContainer = Color(0xFF21536D),
    inversePrimary = Color(0xFF67D5F0),
    secondary = Color(0xFF8A6800),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE9A8),
    onSecondaryContainer = Color(0xFF2A2000),
    tertiary = Color(0xFF34434B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE7EEF1),
    onTertiaryContainer = Color(0xFF17242B),
    background = Color(0xFFF4F8FC),
    onBackground = Color(0xFF172338),
    surface = Color(0xFFFEFFFF),
    onSurface = Color(0xFF172338),
    surfaceVariant = Color(0xFFE4EAED),
    onSurfaceVariant = Color(0xFF647487),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCFEFF),
    surfaceContainer = Color(0xFFF0F4F6),
    surfaceContainerHigh = Color(0xFFE6EDF0),
    surfaceContainerHighest = Color(0xFFD8E2E7),
    surfaceDim = Color(0xFFD0DADF),
    surfaceBright = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF172126),
    inverseOnSurface = Color(0xFFF0F7F9),
    outline = Color(0xFF71838D),
    outlineVariant = Color(0xFFE0E8EF),
    scrim = Color(0x66000000),
    error = Color(0xFFC62828),
    onError = Color.White,
    errorContainer = Color(0xFFFFE5E3),
    onErrorContainer = Color(0xFF3D0000)
)

/**
 * 罗德岛终端·战备（深色 HUD）：近黑舱底 + 三档面板 + 冷白文字，
 * 强调色只用于交互和状态，不把整页染成高亮蓝。
 */
private val ArknightDarkColors = darkColorScheme(
    // 层级：近黑背景 → 低对比一级面板 → 清晰二级面板；避免纯黑和纯白形成刺眼闪烁。
    primary = Color(0xFF7CBADB),
    onPrimary = Color(0xFF101B26),
    primaryContainer = Color(0xFF203B4D),
    onPrimaryContainer = Color(0xFFB5D8EE),
    inversePrimary = Color(0xFF238AAF),
    secondary = Color(0xFFE5C44B),
    onSecondary = Color(0xFF252000),
    secondaryContainer = Color(0xFF3B3210),
    onSecondaryContainer = Color(0xFFF4E39A),
    tertiary = Color(0xFFD5E1E6),
    onTertiary = Color(0xFF172026),
    tertiaryContainer = Color(0xFF202D34),
    onTertiaryContainer = Color(0xFFD5E1E6),
    background = Color(0xFF101518),
    onBackground = Color(0xFFF1F6F7),
    surface = Color(0xFF191F24),
    onSurface = Color(0xFFF1F6F7),
    surfaceVariant = Color(0xFF172229),
    onSurfaceVariant = Color(0xFFA0ADB7),
    surfaceContainerLowest = Color(0xFF05080A),
    surfaceContainerLow = Color(0xFF20272D),
    surfaceContainer = Color(0xFF191F24),
    surfaceContainerHigh = Color(0xFF242C33),
    surfaceContainerHighest = Color(0xFF2C3740),
    surfaceDim = Color(0xFF101518),
    surfaceBright = Color(0xFF263943),
    inverseSurface = Color(0xFFEAF2F4),
    inverseOnSurface = Color(0xFF10181C),
    outline = Color(0xFF4E5D68),
    outlineVariant = Color(0xFF303A43),
    scrim = Color(0xB3000000),
    error = Color(0xFFFF817A),
    onError = Color(0xFF350A08),
    errorContainer = Color(0xFF4B1718),
    onErrorContainer = Color(0xFFFFDAD6)
)

/**
 * 参考视觉稿（罗德岛终端·白舱版）抽取的固定令牌：
 * 钢蓝强调 / 墨黑操作面板 / 冷白舱底 / 生成按钮渐变 / Dock 激活斜块渐变。
 * 明暗两套 ColorScheme 共用这套结构色，保证全 App 风格一致。
 */
object ArkRef {
    /** 主强调：钢蓝（激活磁贴、状态方点、进度段） */
    val steel = Color(0xFF2B91B8)
    val steelDeep = Color(0xFF0F4C65)
    val steelBright = Color(0xFF8BCBEB)
    /** 浅色主题下的墨黑操作面板（提示词区 / 磁贴 / 页头动作块） */
    val ink = Color(0xFF12181B)
    val inkBorder = Color(0xFF2A353A)
    val inkText = Color(0xFFEDF4F6)
    val inkSub = Color(0xFF9AAAB0)
    /** 页面冷白舱底与一级卡片 */
    val paper = Color(0xFFF4F8FC)
    val paperCard = Color(0xFFFEFFFF)
    /** 生成按钮横向渐变 */
    val genStart = Color(0xFF238AAF)
    val genEnd = Color(0xFF0C3A4C)
    /** Dock 舱带：主参考浅色页也是近黑底栏，不跟页面明暗走 */
    val dockBar = Color(0xFF1A2226)
    val dockOn = Color(0xFFE8F0F3)
    val dockMute = Color(0xFF8AA0A8)
    /** Dock 激活斜块渐变（浅钢蓝） */
    val dockTileStart = Color(0xFFE5F3FA)
    val dockTileEnd = Color(0xFFA6CFDC)
}

/**
 * 罗德岛切角形状：官网卡片与按钮的核心造型——矩形在指定角做 45° 斜切（chamfer）。
 * 默认切左上 + 右下，形成对角呼应的工业感轮廓。
 */
class ArkCutShape(
    private val cut: Dp,
    private val cutTopStart: Boolean = true,
    private val cutTopEnd: Boolean = false,
    private val cutBottomStart: Boolean = false,
    private val cutBottomEnd: Boolean = true
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val c = with(density) { cut.toPx() }
            .coerceAtMost(minOf(size.width, size.height) / 2f)
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(if (cutTopStart) c else 0f, 0f)
            lineTo(w - (if (cutTopEnd) c else 0f), 0f)
            if (cutTopEnd) lineTo(w, c)
            lineTo(w, h - (if (cutBottomEnd) c else 0f))
            if (cutBottomEnd) lineTo(w - c, h)
            lineTo(if (cutBottomStart) c else 0f, h)
            if (cutBottomStart) lineTo(0f, h - c)
            if (cutTopStart) lineTo(0f, c)
            close()
        }
        return Outline.Generic(path)
    }
}

/** 全切角（四角同切）版本，用于徽章、输入框等小件。 */
fun ArkChamferShape(cut: Dp): Shape = ArkCutShape(cut, true, true, true, true)

/**
 * 平行四边形斜切块（官方菜单卡 / 返回按钮的倾斜语言）：
 * 左右两边整体倾斜，比 45° 小切角更「硬」、更有终端模块感。
 * slant 为水平偏移量：小件传 3-4dp，底栏按钮传 7dp 左右。
 */
fun ArkSlantShape(slant: Dp): Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = with(density) { slant.toPx() }.coerceAtMost(size.width * 0.4f)
        val path = Path().apply {
            moveTo(s, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width - s, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

// =====主题外观规格：罗德岛终端只保留一套造型规格（明暗共用） =====

data class FlavorSpec(
    val panelCorner: Dp,
    val panelBorder: Dp,
    val panelCelX: Dp,
    val panelCelY: Dp,
    val panelElevation: Dp,
    val panelPadding: Dp,
    val dockCorner: Dp,
    val dockBorder: Dp,
    val dockCelX: Dp,
    val dockCelY: Dp,
    val dockElevation: Dp,
    val fieldCorner: Dp,
    val buttonCorner: Dp,
    val headerShadow: Boolean,
    val headerUnderline: Boolean,
    val headerPill: Boolean
)

/**
 * 罗德岛终端：官网是绝对平面的——无阴影（panelCel=0）、1px 细线描边、
 * 造型靠 45° 切角而非圆角；区块用底边细线分隔（headerUnderline）。
 * 明暗双版共用同一套造型规格，只在色彩上区分。
 */
private val ArknightFlavor = FlavorSpec(
    // Reference rounded surfaces; DARK_TACTIC retains its deliberate mood-specific chamfer.
    panelCorner = 16.dp, panelBorder = 1.dp, panelCelX = 0.dp, panelCelY = 0.dp, panelElevation = 0.dp, panelPadding = 12.dp,
    dockCorner = 4.dp, dockBorder = 1.4.dp, dockCelX = 0.dp, dockCelY = 0.dp, dockElevation = 0.dp,
    fieldCorner = 12.dp, buttonCorner = 12.dp,
    headerShadow = false, headerUnderline = true, headerPill = false
)

val LocalFlavor = staticCompositionLocalOf { ArknightFlavor }
val LocalUiMood = staticCompositionLocalOf { UiMood.COOL_WHITE }

/**
 * 圆角标尺：全应用只允许这五档，避免出现 9dp / 11dp / 13dp 这种「随手写」的圆角——
 * 那种差 1–2dp 的不一致正是界面显得不精致的主要来源。
 *
 * 用法：`PopRadius.chip` / `PopRadius.field` / `PopRadius.card` / `PopRadius.sheet` / `PopRadius.pill`
 */
object PopRadius {
    /** 小元件：徽章内衬、分段高亮、图标底座 */
    val chip = 10.dp
    /** 输入框、下拉、小按钮 */
    val field = 12.dp
    /** 列表项、卡片内块、媒体缩略图 */
    val card = 16.dp
    /** 面板、分组容器、弹窗、大按钮 */
    val sheet = 20.dp
    /** 胶囊：徽章、开关、滑杆 */
    val pill = 999.dp
}

/** Legacy Dp-only consumers use the same mood policy; no day/night branch. */
@Composable
fun themedCorner(default: Dp): Dp = when (LocalUiMood.current) {
    UiMood.DARK_TACTIC -> 0.dp
    UiMood.COOL_WHITE -> default
    UiMood.SOFT_ILLUST -> default + 4.dp
}

/** 明暗共用亮色版几何规格；只切换气质时才改变图标底座和组件的轮廓。 */
@Composable
fun themedShape(default: Dp, pill: Boolean = false): Shape =
    moodShape(LocalUiMood.current, default, pill)

/** Pure geometry: deliberately accepts no theme mode or color scheme. */
internal fun moodShape(mood: UiMood, default: Dp, pill: Boolean = false): Shape = when (mood) {
    UiMood.COOL_WHITE -> if (pill) CircleShape else RoundedCornerShape(default)
    UiMood.DARK_TACTIC -> ArkCutShape(if (pill || default.value <= 10f) 3.dp else 7.dp)
    UiMood.SOFT_ILLUST -> if (pill) CircleShape else RoundedCornerShape(default + 4.dp)
}

@Composable
fun ImagineTheme(
    themeMode: ThemeMode = ThemeMode.ARKNIGHTS_LIGHT,
    moodKey: String = "",
    content: @Composable () -> Unit
) {
    val mood = UiMood.fromId(moodKey)
    val colorScheme = themeColors(themeMode, mood)
    val typography = ArknightTypography
    val flavor = ArknightFlavor
    val popAccents = PopAccentsArk

    CompositionLocalProvider(LocalFlavor provides flavor, LocalPopAccents provides popAccents, LocalUiMood provides mood) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}

/** The palette is independent of icon identity and component geometry. */
internal fun themeColors(mode: ThemeMode, mood: UiMood): ColorScheme = moodColors(
    base = when (mode) {
        ThemeMode.ARKNIGHTS_LIGHT -> ArknightLightColors
        ThemeMode.ARKNIGHTS_DARK -> ArknightDarkColors
    },
    dark = mode == ThemeMode.ARKNIGHTS_DARK,
    mood = mood
)

/** Mood changes finish and tone without changing the selected day/night mode. */
internal fun moodColors(base: ColorScheme, dark: Boolean, mood: UiMood): ColorScheme = when (mood) {
    UiMood.COOL_WHITE -> base
    UiMood.DARK_TACTIC -> if (dark) base.copy(
        primary = Color(0xFF94B5CF), primaryContainer = Color(0xFF263A4B), onPrimaryContainer = Color(0xFFD4E5F2),
        background = Color(0xFF0E1319), surface = Color(0xFF171F28), surfaceContainer = Color(0xFF1C2732),
        outlineVariant = Color(0xFF3A4957)
    ) else base.copy(
        primary = Color(0xFF3B617A), primaryContainer = Color(0xFFDCE7EF), onPrimaryContainer = Color(0xFF233F54),
        background = Color(0xFFE8EDF1), surface = Color(0xFFF5F8FA), surfaceContainer = Color(0xFFDFE6EC),
        outlineVariant = Color(0xFFC2CED8), onSurfaceVariant = Color(0xFF52616F)
    )
    UiMood.SOFT_ILLUST -> if (dark) base.copy(
        primary = Color(0xFFE2ADC0), onPrimary = Color(0xFF35212B), primaryContainer = Color(0xFF49333F),
        onPrimaryContainer = Color(0xFFF4D9E4), background = Color(0xFF211C22), surface = Color(0xFF2B252C),
        surfaceContainer = Color(0xFF332C34), onSurface = Color(0xFFF9F0F3), onSurfaceVariant = Color(0xFFC7B5BF),
        outlineVariant = Color(0xFF534550)
    ) else base.copy(
        primary = Color(0xFF90556F), onPrimary = Color.White, primaryContainer = Color(0xFFF3E0E9),
        onPrimaryContainer = Color(0xFF62374C), background = Color(0xFFFAF5F2), surface = Color(0xFFFFFAF7),
        surfaceContainer = Color(0xFFF0E8E6), onSurface = Color(0xFF352C33), onSurfaceVariant = Color(0xFF77636D),
        outlineVariant = Color(0xFFE2D1D9)
    )
}
