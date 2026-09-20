package com.lo.imagine.ui.director

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.lo.imagine.data.DirectorAsset
import java.io.ByteArrayOutputStream

internal data class DirectorReferenceImage(val label: String, val bytes: ByteArray) {
    val base64: String get() = Base64.encodeToString(bytes, Base64.NO_WRAP)
    val dataUri: String get() = "data:image/jpeg;base64,$base64"
}

internal fun readDirectorReference(path: String, label: String): DirectorReferenceImage {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "$label 图片不存在或已损坏，请重新选择" }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2560) sample *= 2
    val raw = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: error("$label 图片读取失败")
    val scale = minOf(1f, 1280f / maxOf(raw.width, raw.height))
    val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(raw,
        (raw.width * scale).toInt().coerceAtLeast(1), (raw.height * scale).toInt().coerceAtLeast(1), true) else raw
    try {
        val output = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) { "$label 图片编码失败" }
        return DirectorReferenceImage(label, output.toByteArray())
    } finally {
        if (bitmap !== raw) bitmap.recycle()
        raw.recycle()
    }
}

internal fun directorAssetLabel(asset: DirectorAsset): String =
    "${if (asset.kind == "scene") "环境" else "人物"} · ${asset.name.ifBlank { "未命名" }}：${asset.desc}"

internal fun readDirectorAssets(assets: List<DirectorAsset>): List<DirectorReferenceImage> =
    assets.map { readDirectorReference(it.imagePath, directorAssetLabel(it)) }

internal fun referenceLegend(images: List<DirectorReferenceImage>): String =
    images.mapIndexed { i, image -> "参考图${i + 1} = ${image.label}" }.joinToString("\n")
