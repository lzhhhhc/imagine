package com.lo.imagine.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues
import androidx.core.content.FileProvider
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 历史图片的元数据，随图片一起存 sidecar json */
data class HistoryMeta(
    val prompt: String = "",
    val model: String = "",
    val kind: String = "gen",
    val createdAt: Long = 0L,
    /** 生成耗时（秒），预览页角标展示「用时 Xs」 */
    val elapsedSec: Long = 0L,
    val workflowDetails: String? = null
)

data class HistoryEntry(
    val file: File,
    val meta: HistoryMeta
)
object ImageUtils {

    private val gson = Gson()

    /** 历史时间戳 → MM-dd HH:mm，预览页角标展示 */
    fun formatTimestamp(ts: Long): String =
        java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(ts))

    /** 生成耗时 → 「用时 32s」/「用时 1分12秒」 */
    fun formatElapsed(sec: Long): String = when {
        sec <= 0 -> ""
        sec < 60 -> "用时 ${sec}s"
        else -> "用时 ${sec / 60}分${sec % 60}秒"
    }


    fun decodeBase64(raw: String): Bitmap? = try {
        val clean = if (raw.startsWith("data:")) raw.substringAfter("base64,") else raw
        val bytes = android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }

    fun bitmapToPng(bitmap: Bitmap): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    /** JPEG 编码（体积友好，适合回填 b64Json 与落库） */
    fun bitmapToJpegBytes(bitmap: Bitmap, quality: Int = 92): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    /**
     * 画质/画幅兜底。上游偶尔无视 size 参数（实测 gpt-image-2 请求 1728x3072
     * 只回 608x1088，或干脆按别的画幅出图）。按偏差程度分两档处理：
     * 1. 输出画幅与目标接近（偏差 ≤20%，含枚举吸附/64 对齐的固有偏差）→
     *    覆盖式放大后居中裁切到精确目标尺寸，比例不失真；
     * 2. 输出画幅与目标严重不符（上游明显无视了请求）→ 保持输出原始画幅，
     *    不拉伸、不裁切（拉伸会变形、大偏差裁切会砍掉大量内容），仅在
     *    长边不足画质档时按原比例放大补齐。
     */
    fun ensureResolution(source: Bitmap?, targetWidth: Int, targetHeight: Int): Bitmap {
        if (source == null || source.isRecycled) return source ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        if (targetWidth <= 0 || targetHeight <= 0) return source
        val srcRatio = source.width.toFloat() / source.height
        val targetRatio = targetWidth.toFloat() / targetHeight
        val aspectDev = kotlin.math.abs(srcRatio - targetRatio) / targetRatio
        if (aspectDev <= 0.20f) {
            // 轻微偏差：覆盖式放大 + 居中裁切，产物 = 精确目标尺寸，内容比例不变
            val coverScale = maxOf(
                targetWidth.toFloat() / source.width,
                targetHeight.toFloat() / source.height
            )
            val coverW = (source.width * coverScale).toInt().coerceAtLeast(targetWidth)
            val coverH = (source.height * coverScale).toInt().coerceAtLeast(targetHeight)
            val cover = Bitmap.createScaledBitmap(source, coverW, coverH, true)
            val x = ((coverW - targetWidth) / 2).coerceAtLeast(0)
            val y = ((coverH - targetHeight) / 2).coerceAtLeast(0)
            return if (x == 0 && y == 0 && cover.width == targetWidth && cover.height == targetHeight) {
                cover
            } else {
                val out = Bitmap.createBitmap(cover, x, y, targetWidth, targetHeight)
                if (out !== cover) cover.recycle()
                out
            }
        }
        // 严重偏差：保持上游真实输出的画幅，仅补齐画质（长边不足时按原比例放大）
        val srcLong = maxOf(source.width, source.height)
        val targetLong = maxOf(targetWidth, targetHeight)
        if (srcLong >= targetLong) return source
        val scale = targetLong.toFloat() / srcLong
        return Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    /**
     * 多图参考组合：把最多 6 张参考图按 3 列网格拼成一张（白底居中缩放），
     * 供 OpenAI edits 单图协议做多参考图输入（角色三视图/多角度参考）。
     */
    fun combineReferenceGrid(bitmaps: List<Bitmap>, cellSize: Int = 512): Bitmap {
        if (bitmaps.isEmpty()) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        if (bitmaps.size == 1) return bitmaps.first()
        val cols = minOf(3, bitmaps.size)
        val rows = (bitmaps.size + cols - 1) / cols
        val out = Bitmap.createBitmap(cols * cellSize, rows * cellSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        canvas.drawColor(android.graphics.Color.WHITE)
        bitmaps.forEachIndexed { i, bmp ->
            val col = i % cols
            val row = i / cols
            val scale = minOf(
                cellSize.toFloat() / bmp.width,
                cellSize.toFloat() / bmp.height
            )
            val w = (bmp.width * scale).toInt().coerceAtLeast(1)
            val h = (bmp.height * scale).toInt().coerceAtLeast(1)
            val left = col * cellSize + (cellSize - w) / 2
            val top = row * cellSize + (cellSize - h) / 2
            val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
            canvas.drawBitmap(scaled, left.toFloat(), top.toFloat(), null)
            if (scaled !== bmp) scaled.recycle()
        }
        return out
    }

    /**
     * 修图前预处理：仅当图片超大（最长边超过 [maxEdge]）时才轻度降采样，
     * 并用高质量 JPEG 编码控制请求体大小。普通图片分辨率保持原样。
     * 返回处理后的 (bitmap, bytes)。
     */
    fun prepareForEdit(bitmap: Bitmap, maxEdge: Int = 4096): Pair<Bitmap, ByteArray> {
        val w = bitmap.width
        val h = bitmap.height
        val maxDim = maxOf(w, h)
        val scale = if (maxDim > maxEdge) maxEdge.toFloat() / maxDim else 1f
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (w * scale).toInt().coerceAtLeast(1),
                (h * scale).toInt().coerceAtLeast(1),
                true
            )
        } else bitmap
        val out = java.io.ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 95, out)
        return scaled to out.toByteArray()
    }

    /**
     * DST_OUT 编码遮罩时，白色/不透明笔迹对应透明重绘区，未涂区域保持不透明。
     * 这是 Canvas 合成结果的纯函数等价规则，便于在无网络的 JVM 单测中锁定协议语义。
     */
    fun maskOutputAlpha(strokeAlpha: Int): Int =
        255 - strokeAlpha.coerceIn(0, 255)

    /**
     * 将手绘遮罩编码为 OpenAI edits 协议的 PNG：
     * 透明(alpha=0)=重绘区域，不透明=保留区域。
     * 注意：输出尺寸必须与原图完全一致（协议硬性要求），不做降采样。
     * 纯色遮罩 PNG 压缩率极高，全尺寸也不会撑大请求体。
     * 用硬件 Canvas + XFERMODE 整体绘制，避免逐像素循环。
     */
    fun encodeMaskPng(mask: Bitmap, srcWidth: Int, srcHeight: Int): ByteArray {
        val strokes = if (mask.width != srcWidth || mask.height != srcHeight) {
            Bitmap.createScaledBitmap(mask, srcWidth, srcHeight, true)
        } else mask
        // 结果图：默认全黑不透明（保留），再把涂色区域抠成透明（重绘）
        val out = Bitmap.createBitmap(srcWidth, srcHeight, Bitmap.Config.ARGB_8888)
        out.eraseColor(android.graphics.Color.BLACK)
        val canvas = android.graphics.Canvas(out)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
        }
        canvas.drawBitmap(strokes, 0f, 0f, paint)
        paint.xfermode = null

        val bos = java.io.ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.PNG, 100, bos)
        return bos.toByteArray()
    }

    fun decodeFile(file: File): Bitmap? = try {
        BitmapFactory.decodeFile(file.path)
    } catch (e: Exception) {
        null
    }

    /**
     * 保存到系统相册（Pictures/Imagine）。
     * Q+ 走 MediaStore 插入；关键点：一旦写流失败必须删掉这条 IS_PENDING=1 的行，
     * 否则相册里会留下一条永远不可见的挂起记录，用户表现为「保存了但看不到」。
     */
    fun saveToGallery(context: Context, bitmap: Bitmap): Uri? = try {
        val name = "Imagine_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Imagine")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            try {
                val stream = resolver.openOutputStream(uri) ?: throw java.io.IOException("无法打开相册输出流")
                stream.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
                uri
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                null
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.insertImage(
                context.contentResolver, bitmap, name, "Imagine 生成"
            )?.let { Uri.parse(it) }
        }
    } catch (e: Exception) {
        null
    }

    /**
     * 生成/修图完成后的统一落盘：先写应用内历史（作品库的数据源），
     * 再按用户开关写入系统相册。相册失败不影响作品库，两者互不阻塞。
     * 返回内部历史文件；历史写入失败时为 null。
     */
    fun archiveResult(
        context: Context,
        bitmap: Bitmap,
        prompt: String,
        model: String,
        kind: String,
        elapsedSec: Long = 0L,
        toGallery: Boolean = true
    ): File? {
        val file = saveToHistory(context, bitmap, prompt, model, kind, elapsedSec)
        if (toGallery) saveToGallery(context, bitmap)
        return file
    }

    /** 归档到应用内历史，附带提示词元数据 */
    fun saveToHistory(
        context: Context,
        bitmap: Bitmap,
        prompt: String,
        model: String,
        kind: String,
        elapsedSec: Long = 0L
    ): File? = try {
        val dir = File(context.filesDir, "history").apply { mkdirs() }
        val ts = System.currentTimeMillis()
        val img = File(dir, "img_$ts.png")
        FileOutputStream(img).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val meta = HistoryMeta(prompt = prompt, model = model, kind = kind, createdAt = ts, elapsedSec = elapsedSec)
        File(dir, "img_$ts.json").writeText(gson.toJson(meta))
        img
    } catch (e: Exception) {
        null
    }

    fun listHistory(context: Context): List<HistoryEntry> {
        val dir = File(context.filesDir, "history")
        val files = dir.listFiles { f -> f.isFile && f.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp") } ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }.map { f ->
            val metaFile = File(dir, f.nameWithoutExtension + ".json")
            val meta = if (metaFile.exists()) {
                runCatching { gson.fromJson(metaFile.readText(), HistoryMeta::class.java) }
                    .getOrNull() ?: HistoryMeta(createdAt = f.lastModified())
            } else {
                HistoryMeta(createdAt = f.lastModified())
            }
            HistoryEntry(f, meta)
        }
    }

    fun deleteHistory(entry: HistoryEntry) {
        runCatching {
            val meta = File(entry.file.parentFile, entry.file.nameWithoutExtension + ".json")
            if (meta.exists()) meta.delete()
            entry.file.delete()
        }
    }

    fun clearHistory(context: Context) {
        runCatching {
            File(context.filesDir, "history").listFiles()?.forEach { it.delete() }
        }
    }

    /** 通过 FileProvider 分享图片 */
    fun shareBitmap(context: Context, bitmap: Bitmap) {
        runCatching {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val f = File(dir, "imagine_${System.currentTimeMillis()}.png")
            FileOutputStream(f).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", f
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享图片"))
        }
    }

    fun formatTime(ts: Long): String =
        if (ts <= 0) "" else SimpleDateFormat("yyyy // MM // dd", Locale.getDefault()).format(Date(ts))
}