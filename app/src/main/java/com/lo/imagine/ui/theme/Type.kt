package com.lo.imagine.ui.theme

import androidx.compose.material3.Typography as MaterialTypography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lo.imagine.R

/** 全局排版：清晰、紧凑，正文保持可读性。 */
val Typography = MaterialTypography().run {
    copy(
        displaySmall = displaySmall.copy(
            fontSize = 34.sp,
            lineHeight = 39.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp
        ),
        headlineMedium = headlineMedium.copy(
            fontSize = 29.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp
        ),
        headlineSmall = headlineSmall.copy(
            fontSize = 25.sp,
            lineHeight = 31.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp
        ),
        titleLarge = titleLarge.copy(
            fontSize = 21.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp
        ),
        titleMedium = titleMedium.copy(
            fontSize = 17.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.SemiBold
        ),
        titleSmall = titleSmall.copy(
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold
        ),
        bodyLarge = bodyLarge.copy(fontSize = 16.sp, lineHeight = 25.sp),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
        labelLarge = labelLarge.copy(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
        labelSmall = labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    )
}

/** 8-bit：等宽粗体和紧凑字面，中文仍使用系统可用字形。 */
val PixelTypography = Typography.copy(
    displaySmall = Typography.displaySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black),
    headlineMedium = Typography.headlineMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black),
    headlineSmall = Typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black),
    titleLarge = Typography.titleLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black),
    titleMedium = Typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
    titleSmall = Typography.titleSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
    bodyLarge = Typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
    bodyMedium = Typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
    bodySmall = Typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
    labelLarge = Typography.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
    labelMedium = Typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
    labelSmall = Typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
)

/** 包豪斯：海报式重标题和更紧的字距。 */
val BauhausTypography = Typography.copy(
    displaySmall = Typography.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = 0.sp),
    headlineMedium = Typography.headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 0.sp),
    headlineSmall = Typography.headlineSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 0.sp),
    titleLarge = Typography.titleLarge.copy(fontWeight = FontWeight.Black),
    titleMedium = Typography.titleMedium.copy(fontWeight = FontWeight.Black),
    titleSmall = Typography.titleSmall.copy(fontWeight = FontWeight.Bold)
)

/**
 * 罗德岛终端：复刻官网字体搭配。
 * 官网实测——大标题用 Oswald-Medium 且字距 -.05em（紧排），
 * 英文小标签（OPERATOR / SCROLL / VIEW MORE）用宽体几何字加正字距，
 * 正文中文用思源黑体（= Android 系统 Noto Sans CJK，自动回退）。
 * Oswald 保留大标题，RefHud / Chakra Petch 用于英文小标与导航数字，
 * 按钮、正文及中小标题使用中性系统无衬线，与新首页参考稿保持一致。
 */
private val ArkOswald = FontFamily(
    Font(R.font.oswald_light, FontWeight.Light),
    Font(R.font.oswald_regular, FontWeight.Normal),
    Font(R.font.oswald_medium, FontWeight.Medium),
    Font(R.font.oswald_semibold, FontWeight.SemiBold),
    Font(R.font.oswald_bold, FontWeight.Bold),
    Font(R.font.oswald_bold, FontWeight.Black)
)
val RefHud = FontFamily(
    Font(R.font.chakra_medium, FontWeight.Normal),
    Font(R.font.chakra_medium, FontWeight.Medium),
    Font(R.font.chakra_semibold, FontWeight.SemiBold),
    Font(R.font.chakra_bold, FontWeight.Bold),
    Font(R.font.chakra_bold, FontWeight.Black)
)

val ArknightTypography = Typography.copy(
    // 标题族：Oswald 紧排大标题（官网 -0.05em 字距）
    displaySmall = Typography.displaySmall.copy(
        fontFamily = ArkOswald, fontWeight = FontWeight.Medium, letterSpacing = 0.sp
    ),
    headlineMedium = Typography.headlineMedium.copy(
        fontFamily = ArkOswald, fontWeight = FontWeight.Medium, letterSpacing = 0.sp
    ),
    headlineSmall = Typography.headlineSmall.copy(
        fontFamily = ArkOswald, fontWeight = FontWeight.Medium, letterSpacing = 0.sp
    ),
    titleLarge = Typography.titleLarge.copy(
        fontFamily = ArkOswald, fontWeight = FontWeight.Medium, letterSpacing = 0.sp
    ),
    titleMedium = Typography.titleMedium.copy(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp
    ),
    titleSmall = Typography.titleSmall.copy(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp
    ),
    // 正文保持系统族：中文即思源黑体，与官网正文同源
    bodyLarge = Typography.bodyLarge.copy(letterSpacing = 0.15.sp),
    bodyMedium = Typography.bodyMedium.copy(letterSpacing = 0.12.sp),
    bodySmall = Typography.bodySmall.copy(letterSpacing = 0.1.sp),
    // 控件使用中性无衬线；英文 HUD 辅助标记显式使用 RefHud。
    labelLarge = Typography.labelLarge.copy(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp
    ),
    labelMedium = Typography.labelMedium.copy(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp
    ),
    labelSmall = Typography.labelSmall.copy(
        fontFamily = FontFamily.Default, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp
    )
)