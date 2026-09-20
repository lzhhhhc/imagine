package com.lo.imagine.data

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 更新源：GitHub Release。仓库名是这里唯一需要维护的常量——
 * 换仓库只改这一处，检查更新与下载地址都由它派生。
 */
object UpdateSource {
    const val REPO = "lzhhhhc/imagine"
    const val LATEST_API = "https://api.github.com/repos/$REPO/releases/latest"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases"
}

/** GitHub release 资产（只取 APK 需要的最小字段）。 */
data class ReleaseAsset(
    @SerializedName("name") val name: String? = null,
    @SerializedName("browser_download_url") val downloadUrl: String? = null,
    @SerializedName("size") val size: Long = 0L
)

/** GitHub release 响应体。 */
data class ReleaseInfo(
    @SerializedName("tag_name") val tag: String? = null,
    @SerializedName("name") val title: String? = null,
    @SerializedName("body") val notes: String? = null,
    @SerializedName("published_at") val publishedAt: String? = null,
    @SerializedName("assets") val assets: List<ReleaseAsset>? = null
)

/** 面向界面的更新信息：只保留展示与下载需要的字段。 */
data class UpdateInfo(
    val tag: String,
    val title: String,
    val notes: String,
    val publishedAt: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long
)

/**
 * 版本号比较：忽略 `v` 前缀与 `-beta`/`+build` 之类的后缀，逐段按数值比较。
 * 返回 >0 表示 a 比 b 新。
 */
internal fun compareVersion(a: String, b: String): Int {
    fun parts(s: String): List<Int> = s.trim()
        .removePrefix("v").removePrefix("V")
        .substringBefore('-').substringBefore('+')
        .split('.')
        .map { seg -> seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
    val pa = parts(a)
    val pb = parts(b)
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val x = pa.getOrElse(i) { 0 }
        val y = pb.getOrElse(i) { 0 }
        if (x != y) return x - y
    }
    return 0
}

/** 从 release 资产里挑出 APK 附件（大小写不敏感，取第一个）。 */
internal fun pickApkAsset(assets: List<ReleaseAsset>?): ReleaseAsset? =
    assets?.firstOrNull { it.name?.endsWith(".apk", ignoreCase = true) == true }

/**
 * Release 正文是 Markdown，弹窗里按纯文本展示：
 * 去掉标题符号与强调标记，表格行折成「·」分隔，丢掉分隔线与空行。
 */
internal fun releaseNotesToPlainText(md: String): String {
    val tableSeparator = Regex("^\\|[\\s:|-]+\\|$")
    return md.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            line.removePrefix("### ").removePrefix("## ").removePrefix("# ")
                .replace("**", "").replace("`", "")
        }
        .filterNot { it.startsWith("---") }
        .filterNot { tableSeparator.matches(it) }
        .map { line ->
            if (line.startsWith("|") && line.endsWith("|")) {
                line.trim('|').split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    .joinToString(" · ")
            } else line
        }
        .joinToString("\n")
        .trim()
}

/** 发布时间 `2026-09-20T12:10:53Z` → `2026-09-20`；异常原样返回。 */
internal fun formatPublishedDate(raw: String): String {
    val idx = raw.indexOf('T')
    return if (idx > 0) raw.substring(0, idx) else raw
}

/**
 * 应用更新：检查 GitHub Release → 下载 APK → 拉起系统安装器。
 * 全部走 OkHttp（项目已有依赖），不引入新的网络库。
 */
class AppUpdater(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /** 当前安装版本名；取不到时返回 "0"。 */
    fun currentVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("").ifBlank { "0" }

    /**
     * 检查是否有新版本。
     * 返回 `Result.success(null)` 表示已是最新；失败时携带可展示的原因。
     */
    suspend fun checkForUpdate(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(UpdateSource.LATEST_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "imagine-android-updater")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val reason = when (response.code) {
                        403 -> "更新服务暂时不可用（请求过于频繁），稍后再试"
                        404 -> "还没有发布任何版本"
                        else -> "检查更新失败：HTTP ${response.code}"
                    }
                    return@withContext Result.failure(IOException(reason))
                }
                val body = response.body?.string().orEmpty()
                val info = runCatching { gson.fromJson(body, ReleaseInfo::class.java) }.getOrNull()
                    ?: return@withContext Result.failure(IOException("更新信息解析失败"))

                val tag = info.tag?.trim().orEmpty()
                if (tag.isEmpty()) return@withContext Result.failure(IOException("该发布没有版本号"))

                // 本地已是最新：不是错误，返回 null 让界面显示「已是最新」
                if (compareVersion(tag, currentVersionName()) <= 0) {
                    return@withContext Result.success(null)
                }

                val asset = pickApkAsset(info.assets)
                    ?: return@withContext Result.failure(IOException("该发布没有附带 APK"))
                val url = asset.downloadUrl?.trim().orEmpty()
                if (url.isEmpty()) return@withContext Result.failure(IOException("APK 下载地址缺失"))

                Result.success(
                    UpdateInfo(
                        tag = tag,
                        title = info.title?.trim().orEmpty().ifBlank { tag },
                        notes = releaseNotesToPlainText(info.notes.orEmpty()),
                        publishedAt = formatPublishedDate(info.publishedAt.orEmpty()),
                        apkUrl = url,
                        apkName = asset.name?.trim().orEmpty().ifBlank { "imagine-$tag.apk" },
                        apkSize = asset.size
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(IOException(e.message ?: "网络不可用，检查更新失败"))
        }
    }

    /** 下载 APK 到 cache/apk/，onProgress 回调 0f~1f 的进度。 */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(info.apkUrl)
                    .header("User-Agent", "imagine-android-updater")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(IOException("下载失败：HTTP ${response.code}"))
                    }
                    val body = response.body
                        ?: return@withContext Result.failure(IOException("下载响应为空"))
                    val total = if (info.apkSize > 0) info.apkSize else body.contentLength()
                    val dir = File(context.cacheDir, "apk").apply { mkdirs() }
                    val target = File(dir, info.apkName)

                    body.byteStream().use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var written = 0L
                            var lastPct = -1
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                written += read
                                if (total > 0) {
                                    val pct = ((written * 100) / total).toInt()
                                    if (pct != lastPct) {
                                        lastPct = pct
                                        onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                                    }
                                }
                            }
                        }
                    }
                    // 大小对不上说明下载被截断，不能拿半个包装上去
                    if (total > 0 && target.length() != total) {
                        target.delete()
                        return@withContext Result.failure(IOException("下载不完整，请重试"))
                    }
                    onProgress(1f)
                    Result.success(target)
                }
            } catch (e: Exception) {
                Result.failure(IOException(e.message ?: "下载失败"))
            }
        }

    /** Android 8.0+ 需要用户授予「安装未知应用」权限，否则安装器会直接拒绝。 */
    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** 已下载的包再装一次时避免重复下载。 */
    fun cachedApk(info: UpdateInfo): File? {
        val file = File(File(context.cacheDir, "apk"), info.apkName)
        return file.takeIf { it.exists() && (info.apkSize <= 0 || it.length() == info.apkSize) }
    }

    /** 拉起系统安装器。 */
    fun install(file: File): Result<Unit> = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
