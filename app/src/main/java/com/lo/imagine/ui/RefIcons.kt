package com.lo.imagine.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Theme53: refined reference-sheet glyphs, identical paths in day and night. */
private fun referenceIcon(name: String, stroke: String, fill: String): ImageVector =
    ImageVector.Builder(name = "Ref$name", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f).apply {
        if (stroke.isNotEmpty()) addPath(PathParser().parsePathString(stroke).toNodes(),
            stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        if (fill.isNotEmpty()) addPath(PathParser().parsePathString(fill).toNodes(),
            fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd)
    }.build()

object RefIcons {
    val Spark: ImageVector = referenceIcon("Spark", "", "M12 1.7 C13.6 8.7 15.3 10.4 22.3 12 C15.3 13.6 13.6 15.3 12 22.3 C10.4 15.3 8.7 13.6 1.7 12 C8.7 10.4 10.4 8.7 12 1.7 Z")
    val Sparks: ImageVector = referenceIcon("Sparks", "", "M11 3 C12.2 10.1 13.7 11.6 20 13 C13.7 14.4 12.2 15.9 11 23 C9.6 15.9 8.1 14.4 2 13 C8.1 11.6 9.6 10.1 11 3 Z M20 1 L21 3.5 L23.5 4.5 L21 5.5 L20 8 L19 5.5 L16.5 4.5 L19 3.5 Z M3 18 L3.7 19.8 L5.5 20.5 L3.7 21.2 L3 23 L2.3 21.2 L.5 20.5 L2.3 19.8 Z")
    val Undo: ImageVector = referenceIcon("Undo", "M3.5 10.5 A8.5 8.5 0 1 1 5.8 17.8 M3.5 4.5 V10.5 H9.5", "")
    val Translate: ImageVector = referenceIcon("Translate", "M3 5 H14 M8.5 2.5 V5 M11.6 5 C10.5 9 7.8 12.6 3.5 14.8 M5.2 8 C6.5 10.8 8.4 12.8 11.2 14.3 M12.8 21 L17.2 10.5 L21.6 21 M14.3 17.5 H20.1", "")
    val Image: ImageVector = referenceIcon("Image", "M5 3.5 H19 Q20.5 3.5 20.5 5 V19 Q20.5 20.5 19 20.5 H5 Q3.5 20.5 3.5 19 V5 Q3.5 3.5 5 3.5 Z M9.7 8.4 A1.6 1.6 0 1 1 6.5 8.4 A1.6 1.6 0 1 1 9.7 8.4 M6 20.5 L14.8 11.7 Q16 10.5 17.2 11.7 L20.5 15", "")
    val Trash: ImageVector = referenceIcon("Trash", "M4 6.5 H20 M8.5 6.5 V4.5 Q8.5 3 10 3 H14 Q15.5 3 15.5 4.5 V6.5 M6 6.5 L6.8 19 Q6.9 21 8.7 21 H15.3 Q17.1 21 17.2 19 L18 6.5 M10 10.5 V17 M14 10.5 V17", "")
    val Crop: ImageVector = referenceIcon("Crop", "M4 9 V4 H9 M15 4 H20 V9 M20 15 V20 H15 M9 20 H4 V15", "")
    val Film: ImageVector = referenceIcon("Film", "M3.5 8.5 H20.5 V20 H3.5 Z M3.5 8.5 L5.4 3.5 H22.2 L20.5 8.5 M8.3 8.5 L10.2 3.5 M13.2 8.5 L15.1 3.5 M18.1 8.5 L20 3.5", "")
    val Folder: ImageVector = referenceIcon("Folder", "M3.5 6 V20 H20.5 V8 H11 L9 4.7 H4.8 Q3.5 4.7 3.5 6 Z", "")
    val Settings: ImageVector = referenceIcon("Settings", "M12 2.7 L20.1 7.35 V16.65 L12 21.3 L3.9 16.65 V7.35 Z M12 8 L15.5 10 V14 L12 16 L8.5 14 V10 Z", "")
    val Style: ImageVector = referenceIcon("Style", "M12 3 C6.9 3 3 6.9 3 12 C3 17.1 7 21 12 21 H13 Q15 21 15 19.3 Q15 18.5 14.2 17.7 Q13.4 16.9 14 16.1 Q14.5 15.5 15.5 15.5 H17 Q21 15.5 21 12 C21 6.9 17.1 3 12 3 Z M7.5 10 H7.6 M10.5 6.8 H10.6 M15.2 7.2 H15.3 M17.3 10.8 H17.4", "")
    val Sliders: ImageVector = referenceIcon("Sliders", "M3.5 5.5 H7 M10.5 5.5 H20.5 M3.5 12 H13.5 M17 12 H20.5 M3.5 18.5 H6 M9.5 18.5 H20.5 M10.5 5.5 A1.75 1.75 0 1 1 7 5.5 A1.75 1.75 0 1 1 10.5 5.5 M17 12 A1.75 1.75 0 1 1 13.5 12 A1.75 1.75 0 1 1 17 12 M9.5 18.5 A1.75 1.75 0 1 1 6 18.5 A1.75 1.75 0 1 1 9.5 18.5", "")
    val Layers: ImageVector = referenceIcon("Layers", "M12 3 L21 8 L12 13 L3 8 Z M3 12 L12 17 L21 12 M3 16 L12 21 L21 16", "")
    val ArrowRight: ImageVector = referenceIcon("ArrowRight", "M3.5 12 H20.5 M13.5 5 L20.5 12 L13.5 19", "")
    val Chevron: ImageVector = referenceIcon("Chevron", "M9 5 L16 12 L9 19", "")
    val ChevronDown: ImageVector = referenceIcon("ChevronDown", "M5.5 8.5 L12 15 L18.5 8.5", "")
    val ChevronUp: ImageVector = referenceIcon("ChevronUp", "M5.5 15.5 L12 9 L18.5 15.5", "")
    val Expand: ImageVector = referenceIcon("Expand", "M14.5 3.5 H20.5 V9.5 M20 4 L13.5 10.5 M3.5 14.5 V20.5 H9.5 M4 20 L10.5 13.5", "")
    val Nai: ImageVector = referenceIcon("Nai", "M4.2 19 L12 4.5 L19.8 19 M8.2 19 L12 11.8 L15.8 19", "")
    val Transfer: ImageVector = referenceIcon("Transfer", "M4 7 H20 M16 3 L20 7 L16 11 M20 17 H4 M8 13 L4 17 L8 21", "")
    val Play: ImageVector = referenceIcon("Play", "", "M8 4.8 L19.2 12 L8 19.2 Z")
    val Home: ImageVector = referenceIcon("Home", "M3 10 L12 3 L21 10 M5 8.5 V21 H10 V14 H14 V21 H19 V8.5", "")
    val Brush: ImageVector = referenceIcon("Brush", "M10 14 L18.5 3.5 Q20.5 1.5 22 4 L13 15 M10 14 C6 12.5 6 18 2.5 20.5 C9 22.5 13.5 21 13 16 Z", "")
    val ImageAdd: ImageVector = referenceIcon("ImageAdd", "M13.5 4 H4 Q3 4 3 5 V19 Q3 20 4 20 H19 Q20 20 20 19 V13 M3.5 18 L9 12.5 L13.5 17 L16.5 14 M9.5 8 A1.5 1.5 0 1 1 6.5 8 A1.5 1.5 0 1 1 9.5 8 M19 2 V10 M15 6 H23", "")
    val Frame: ImageVector = referenceIcon("Frame", "M5 3.5 H19 Q20.5 3.5 20.5 5 V19 Q20.5 20.5 19 20.5 H5 Q3.5 20.5 3.5 19 V5 Q3.5 3.5 5 3.5 Z", "")
    val Clock: ImageVector = referenceIcon("Clock", "M21 12 A9 9 0 1 1 3 12 A9 9 0 1 1 21 12 M12 6 V12 L16 14", "")
    val Palette: ImageVector = referenceIcon("Palette", "M20.5 13.5 C23 6 15.5 .8 8 3.5 C2 5.3 1 13.2 5 17.8 C8.5 22 14 22 14 18.4 C14 16.5 12.5 15.6 14.3 14.4 C16 13.3 18.8 15.6 20.5 13.5 M7 8 H7.1 M12 6 H12.1 M17 8.2 H17.1 M6.3 13 H6.4", "")
    val Edit: ImageVector = referenceIcon("Edit", "M14 4.5 L18 1.8 L22 5.8 L10 17.8 L5.5 19 L6.7 14.5 L18 3.2 M14.4 6.9 L18.4 10.9 M12 4 H4 V21 H21 V13", "")
    val Pencil: ImageVector = referenceIcon("Pencil", "M4 15 L16.5 2.5 L21.5 7.5 L9 20 L2.5 21.5 Z M14 5 L19 10 M4 15 L9 20", "")
    val Wand: ImageVector = referenceIcon("Wand", "M4.2 17.4 L15.2 6.4 Q15.9 5.7 16.6 6.4 L18.1 7.9 Q18.8 8.6 18.1 9.3 L7.1 20.3 Q6.4 21 5.7 20.3 L4.2 18.8 Q3.5 18.1 4.2 17.4 Z M12.8 8.8 L15.7 11.7 M5.5 2.8 V7.2 M3.3 5 H7.7 M19.5 15.3 V19.7 M17.3 17.5 H21.7", "")
    val Download: ImageVector = referenceIcon("Download", "M12 3 V16 M7 11 L12 16 L17 11 M3.5 16 V21 H20.5 V16", "")
    val Refresh: ImageVector = referenceIcon("Refresh", "M20.5 8 A9 9 0 0 0 4.4 6 M20.5 3 V8 H15.5 M3.5 16 A9 9 0 0 0 19.6 18 M3.5 21 V16 H8.5", "")
    val Copy: ImageVector = referenceIcon("Copy", "M8 8 H21 V21 H8 Z M16 8 V3 H3 V16 H8", "")
    val Share: ImageVector = referenceIcon("Share", "M8 11 L16.5 6 M8 13 L16.5 18 M8 12 A3 3 0 1 1 2 12 A3 3 0 1 1 8 12 M22 4.5 A3 3 0 1 1 16 4.5 A3 3 0 1 1 22 4.5 M22 19.5 A3 3 0 1 1 16 19.5 A3 3 0 1 1 22 19.5", "")
    val Close: ImageVector = referenceIcon("Close", "M5 5 L19 19 M19 5 L5 19", "")
    val Check: ImageVector = referenceIcon("Check", "M4.5 12.5 L9.5 17.5 L20 6.5", "")
    val Back: ImageVector = referenceIcon("Back", "M20 12 H4 M11 5 L4 12 L11 19", "")
    val Alert: ImageVector = referenceIcon("Alert", "M10.5 3.5 Q12 1 13.5 3.5 L22 19 Q23 21 20.5 21 H3.5 Q1 21 2 19 Z M12 8 V13.5 M12 17 H12.1", "")
    val Info: ImageVector = referenceIcon("Info", "M21 12 A9 9 0 1 1 3 12 A9 9 0 1 1 21 12 M12 10.5 V17 M12 7 H12.1", "")
    val Person: ImageVector = referenceIcon("Person", "M16 7 A4 4 0 1 1 8 7 A4 4 0 1 1 16 7 M4 21 V19 Q4 14 12 14 Q20 14 20 19 V21", "")
    val Cube: ImageVector = referenceIcon("Cube", "M12 2 L21 7 V17 L12 22 L3 17 V7 Z M3 7 L12 12 L21 7 M12 12 V22", "")
    val Box: ImageVector = referenceIcon("Box", "M3 8 H21 V21 H3 Z M2 3 H22 V8 H2 Z M9 12 H15", "")
    val Circle: ImageVector = referenceIcon("Circle", "M21 12 A9 9 0 1 1 3 12 A9 9 0 1 1 21 12", "")
    val Grid: ImageVector = referenceIcon("Grid", "M3 3 H10 V10 H3 Z M14 3 H21 V10 H14 Z M3 14 H10 V21 H3 Z M14 14 H21 V21 H14 Z", "")
    val Bookmark: ImageVector = referenceIcon("Bookmark", "M5 3 H19 V22 L12 17.5 L5 22 Z", "")
    val Plug: ImageVector = referenceIcon("Plug", "M8 2 V7 M16 2 V7 M5 7 H19 M6.5 7 V11.5 A5.5 5.5 0 0 0 17.5 11.5 V7 M12 17 V22", "")
    val Key: ImageVector = referenceIcon("Key", "M12.5 7.5 A5 5 0 1 1 2.5 7.5 A5 5 0 1 1 12.5 7.5 M11 11 L21.5 21.5 M17 17 L20 14 M19 19 L22 16 M7 7 H7.1", "")
    val Inspect: ImageVector = referenceIcon("Inspect", "M17.5 10 A7.5 7.5 0 1 1 2.5 10 A7.5 7.5 0 1 1 17.5 10 M15.5 15.5 L22 22 M7 10 H13 M10 7 V13", "")
    val Wave: ImageVector = referenceIcon("Wave", "M2 12 H5 L8 3 L12 21 L16 7 L19 12 H22", "")
    val Bolt: ImageVector = referenceIcon("Bolt", "M13.5 2 L4 14 H10.5 L9.5 22 L20 9 H13 Z", "")
    val Moon: ImageVector = referenceIcon("Moon", "M20.8 14.2 A9.3 9.3 0 1 1 9.8 3.2 A7.5 7.5 0 0 0 20.8 14.2 Z", "")
    val HeartOutline: ImageVector = referenceIcon("HeartOutline", "M12 21 L3.5 12.3 C-2 6 6 .2 12 7 C18 .2 26 6 20.5 12.3 Z", "")
    val Heart: ImageVector = referenceIcon("Heart", "", "M12 21 L3.5 12.3 C-2 6 6 .2 12 7 C18 .2 26 6 20.5 12.3 Z")
}
