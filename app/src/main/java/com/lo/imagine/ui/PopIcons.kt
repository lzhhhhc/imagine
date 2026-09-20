package com.lo.imagine.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.unit.dp

/**
 * 波普手绘图标集：粗圆描边 + 几何剪影，跟随 Icon tint 单色渲染。
 * 自绘矢量，区别于通用 Material 图标。
 */

private fun popIcon(
    name: String,
    block: ImageVector.Builder.() -> Unit
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply(block).build()

private fun ImageVector.Builder.strokePath(
    width: Float = 2f,
    block: PathBuilder.() -> Unit
) {
    addPath(
        pathData = PathData(block),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    )
}

private fun ImageVector.Builder.fillPath(block: PathBuilder.() -> Unit) {
    addPath(
        pathData = PathData(block),
        fill = SolidColor(Color.Black)
    )
}

private fun PathBuilder.dot(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
    arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
    close()
}

/** 绘图 API：蜡笔——粗斜笔身 + 实心笔尖。 */
val PopBrush: ImageVector = popIcon("PopBrush") {
    strokePath(width = 3.4f) {
        moveTo(19f, 5f)
        lineTo(8.2f, 15.8f)
    }
    fillPath {
        moveTo(3.2f, 20.8f)
        lineTo(4.8f, 14.8f)
        lineTo(9.2f, 19.2f)
        close()
    }
    strokePath(width = 1.1f) {
        moveTo(16.6f, 7.4f)
        lineTo(12.9f, 11.1f)
    }
}

/** 润色 LLM：AI 星芒——大四角星 + 小四角星。 */
val PopSpark: ImageVector = popIcon("PopSpark") {
    fillPath {
        moveTo(11.6f, 2.6f)
        curveTo(12.2f, 7.4f, 14.2f, 9.7f, 20.4f, 11.9f)
        curveTo(14.2f, 14.1f, 12.2f, 16.4f, 11.6f, 21.4f)
        curveTo(11f, 16.4f, 9f, 14.1f, 2.8f, 11.9f)
        curveTo(9f, 9.7f, 11f, 7.4f, 11.6f, 2.6f)
        close()
    }
    fillPath {
        moveTo(19.3f, 2.2f)
        lineTo(20.1f, 4.3f)
        lineTo(22.2f, 5.1f)
        lineTo(20.1f, 5.9f)
        lineTo(19.3f, 8f)
        lineTo(18.5f, 5.9f)
        lineTo(16.4f, 5.1f)
        lineTo(18.5f, 4.3f)
        close()
    }
}

/** 作品库：相框——圆角框 + 太阳 + 山峦。 */
val PopGallery: ImageVector = popIcon("PopGallery") {
    strokePath(width = 1.9f) {
        moveTo(5.2f, 4.5f)
        lineTo(18.8f, 4.5f)
        arcTo(2f, 2f, 0f, false, true, 20.8f, 6.5f)
        lineTo(20.8f, 17.5f)
        arcTo(2f, 2f, 0f, false, true, 18.8f, 19.5f)
        lineTo(5.2f, 19.5f)
        arcTo(2f, 2f, 0f, false, true, 3.2f, 17.5f)
        lineTo(3.2f, 6.5f)
        arcTo(2f, 2f, 0f, false, true, 5.2f, 4.5f)
        close()
    }
    fillPath { dot(9.2f, 9.1f, 1.5f) }
    strokePath(width = 1.9f) {
        moveTo(4.6f, 16.8f)
        lineTo(9.6f, 11.8f)
        lineTo(13f, 14.8f)
        lineTo(15.2f, 12.8f)
        lineTo(19.4f, 16.8f)
    }
}

/** 本地优先：小房子——屋顶 + 墙体 + 门。 */
val PopHome: ImageVector = popIcon("PopHome") {
    strokePath(width = 2f) {
        moveTo(3.6f, 11.2f)
        lineTo(12f, 4.2f)
        lineTo(20.4f, 11.2f)
    }
    strokePath(width = 2f) {
        moveTo(5.9f, 10.2f)
        lineTo(5.9f, 20.2f)
        lineTo(18.1f, 20.2f)
        lineTo(18.1f, 10.2f)
    }
    strokePath(width = 2f) {
        moveTo(10.1f, 20.2f)
        lineTo(10.1f, 14.6f)
        lineTo(13.9f, 14.6f)
        lineTo(13.9f, 20.2f)
    }
}

/** 历史：怀表——圆盘 + 指针。 */
val PopClock: ImageVector = popIcon("PopClock") {
    strokePath(width = 2f) {
        moveTo(3.9f, 12f)
        arcToRelative(8.1f, 8.1f, 0f, true, true, 16.2f, 0f)
        arcToRelative(8.1f, 8.1f, 0f, true, true, -16.2f, 0f)
    }
    strokePath(width = 2f) {
        moveTo(12f, 7.4f)
        lineTo(12f, 12.2f)
        lineTo(15.3f, 14f)
    }
}

/** 创作：魔法棒——斜棒 + 双星芒。 */
val PopWand: ImageVector = popIcon("PopWand") {
    strokePath(width = 2.6f) {
        moveTo(4.4f, 19.6f)
        lineTo(14.2f, 9.8f)
    }
    fillPath {
        moveTo(16.2f, 3.4f)
        curveTo(16.5f, 5.9f, 17.5f, 6.9f, 20f, 7.8f)
        curveTo(17.5f, 8.7f, 16.5f, 9.7f, 16.2f, 12.2f)
        curveTo(15.9f, 9.7f, 14.9f, 8.7f, 12.4f, 7.8f)
        curveTo(14.9f, 6.9f, 15.9f, 5.9f, 16.2f, 3.4f)
        close()
    }
    fillPath { dot(20.6f, 13.4f, 1.05f) }
    fillPath { dot(11.8f, 3.4f, 0.95f) }
}

/** 修图：橡皮擦——斜置圆角块 + 分割线。 */
val PopRetouch: ImageVector = popIcon("PopRetouch") {
    strokePath(width = 2f) {
        moveTo(4.6f, 14.6f)
        lineTo(12.4f, 6.8f)
        arcToRelative(2.2f, 2.2f, 0f, false, true, 3.1f, 0f)
        lineTo(19.2f, 10.5f)
        arcToRelative(2.2f, 2.2f, 0f, false, true, 0f, 3.1f)
        lineTo(13.5f, 19.3f)
        arcToRelative(2.2f, 2.2f, 0f, false, true, -3.1f, 0f)
        lineTo(4.6f, 13.5f)
        arcToRelative(1f, 1f, 0f, false, true, 0f, -1.1f)
        close()
    }
    strokePath(width = 2f) {
        moveTo(9.1f, 10.1f)
        lineTo(15.9f, 16.9f)
    }
    strokePath(width = 2f, block = {
        moveTo(4f, 20.6f)
        lineTo(11.2f, 20.6f)
    })
}

/** 设置：滑杆组——三条滑轨 + 三个旋钮。 */
val PopSliders: ImageVector = popIcon("PopSliders") {
    strokePath(width = 2f) {
        moveTo(4f, 7f)
        lineTo(20f, 7f)
    }
    strokePath(width = 2f) {
        moveTo(4f, 12f)
        lineTo(20f, 12f)
    }
    strokePath(width = 2f) {
        moveTo(4f, 17f)
        lineTo(20f, 17f)
    }
    fillPath { dot(9.4f, 7f, 2.1f) }
    fillPath { dot(15.4f, 12f, 2.1f) }
    fillPath { dot(7.4f, 17f, 2.1f) }
}

/** 主题：调色盘—— blobs + 三个颜料点。 */
val PopPalette: ImageVector = popIcon("PopPalette") {
    strokePath(width = 1.9f) {
        moveTo(12f, 3.8f)
        curveTo(7.4f, 3.8f, 3.7f, 7.5f, 3.7f, 12f)
        curveTo(3.7f, 16.5f, 7.4f, 20.2f, 12f, 20.2f)
        curveTo(13.4f, 20.2f, 14.2f, 19.3f, 14.2f, 18.2f)
        curveTo(14.2f, 17.6f, 13.9f, 17.2f, 13.6f, 16.8f)
        curveTo(13.3f, 16.4f, 13f, 16f, 13f, 15.5f)
        curveTo(13f, 14.6f, 13.7f, 13.9f, 14.6f, 13.9f)
        lineTo(16.6f, 13.9f)
        curveTo(18.8f, 13.9f, 20.3f, 12.5f, 20.3f, 10.7f)
        curveTo(20.3f, 6.8f, 16.5f, 3.8f, 12f, 3.8f)
        close()
    }
    fillPath { dot(8.4f, 9.2f, 1.15f) }
    fillPath { dot(12.1f, 7.5f, 1.15f) }
    fillPath { dot(15.3f, 10.3f, 1.15f) }
}

/** 翻译：上下交错的手绘双向箭头。 */
val PopTranslate: ImageVector = popIcon("PopTranslate") {
    strokePath(width = 2.1f) {
        moveTo(4f, 7f)
        lineTo(17f, 7f)
        lineTo(14.2f, 4.2f)
        moveTo(17f, 7f)
        lineTo(14.2f, 9.8f)
    }
    strokePath(width = 2.1f) {
        moveTo(20f, 17f)
        lineTo(7f, 17f)
        lineTo(9.8f, 14.2f)
        moveTo(7f, 17f)
        lineTo(9.8f, 19.8f)
    }
}

/** 观察：取景框 + 放大镜，替代通用图片搜索图标。 */
val PopInspect: ImageVector = popIcon("PopInspect") {
    strokePath(width = 2f) {
        moveTo(4.6f, 10.2f)
        arcToRelative(5.8f, 5.8f, 0f, true, true, 11.6f, 0f)
        arcToRelative(5.8f, 5.8f, 0f, true, true, -11.6f, 0f)
    }
    strokePath(width = 2.4f) {
        moveTo(16.5f, 16.5f)
        lineTo(20.2f, 20.2f)
    }
    fillPath { dot(10.4f, 10.2f, 1.2f) }
}

/** 刷新：不规则圆环 + 轻快箭头。 */
val PopRefresh: ImageVector = popIcon("PopRefresh") {
    strokePath(width = 2.1f) {
        moveTo(19.2f, 8.6f)
        arcToRelative(7.5f, 7.5f, 0f, true, false, .4f, 6.5f)
        moveTo(19.2f, 8.6f)
        lineTo(19.2f, 4.8f)
        moveTo(19.2f, 8.6f)
        lineTo(15.5f, 8.6f)
    }
}

/** 下载：向下落入托盘的箭头。 */
val PopDownload: ImageVector = popIcon("PopDownload") {
    strokePath(width = 2.2f) {
        moveTo(12f, 3.8f)
        lineTo(12f, 14.5f)
        moveTo(7.8f, 10.6f)
        lineTo(12f, 14.8f)
        lineTo(16.2f, 10.6f)
    }
    strokePath(width = 2.1f) {
        moveTo(5.2f, 18.4f)
        lineTo(18.8f, 18.4f)
    }
}

/** 编辑：带圆头的斜铅笔。 */
val PopEdit: ImageVector = popIcon("PopEdit") {
    strokePath(width = 2.3f) {
        moveTo(5.2f, 18.8f)
        lineTo(6.4f, 14.2f)
        lineTo(16.6f, 4f)
        lineTo(20f, 7.4f)
        lineTo(9.8f, 17.6f)
        close()
    }
    strokePath(width = 1.5f) {
        moveTo(14.8f, 5.8f)
        lineTo(18.2f, 9.2f)
    }
}

/** 复制：两张错位的纸片。 */
val PopCopy: ImageVector = popIcon("PopCopy") {
    strokePath(width = 1.9f) {
        moveTo(8f, 7.2f)
        lineTo(18.2f, 7.2f)
        lineTo(18.2f, 19.2f)
        lineTo(8f, 19.2f)
        close()
    }
    strokePath(width = 1.9f) {
        moveTo(5.8f, 16.2f)
        lineTo(5.8f, 4.8f)
        lineTo(15.8f, 4.8f)
    }
}

/** 分享：三个圆点与轻量连接线。 */
val PopShare: ImageVector = popIcon("PopShare") {
    strokePath(width = 1.8f) {
        moveTo(7.2f, 12f)
        lineTo(16.8f, 6.8f)
        moveTo(7.2f, 12f)
        lineTo(16.8f, 17.2f)
    }
    fillPath { dot(6f, 12f, 2.2f) }
    fillPath { dot(18f, 6.2f, 2.2f) }
    fillPath { dot(18f, 17.8f, 2.2f) }
}

/** 删除：带粗墨线的纸篓。 */
val PopTrash: ImageVector = popIcon("PopTrash") {
    strokePath(width = 2f) {
        moveTo(6.2f, 7.8f)
        lineTo(17.8f, 7.8f)
        lineTo(16.8f, 19.4f)
        lineTo(7.2f, 19.4f)
        close()
        moveTo(4.8f, 5.2f)
        lineTo(19.2f, 5.2f)
        moveTo(9.2f, 5.2f)
        lineTo(10.2f, 2.9f)
        lineTo(13.8f, 2.9f)
        lineTo(14.8f, 5.2f)
    }
}

/** 右移：短促的手绘方向箭头。 */
val PopChevron: ImageVector = popIcon("PopChevron") {
    strokePath(width = 2.4f) {
        moveTo(9f, 5.5f)
        lineTo(15.5f, 12f)
        lineTo(9f, 18.5f)
    }
}

/** 导入图片：相框 + 加号。 */
val PopImageAdd: ImageVector = popIcon("PopImageAdd") {
    strokePath(width = 1.9f) {
        moveTo(4.2f, 5.2f)
        lineTo(15.5f, 5.2f)
        lineTo(15.5f, 18.8f)
        lineTo(4.2f, 18.8f)
        close()
        moveTo(5.3f, 15.8f)
        lineTo(8.5f, 12.4f)
        lineTo(10.6f, 14.4f)
        lineTo(12.7f, 12.1f)
    }
    strokePath(width = 2f) {
        moveTo(18.2f, 7.2f)
        lineTo(18.2f, 15.8f)
        moveTo(13.9f, 11.5f)
        lineTo(22.5f, 11.5f)
    }
}

/** 连接端点：小插头造型，避免直接使用链条图标。 */
val PopPlug: ImageVector = popIcon("PopPlug") {
    strokePath(width = 2f) {
        moveTo(8.4f, 4.2f)
        lineTo(8.4f, 9.3f)
        moveTo(15.6f, 4.2f)
        lineTo(15.6f, 9.3f)
        moveTo(6.4f, 8.6f)
        lineTo(17.6f, 8.6f)
        curveTo(17.6f, 13.3f, 15.5f, 15.8f, 12f, 15.8f)
        curveTo(8.5f, 15.8f, 6.4f, 13.3f, 6.4f, 8.6f)
        moveTo(12f, 15.8f)
        lineTo(12f, 20.2f)
    }
}

/** 密钥：圆环 + 短齿。 */
val PopKey: ImageVector = popIcon("PopKey") {
    strokePath(width = 2f) {
        // 锁孔环：正圆圆环（弦长 = 直径，避免两段大弧拼出扁圆）
        moveTo(16.3f, 10.4f)
        arcToRelative(2.95f, 2.95f, 0f, true, true, -5.9f, 0f)
        arcToRelative(2.95f, 2.95f, 0f, true, true, 5.9f, 0f)
        // 钥匙柄：从环右下缘斜向右下
        moveTo(15.5f, 12.5f)
        lineTo(20.6f, 19.4f)
        // 钥匙齿
        moveTo(18.2f, 17.1f)
        lineTo(20.3f, 15.0f)
    }
}

/** 书签：圆角纸签 + 底部切角。 */
val PopBookmark: ImageVector = popIcon("PopBookmark") {
    strokePath(width = 2f) {
        moveTo(6.2f, 4.2f)
        lineTo(17.8f, 4.2f)
        lineTo(17.8f, 20.2f)
        lineTo(12f, 16.7f)
        lineTo(6.2f, 20.2f)
        close()
    }
}

/** 圆心：无风格状态使用的简洁手绘圆。 */
val PopCircle: ImageVector = popIcon("PopCircle") {
    strokePath(width = 2.2f) {
        moveTo(4.2f, 12f)
        arcToRelative(7.8f, 7.8f, 0f, true, true, 15.6f, 0f)
        arcToRelative(7.8f, 7.8f, 0f, true, true, -15.6f, 0f)
    }
}

/** 电影感：胶片框 + 两个孔位。 */
val PopFilm: ImageVector = popIcon("PopFilm") {
    strokePath(width = 1.9f) {
        moveTo(4f, 5f)
        lineTo(20f, 5f)
        lineTo(20f, 19f)
        lineTo(4f, 19f)
        close()
        moveTo(8f, 5f)
        lineTo(8f, 19f)
        moveTo(16f, 5f)
        lineTo(16f, 19f)
    }
    fillPath { dot(6f, 8f, .8f) }
    fillPath { dot(6f, 16f, .8f) }
    fillPath { dot(18f, 8f, .8f) }
    fillPath { dot(18f, 16f, .8f) }
}

/** 立体：简化几何方块。 */
val PopCube: ImageVector = popIcon("PopCube") {
    strokePath(width = 1.9f) {
        moveTo(12f, 3.8f)
        lineTo(19.3f, 8f)
        lineTo(19.3f, 16.2f)
        lineTo(12f, 20.2f)
        lineTo(4.7f, 16.2f)
        lineTo(4.7f, 8f)
        close()
        moveTo(4.7f, 8f)
        lineTo(12f, 12.1f)
        lineTo(19.3f, 8f)
        moveTo(12f, 12.1f)
        lineTo(12f, 20.2f)
    }
}

/** 产品：方盒 + 盖线。 */
val PopBox: ImageVector = popIcon("PopBox") {
    strokePath(width = 1.9f) {
        moveTo(4.5f, 8.2f)
        lineTo(12f, 4.2f)
        lineTo(19.5f, 8.2f)
        lineTo(12f, 12.3f)
        close()
        moveTo(4.5f, 8.2f)
        lineTo(4.5f, 16.4f)
        lineTo(12f, 20.2f)
        lineTo(19.5f, 16.4f)
        lineTo(19.5f, 8.2f)
        moveTo(12f, 12.3f)
        lineTo(12f, 20.2f)
    }
}

/** 人像：圆头 + 肩部轮廓。 */
val PopPerson: ImageVector = popIcon("PopPerson") {
    strokePath(width = 1.9f) {
        moveTo(12f, 4.2f)
        arcToRelative(3.3f, 3.3f, 0f, true, true, 6.6f, 0f)
        arcToRelative(3.3f, 3.3f, 0f, true, true, -6.6f, 0f)
        moveTo(4.6f, 19.8f)
        curveTo(5.2f, 15.7f, 7.7f, 13.7f, 12f, 13.7f)
        curveTo(16.3f, 13.7f, 18.8f, 15.7f, 19.4f, 19.8f)
    }
}

/** 像素：四格构成的简洁网格。 */
val PopGrid: ImageVector = popIcon("PopGrid") {
    strokePath(width = 1.9f) {
        moveTo(5f, 5f)
        lineTo(10.2f, 5f)
        lineTo(10.2f, 10.2f)
        lineTo(5f, 10.2f)
        close()
        moveTo(13.8f, 5f)
        lineTo(19f, 5f)
        lineTo(19f, 10.2f)
        lineTo(13.8f, 10.2f)
        close()
        moveTo(5f, 13.8f)
        lineTo(10.2f, 13.8f)
        lineTo(10.2f, 19f)
        lineTo(5f, 19f)
        close()
        moveTo(13.8f, 13.8f)
        lineTo(19f, 13.8f)
        lineTo(19f, 19f)
        lineTo(13.8f, 19f)
        close()
    }
}

/** 铅笔：较轻的素描笔尖。 */
val PopPencil: ImageVector = popIcon("PopPencil") {
    strokePath(width = 2.1f) {
        moveTo(5.2f, 18.8f)
        lineTo(6.6f, 14.2f)
        lineTo(16.9f, 3.9f)
        lineTo(20.1f, 7.1f)
        lineTo(9.8f, 17.4f)
        close()
    }
    strokePath(width = 1.3f) {
        moveTo(14.9f, 5.9f)
        lineTo(18.1f, 9.1f)
    }
}

/** 霓虹闪电：赛博风格使用的折线闪电。 */
val PopBolt: ImageVector = popIcon("PopBolt") {
    fillPath {
        moveTo(13.8f, 2.8f)
        lineTo(5.4f, 13.1f)
        lineTo(11.2f, 13.1f)
        lineTo(9.8f, 21.2f)
        lineTo(18.6f, 10.2f)
        lineTo(12.8f, 10.2f)
        close()
    }
}

/** 极简：方框与留白。 */
val PopFrame: ImageVector = popIcon("PopFrame") {
    strokePath(width = 1.9f) {
        moveTo(3.6f, 7f)
        lineTo(20.4f, 7f)
        arcTo(1.6f, 1.6f, 0f, false, true, 20.4f, 8.6f)
        lineTo(20.4f, 15.4f)
        arcTo(1.6f, 1.6f, 0f, false, true, 18.8f, 17f)
        lineTo(5.2f, 17f)
        arcTo(1.6f, 1.6f, 0f, false, true, 3.6f, 15.4f)
        close()
    }
    strokePath(width = 1.9f) {
        moveTo(7.2f, 12f)
        lineTo(16.8f, 12f)
    }
}

/** 蒸汽朋克：齿轮感圆盘。 */
val PopGear: ImageVector = popIcon("PopGear") {
    strokePath(width = 1.9f) {
        moveTo(12f, 4.2f)
        arcToRelative(7.8f, 7.8f, 0f, true, true, 0f, 15.6f)
        arcToRelative(7.8f, 7.8f, 0f, true, true, 0f, -15.6f)
    }
    fillPath { dot(12f, 12f, 2.2f) }
    strokePath(width = 2f) {
        moveTo(12f, 2.6f)
        lineTo(12f, 5f)
        moveTo(12f, 19f)
        lineTo(12f, 21.4f)
        moveTo(2.6f, 12f)
        lineTo(5f, 12f)
        moveTo(19f, 12f)
        lineTo(21.4f, 12f)
    }
}

/** 浮世绘：一笔海浪。 */
val PopWave: ImageVector = popIcon("PopWave") {
    strokePath(width = 2f) {
        moveTo(3.5f, 14.5f)
        curveTo(6.2f, 10.5f, 8.8f, 10.5f, 11.5f, 14.5f)
        curveTo(14.2f, 18.5f, 16.8f, 18.5f, 20.5f, 13.2f)
        moveTo(3.5f, 18.4f)
        curveTo(6.2f, 14.4f, 8.8f, 14.4f, 11.5f, 18.4f)
        curveTo(14.2f, 22.4f, 16.8f, 22.4f, 20.5f, 17.1f)
    }
}

/** 暗黑：月牙。 */
val PopMoon: ImageVector = popIcon("PopMoon") {
    strokePath(width = 2.1f) {
        moveTo(17.8f, 4.3f)
        curveTo(13.1f, 5.3f, 10.4f, 9.9f, 11.7f, 14.2f)
        curveTo(12.6f, 17.4f, 15.4f, 19.7f, 18.8f, 19.7f)
        curveTo(16.9f, 21f, 14.5f, 21.5f, 12.1f, 20.8f)
        curveTo(7.1f, 19.3f, 4.3f, 14.1f, 5.8f, 9.1f)
        curveTo(7.2f, 4.6f, 12.5f, 2.3f, 17.8f, 4.3f)
    }
}

/** 可爱：圆润爱心。 */
val PopHeart: ImageVector = popIcon("PopHeart") {
    fillPath {
        moveTo(12f, 20.5f)
        curveTo(10.2f, 18.7f, 4.4f, 14.7f, 4.4f, 9.6f)
        curveTo(4.4f, 6.7f, 6.3f, 4.5f, 9f, 4.5f)
        curveTo(10.4f, 4.5f, 11.4f, 5.2f, 12f, 6.3f)
        curveTo(12.6f, 5.2f, 13.6f, 4.5f, 15f, 4.5f)
        curveTo(17.7f, 4.5f, 19.6f, 6.7f, 19.6f, 9.6f)
        curveTo(19.6f, 14.7f, 13.8f, 18.7f, 12f, 20.5f)
        close()
    }
}

/** 返回：向左的手绘方向箭头。 */
val PopBack: ImageVector = popIcon("PopBack") {
    strokePath(width = 2.3f) {
        moveTo(18.2f, 12f)
        lineTo(6.2f, 12f)
        moveTo(6.2f, 12f)
        lineTo(12f, 6.2f)
        moveTo(6.2f, 12f)
        lineTo(12f, 17.8f)
    }
}

/** 空心爱心：未收藏状态使用的轻量轮廓。 */
val PopHeartOutline: ImageVector = popIcon("PopHeartOutline") {
    strokePath(width = 1.9f) {
        moveTo(12f, 20.5f)
        curveTo(10.2f, 18.7f, 4.4f, 14.7f, 4.4f, 9.6f)
        curveTo(4.4f, 6.7f, 6.3f, 4.5f, 9f, 4.5f)
        curveTo(10.4f, 4.5f, 11.4f, 5.2f, 12f, 6.3f)
        curveTo(12.6f, 5.2f, 13.6f, 4.5f, 15f, 4.5f)
        curveTo(17.7f, 4.5f, 19.6f, 6.7f, 19.6f, 9.6f)
        curveTo(19.6f, 14.7f, 13.8f, 18.7f, 12f, 20.5f)
        close()
    }
}

/** 勾选：短促的手绘确认线。 */
val PopCheck: ImageVector = popIcon("PopCheck") {
    strokePath(width = 2.4f) {
        moveTo(4.5f, 12.2f)
        lineTo(9.4f, 17.1f)
        lineTo(19.5f, 6.8f)
    }
}

/** 关闭：圆润交叉线，作为弹窗和错误状态的统一动作图形。 */
val PopClose: ImageVector = popIcon("PopClose") {
    strokePath(width = 2.3f) {
        moveTo(5.3f, 5.3f)
        lineTo(18.7f, 18.7f)
        moveTo(18.7f, 5.3f)
        lineTo(5.3f, 18.7f)
    }
}

/** 警示：圆环 + 感叹号，替代错误提示中的通用 Material 图标。 */
val PopAlert: ImageVector = popIcon("PopAlert") {
    strokePath(width = 1.9f) {
        moveTo(12f, 3.8f)
        arcToRelative(8.2f, 8.2f, 0f, true, true, 0f, 16.4f)
        arcToRelative(8.2f, 8.2f, 0f, true, true, 0f, -16.4f)
    }
    strokePath(width = 2.2f) {
        moveTo(12f, 7.7f)
        lineTo(12f, 13.4f)
    }
    fillPath { dot(12f, 16.6f, 1.05f) }
}

/** 信息：圆环 + 小写 i，作为说明提示的统一图形。 */
val PopInfo: ImageVector = popIcon("PopInfo") {
    strokePath(width = 1.9f) {
        moveTo(12f, 3.8f)
        arcToRelative(8.2f, 8.2f, 0f, true, true, 0f, 16.4f)
        arcToRelative(8.2f, 8.2f, 0f, true, true, 0f, -16.4f)
    }
    fillPath { dot(12f, 7.2f, 1f) }
    strokePath(width = 2.1f) {
        moveTo(12f, 11f)
        lineTo(12f, 16.7f)
    }
}

/** 向下展开：保留展开语义，但与其他高频动作统一为手绘线条。 */
val PopChevronDown: ImageVector = popIcon("PopChevronDown") {
    strokePath(width = 2.3f) {
        moveTo(5.5f, 9f)
        lineTo(12f, 15.5f)
        lineTo(18.5f, 9f)
    }
}

/** 向上收起：与 PopChevronDown 成对使用。 */
val PopChevronUp: ImageVector = popIcon("PopChevronUp") {
    strokePath(width = 2.3f) {
        moveTo(5.5f, 15f)
        lineTo(12f, 8.5f)
        lineTo(18.5f, 15f)
    }
}

/** 核心动作映射：风格库使用同一套手绘图形，不再依赖 Emoji 字体。 */
fun popStyleIcon(id: String): ImageVector = when (id) {
    "none" -> PopCircle
    "cinematic" -> PopFilm
    "anime" -> PopSpark
    "watercolor" -> PopBrush
    "3d" -> PopCube
    "product" -> PopBox
    "portrait" -> PopPerson
    "ink" -> PopBrush
    "cyberpunk" -> PopBolt
    "minimal" -> PopFrame
    "pixel" -> PopGrid
    "figure" -> PopCube
    "oil" -> PopBrush
    "sketch" -> PopPencil
    "steam" -> PopGear
    "ukiyoe" -> PopWave
    "scifi" -> PopWand
    "dark" -> PopMoon
    "cute" -> PopHeart
    "retro" -> PopGallery
    else -> PopSpark
}
