package com.lo.imagine.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lo.imagine.data.AppUpdater
import com.lo.imagine.data.UpdateInfo
import com.lo.imagine.data.UpdateSource
import com.lo.imagine.data.shouldOfferUpdate
import com.lo.imagine.ui.PIcon
import com.lo.imagine.ui.PopBusySpinner
import com.lo.imagine.ui.RefIcons
import com.lo.imagine.ui.celInk
import com.lo.imagine.ui.theme.PopRadius
import com.lo.imagine.ui.theme.RefHud
import com.lo.imagine.ui.theme.themedShape
import kotlinx.coroutines.launch
import java.io.File

/** 更新流程的稳定状态；互斥，避免出现「既在下载又说失败」。 */
private enum class UpdatePhase {
    IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, NEED_PERMISSION, READY, FAILED
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    else -> "%d KB".format(bytes / 1024)
}

/**
 * 设置 → 关于：检查更新卡。
 * 拉取 GitHub Release → 展示本次更新内容 → 下载 APK → 拉起系统安装器。
 */
@Composable
internal fun UpdateCard(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updater = remember { AppUpdater(context) }
    val currentVersion = remember { updater.currentVersionName() }

    var phase by remember { mutableStateOf(UpdatePhase.IDLE) }
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    /** 已下载好的安装包：权限补齐后直接用它重试，不重新下载 */
    var apk by remember { mutableStateOf<File?>(null) }
    var message by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    var showNotes by remember { mutableStateOf(false) }

    /**
     * 权限已备好就直接拉起安装器；否则切到「待授权」并带用户去设置页。
     * 关键：这里失败只是状态切换，绝不抛异常。
     */
    fun tryInstall(target: File) {
        if (!updater.canInstallPackages()) {
            phase = UpdatePhase.NEED_PERMISSION
            message = "需要先允许「安装未知应用」"
            openInstallPermission(context)
            return
        }
        updater.install(target)
            .onSuccess { phase = UpdatePhase.READY }
            .onFailure {
                phase = UpdatePhase.FAILED
                message = it.message ?: "无法打开安装器"
            }
    }

    fun check() {
        if (phase == UpdatePhase.CHECKING || phase == UpdatePhase.DOWNLOADING) return
        phase = UpdatePhase.CHECKING
        message = ""
        apk = null
        scope.launch {
            updater.checkForUpdate()
                .onSuccess { found ->
                    if (found == null) {
                        phase = UpdatePhase.UP_TO_DATE
                        message = "当前 v$currentVersion 已是最新版本"
                    } else {
                        info = found
                        phase = UpdatePhase.AVAILABLE
                        showNotes = true
                    }
                }
                .onFailure {
                    phase = UpdatePhase.FAILED
                    message = it.message ?: "检查更新失败"
                }
        }
    }

    fun download() {
        val target = info ?: return
        // 已经下载完的同名包直接进安装，不重复下载
        updater.cachedApk(target)?.let { cached ->
            apk = cached
            tryInstall(cached)
            return
        }
        phase = UpdatePhase.DOWNLOADING
        progress = 0f
        message = ""
        scope.launch {
            updater.download(target) { p -> progress = p }
                .onSuccess { file ->
                    apk = file
                    tryInstall(file)
                }
                .onFailure {
                    phase = UpdatePhase.FAILED
                    message = it.message ?: "下载失败"
                }
        }
    }

    /** 主按钮行为：按当前状态决定下一步，避免「重试」误触发重新下载 */
    fun primaryAction() {
        when (phase) {
            UpdatePhase.AVAILABLE -> download()
            UpdatePhase.NEED_PERMISSION -> {
                val pending = apk
                if (pending == null) check() else tryInstall(pending)
            }
            UpdatePhase.READY -> apk?.let { tryInstall(it) } ?: check()
            UpdatePhase.FAILED -> if (apk != null) tryInstall(apk!!) else check()
            else -> check()
        }
    }

    Surface(
        shape = themedShape(PopRadius.card),
        color = c.surface,
        border = BorderStroke(.8.dp, c.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).background(c.primaryContainer, themedShape(PopRadius.chip)),
                    contentAlignment = Alignment.Center
                ) {
                    PIcon(RefIcons.Refresh, null, Modifier.size(18.dp), tint = c.onPrimaryContainer)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("应用更新", color = c.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
                    Text(
                        "UPDATE", fontFamily = RefHud, fontSize = 9.sp, lineHeight = 12.sp,
                        letterSpacing = 1.sp, color = c.primary
                    )
                }
                Text(
                    "v$currentVersion",
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    color = c.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(.7.dp).background(c.outlineVariant))
            Spacer(Modifier.height(12.dp))

            when (phase) {
                UpdatePhase.IDLE -> Text(
                    "从 GitHub Release 检查新版本，下载后由系统安装器覆盖安装。",
                    fontSize = 11.sp, lineHeight = 17.sp, color = c.onSurfaceVariant
                )

                UpdatePhase.CHECKING -> Row(verticalAlignment = Alignment.CenterVertically) {
                    PopBusySpinner(modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("正在检查更新…", fontSize = 11.sp, lineHeight = 17.sp, color = c.onSurfaceVariant)
                }

                UpdatePhase.UP_TO_DATE -> Row(verticalAlignment = Alignment.CenterVertically) {
                    PIcon(RefIcons.Check, null, Modifier.size(15.dp), tint = c.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(message, fontSize = 11.sp, lineHeight = 17.sp, color = c.onSurface)
                }

                UpdatePhase.AVAILABLE -> Column {
                    Text(
                        "发现新版本 ${info?.tag.orEmpty()}",
                        fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface
                    )
                    val meta = listOfNotNull(
                        info?.publishedAt?.takeIf { it.isNotBlank() }?.let { "发布于 $it" },
                        info?.apkSize?.takeIf { it > 0 }?.let { formatSize(it) }
                    ).joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(meta, fontSize = 10.sp, lineHeight = 15.sp, color = c.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    NotesEntry(c.primary) { showNotes = true }
                }

                UpdatePhase.DOWNLOADING -> Column {
                    Text(
                        "正在下载 ${info?.tag.orEmpty()}… ${(progress * 100).toInt()}%",
                        fontSize = 11.sp, lineHeight = 17.sp, color = c.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = c.primary,
                        trackColor = c.surfaceVariant
                    )
                }

                UpdatePhase.NEED_PERMISSION -> Column {
                    Text(
                        "安装包已下载完成",
                        fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "系统要求先允许「安装未知应用」。已为你打开设置页，允许后返回点下方按钮即可安装。",
                        fontSize = 11.sp, lineHeight = 17.sp, color = c.onSurfaceVariant
                    )
                }

                UpdatePhase.READY -> Row(verticalAlignment = Alignment.CenterVertically) {
                    PIcon(RefIcons.Check, null, Modifier.size(15.dp), tint = c.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "已交给系统安装器，按提示完成安装即可。",
                        fontSize = 11.sp, lineHeight = 17.sp, color = c.onSurface
                    )
                }

                UpdatePhase.FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
                    PIcon(RefIcons.Close, null, Modifier.size(15.dp), tint = c.error)
                    Spacer(Modifier.width(8.dp))
                    Text(message, fontSize = 11.sp, lineHeight = 17.sp, color = c.error)
                }
            }

            Spacer(Modifier.height(12.dp))

            val primaryLabel = when (phase) {
                UpdatePhase.CHECKING -> "检查中…"
                UpdatePhase.DOWNLOADING -> "下载中…"
                UpdatePhase.AVAILABLE -> "下载并安装"
                UpdatePhase.NEED_PERMISSION -> "已授权，去安装"
                UpdatePhase.READY -> "再次打开安装器"
                UpdatePhase.FAILED -> if (apk != null) "重试安装" else "重试"
                else -> "检查更新"
            }
            val primaryEnabled = phase != UpdatePhase.CHECKING && phase != UpdatePhase.DOWNLOADING
            Surface(
                onClick = { primaryAction() },
                enabled = primaryEnabled,
                shape = themedShape(PopRadius.field),
                color = if (primaryEnabled) c.primaryContainer else c.surfaceVariant,
                contentColor = if (primaryEnabled) c.onPrimaryContainer else c.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .semantics {
                        contentDescription = primaryLabel
                        liveRegion = LiveRegionMode.Polite
                    }
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(primaryLabel, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(6.dp))
            TextButton(
                onClick = { openReleasesPage(context) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
            ) {
                Text("在浏览器中查看全部版本", fontSize = 11.sp, color = c.onSurfaceVariant)
            }
        }
    }

    // 本次更新内容：应用自有弹窗样式（主题底色 + 墨线描边），不用系统白底弹窗
    if (showNotes) {
        val target = info
        Dialog(
            onDismissRequest = { showNotes = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                shape = themedShape(PopRadius.sheet),
                border = BorderStroke(2.dp, celInk()),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 26.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "本次更新内容",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showNotes = false }) { Text("关闭") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        target?.title.orEmpty().ifBlank { target?.tag.orEmpty() },
                        fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!target?.publishedAt.isNullOrBlank()) {
                        Text(
                            "发布于 ${target?.publishedAt}",
                            fontSize = 10.sp, lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            target?.notes.orEmpty().ifBlank { "该版本没有提供更新说明。" },
                            fontSize = 12.sp, lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        onClick = {
                            showNotes = false
                            if (phase == UpdatePhase.AVAILABLE) download()
                        },
                        shape = themedShape(PopRadius.field),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (phase == UpdatePhase.AVAILABLE) "下载并安装" else "关闭",
                                fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val UPDATE_PREFS = "imagine-update"
private const val DISMISSED_TAG = "dismissed_tag"

/**
 * 开场结束后静默检查一次。只有更新的、且没有被「稍后再说」压住的版本才弹窗。
 * 网络失败不提示，避免每次启动都报错。
 */
@Composable
internal fun UpdatePrompt(enabled: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updater = remember { AppUpdater(context) }
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var phase by remember { mutableStateOf(UpdatePhase.IDLE) }
    var apk by remember { mutableStateOf<File?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(enabled) {
        if (!enabled || info != null) return@LaunchedEffect
        val found = updater.checkForUpdate().getOrNull() ?: return@LaunchedEffect
        val dismissed = context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE).getString(DISMISSED_TAG, null)
        if (!shouldOfferUpdate(found.tag, updater.currentVersionName(), dismissed)) return@LaunchedEffect
        info = found
        phase = UpdatePhase.AVAILABLE
    }
    val target = info ?: return
    fun postpone() {
        context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE).edit().putString(DISMISSED_TAG, target.tag).apply()
        info = null
    }
    fun tryInstall(file: File) {
        if (!updater.canInstallPackages()) {
            phase = UpdatePhase.NEED_PERMISSION
            message = "需要先允许「安装未知应用」"
            openInstallPermission(context)
            return
        }
        updater.install(file)
            .onSuccess { phase = UpdatePhase.READY }
            .onFailure { phase = UpdatePhase.FAILED; message = it.message ?: "无法打开安装器" }
    }
    fun download() {
        updater.cachedApk(target)?.let { cached -> apk = cached; tryInstall(cached); return }
        phase = UpdatePhase.DOWNLOADING
        progress = 0f
        message = ""
        scope.launch {
            updater.download(target) { progress = it }
                .onSuccess { file -> apk = file; tryInstall(file) }
                .onFailure { phase = UpdatePhase.FAILED; message = it.message ?: "下载失败" }
        }
    }
    Dialog(
        onDismissRequest = { if (phase != UpdatePhase.DOWNLOADING) postpone() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = themedShape(PopRadius.sheet),
            border = BorderStroke(2.dp, celInk()),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 26.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("发现新版本", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(target.title.ifBlank { target.tag }, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                val meta = listOfNotNull(target.publishedAt.takeIf { it.isNotBlank() }?.let { "发布于 $it" }, target.apkSize.takeIf { it > 0 }?.let { formatSize(it) }).joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, fontSize = 10.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text(target.notes.ifBlank { "该版本没有提供更新说明。" }, fontSize = 12.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                if (phase == UpdatePhase.DOWNLOADING) {
                    Spacer(Modifier.height(10.dp))
                    Text("正在下载 ${(progress * 100).toInt()}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                }
                if (message.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(message, fontSize = 11.sp, lineHeight = 17.sp, color = if (phase == UpdatePhase.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                val label = when (phase) {
                    UpdatePhase.DOWNLOADING -> "下载中…"
                    UpdatePhase.NEED_PERMISSION -> "已授权，去安装"
                    UpdatePhase.READY -> "再次打开安装器"
                    UpdatePhase.FAILED -> if (apk != null) "重试安装" else "重试下载"
                    else -> "下载并安装"
                }
                Surface(
                    onClick = {
                        when (phase) {
                            UpdatePhase.NEED_PERMISSION, UpdatePhase.READY -> apk?.let { tryInstall(it) } ?: download()
                            UpdatePhase.FAILED -> if (apk != null) tryInstall(apk!!) else download()
                            UpdatePhase.DOWNLOADING -> Unit
                            else -> download()
                        }
                    },
                    enabled = phase != UpdatePhase.DOWNLOADING,
                    shape = themedShape(PopRadius.field),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text(label, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
                    }
                }
                TextButton(onClick = ::postpone, enabled = phase != UpdatePhase.DOWNLOADING, modifier = Modifier.fillMaxWidth()) { Text("稍后再说") }
            }
        }
    }
}

/** 「查看本次更新内容」入口：与卡片其他行保持同一套描边语言。 */
@Composable
private fun NotesEntry(accent: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = themedShape(PopRadius.chip),
        color = c.surfaceContainerLow,
        border = BorderStroke(.8.dp, c.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PIcon(RefIcons.Inspect, null, Modifier.size(14.dp), tint = accent)
            Spacer(Modifier.width(7.dp))
            Text(
                "查看本次更新内容", fontSize = 11.sp, lineHeight = 16.sp,
                color = c.onSurface, modifier = Modifier.weight(1f)
            )
            PIcon(RefIcons.Chevron, null, Modifier.size(13.dp), tint = c.onSurfaceVariant)
        }
    }
}

/** 打开本应用的「安装未知应用」授权页；失败则退回应用详情页，绝不抛异常。 */
private fun openInstallPermission(context: Context) {
    val appUri = Uri.parse("package:${context.packageName}")
    val direct = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, appUri)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(direct) }.isSuccess) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** 浏览器打开 Release 列表页。 */
private fun openReleasesPage(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(UpdateSource.RELEASES_PAGE))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}