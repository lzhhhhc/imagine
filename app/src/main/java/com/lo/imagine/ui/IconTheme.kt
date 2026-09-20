package com.lo.imagine.ui

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource

/**
 * 图标语义层：ArkIcons 是 RefIcons 参考稿图标族的稳定别名
 * （24dp 画布、1.7 线宽、圆头圆接角；日夜共用）。
 */
fun themedIcon(icon: ImageVector): ImageVector {
    // 旧语义名直达新图标族，不走旧图标渲染分支。
    return when (icon.name) {
        "PopHome" -> ArkHome
        "PopBrush" -> ArkBrush
        "PopSpark" -> ArkSpark
        "PopGallery" -> ArkGallery
        "PopImageAdd" -> ArkImageAdd
        "PopFilm" -> ArkFilm
        "PopFrame" -> ArkFrame
        "PopClock" -> ArkClock
        "PopSliders" -> ArkSliders
        "PopPalette" -> ArkPalette
        "PopGear" -> ArkGear
        "PopRetouch" -> ArkRetouch
        "PopEdit" -> ArkEdit
        "PopPencil" -> ArkPencil
        "PopWand" -> ArkWand
        "PopDownload" -> ArkDownload
        "PopRefresh" -> ArkRefresh
        "PopCopy" -> ArkCopy
        "PopShare" -> ArkShare
        "PopTrash" -> ArkTrash
        "PopClose" -> ArkClose
        "PopCheck" -> ArkCheck
        "PopBack" -> ArkBack
        "PopChevron" -> ArkChevron
        "PopChevronDown" -> ArkChevronDown
        "PopChevronUp" -> ArkChevronUp
        "PopAlert" -> ArkAlert
        "PopInfo" -> ArkInfo
        "PopPerson" -> ArkPerson
        "PopCube" -> ArkCube
        "PopBox" -> ArkBox
        "PopCircle" -> ArkCircle
        "PopGrid" -> ArkGrid
        "PopBookmark" -> ArkBookmark
        "PopPlug" -> ArkPlug
        "PopKey" -> ArkKey
        "PopTranslate" -> ArkTranslate
        "PopInspect" -> ArkInspect
        "PopWave" -> ArkWave
        "PopBolt" -> ArkBolt
        "PopMoon" -> ArkMoon
        "PopHeart" -> ArkHeart
        "PopHeartOutline" -> ArkHeartOutline
        else -> icon
    }
}

/** 主题感知图标：除自动映射图标外，行为与 Material3 Icon 一致（tint 缺省沿用内容色）。 */
@Composable
fun PIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    Icon(
        imageVector = themedIcon(imageVector),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint ?: LocalContentColor.current
    )
}

/** XML vectors use the same content-color contract; embedded black strokes are always tinted. */
@Composable
fun PIcon(
    @DrawableRes resourceId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Icon(
        painter = painterResource(resourceId),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}