package com.lo.imagine.data.comfy

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.gson.Gson
import com.lo.imagine.data.GenerationTasks
import com.lo.imagine.data.TaskOutcome
import com.lo.imagine.util.HistoryMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** One owner shared by the workbench and settings. No Activity or Composable owns the task. */
data class LocalComfyImage(val file: File, val filename: String)

suspend fun copyComfyImage(context: Context, uri: Uri): LocalComfyImage = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
    val mime = resolver.getType(uri).orEmpty().lowercase()
    require(mime.isBlank() || mime.startsWith("image/")) { "请选择图片文件" }
    val suffix = when {
        mime == "image/png" -> "png"
        mime == "image/jpeg" -> "jpg"
        mime == "image/webp" -> "webp"
        else -> displayName?.substringAfterLast('.', "")?.lowercase()?.takeIf { it in setOf("png", "jpg", "jpeg", "webp", "bmp", "gif") } ?: "img"
    }
    val base = displayName?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf {
        it.isNotBlank() && it.length <= 180 && it.none { char -> char.isISOControl() }
    }
    val filename = if (base != null) base else "reference_${comfyId()}.$suffix"
    val target = File.createTempFile("comfy-upload-", ".part", context.cacheDir)
    try {
        val input = resolver.openInputStream(uri) ?: error("图片文件无法读取")
        var size = 0L
        FileOutputStream(target).use { output ->
            input.use { stream ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    size += count
                    require(size <= COMFY_MAX_UPLOAD_BYTES) { "图片超过 128 MiB" }
                    output.write(buffer, 0, count)
                }
            }
        }
        require(size > 0) { "图片文件为空" }
        LocalComfyImage(target, filename)
    } catch (e: Exception) {
        target.delete()
        throw e
    }
}

class ComfyRuntime private constructor(context: Context) {
    private val app = context.applicationContext
    val backend: WorkflowBackend = ComfyClient()
    val repository = ComfyRepository(ComfyStore(File(app.filesDir, "comfy"), File(app.noBackupFilesDir, "comfy-connection.json")))
    val coordinator = ComfyTaskCoordinator(repository, backend, AndroidComfyArchive(app), launch = { work ->
        GenerationTasks.launch(app, doneTitle = "ComfyUI 任务完成", failTitle = "ComfyUI 任务未完成", cancelTitle = "ComfyUI 等待已暂停") {
            work()
            val state = repository.state.value
            val job = state.jobs.find { it.id == state.activeJobId }
            TaskOutcome(job?.message ?: state.error ?: state.status, state.error == null && job?.phase == ComfyPhase.SUCCEEDED)
        }
    })
    companion object {
        @Volatile private var instance: ComfyRuntime? = null
        fun get(context: Context): ComfyRuntime = instance ?: synchronized(this) {
            instance ?: ComfyRuntime(context).also { instance = it }
        }
    }
}

class AndroidComfyArchive(private val context: Context) : ComfyArchive {
    private val gson = Gson()
    override fun temporary(job: ComfyJob, image: RemoteImage): File =
        File(context.cacheDir, "comfy").apply { mkdirs() }.let { File(it, "${job.id}_${image.key}_${comfyId()}.part") }

    override suspend fun save(job: ComfyJob, image: RemoteImage, temporary: File): String = withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(temporary.path, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "下载内容不是有效图片" }
        val extension = when (options.outMimeType) { "image/png" -> "png"; "image/jpeg" -> "jpg"; "image/webp" -> "webp"; else -> error("首版仅支持 PNG/JPEG/WebP 静态图片") }
        // A bounded decode detects truncated payloads without retaining full-size bitmap batches.
        val sample = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (options.outWidth / inSampleSize > 2048 || options.outHeight / inSampleSize > 2048) inSampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeFile(temporary.path, sample) ?: error("图片损坏或无法解码")
        bitmap.recycle()
        val dir = File(context.filesDir, "history").apply { mkdirs() }
        val stem = "comfy_${job.id}_${image.key}"
        val target = File(dir, "$stem.$extension")
        val meta = HistoryMeta(prompt = job.prompt, model = job.workflowName, kind = "comfy", createdAt = job.startedAt,
            elapsedSec = ((System.currentTimeMillis() - job.startedAt) / 1000).coerceAtLeast(0),
            workflowDetails = "ComfyUI · ${job.workflowName}\n任务 ${job.promptId}\n${options.outWidth} × ${options.outHeight}\n" + job.values.entries.joinToString("\n") { "${it.key}：${it.value}" })
        val stagedMeta = File(dir, "$stem.json.pending")
        stagedMeta.writeText(gson.toJson(meta))
        check(stagedMeta.renameTo(File(dir, "$stem.json"))) { "作品参数保存失败" }
        if (!target.exists()) {
            val staged = File(dir, "$stem.image.pending")
            try { temporary.copyTo(staged, overwrite = true); check(staged.renameTo(target)) { "作品保存失败" } }
            finally { staged.delete() }
        }
        target.absolutePath
    }

    override suspend fun gallery(path: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        val mime = when (file.extension) { "png" -> "image/png"; "jpg" -> "image/jpeg"; "webp" -> "image/webp"; else -> return@withContext false }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val relative = "${Environment.DIRECTORY_PICTURES}/Imagine/"
                val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.IS_PENDING)
                resolver.query(collection, projection, "${MediaStore.Images.Media.DISPLAY_NAME}=? AND ${MediaStore.Images.Media.RELATIVE_PATH}=?",
                    arrayOf(file.name, relative), null)?.use { rows ->
                    while (rows.moveToNext()) {
                        val uri = android.content.ContentUris.withAppendedId(collection, rows.getLong(0))
                        if (rows.getInt(1) == 0) return@withContext true
                        resolver.delete(uri, null, null) // Recover only this app's stable, unfinished output.
                    }
                }
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, file.name); put(MediaStore.Images.Media.MIME_TYPE, mime)
                    put(MediaStore.Images.Media.RELATIVE_PATH, relative); put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(collection, values) ?: return@withContext false
                try {
                    val output = resolver.openOutputStream(uri) ?: error("无法写入相册")
                    output.use { out -> file.inputStream().use { it.copyTo(out) } }
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Imagine").apply { mkdirs() }
                val target = File(dir, file.name)
                if (!target.exists()) {
                    val temp = File(dir, file.name + ".pending")
                    try { file.copyTo(temp, overwrite = true); check(temp.renameTo(target)) } finally { temp.delete() }
                }
                MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(mime), null)
            }
            true
        } catch (e: Exception) { false }
    }
}