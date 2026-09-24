package com.lo.imagine.ui.studio


import com.lo.imagine.ui.theme.PopRadius
import android.graphics.Bitmap
import com.lo.imagine.ui.ImageImportSource
import com.lo.imagine.ui.rememberImageImport
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lo.imagine.ui.PIcon
import com.lo.imagine.data.AppSettings
import com.lo.imagine.data.ImageRepository
import com.lo.imagine.ui.StudioState
import com.lo.imagine.ui.PopChevron
import com.lo.imagine.ui.PopImageAdd
import com.lo.imagine.ui.PopInspect
import com.lo.imagine.ui.PopBusySpinner
import com.lo.imagine.ui.PopRefresh
import com.lo.imagine.ui.PopTextField
import com.lo.imagine.ui.celInk
import com.lo.imagine.ui.theme.themedCorner
import com.lo.imagine.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 首页反推提示词弹窗：
 *  - 让用户从相册导入一张图片
 *  - 调 vision LLM 按反推模板输出提示词（标准模式为四段式自然语言描述；NAI 目标模型为标签流）
 *  - 一键写回 StudioState.prompt，开始创作
 */
@Composable
fun ReversePromptDialog(
    settings: AppSettings,
    repository: ImageRepository,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit,
    /** 反推时按哪个模型选模板：普通模式用绘图模型，NAI 页应传自己的 NAI 模型 */
    targetModel: String = settings.genModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberImageImport(ImageImportSource.fromId(settings.imageImportSource)) { uris ->
        val uri = uris.firstOrNull() ?: return@rememberImageImport
        scope.launch {
            bitmap = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    android.graphics.BitmapFactory.decodeStream(input)
                }
            }
            // 换图后清掉之前的报错
            error = null
        }
    }

    fun runReverse() {
        val src = bitmap
        if (src == null || loading) return
        loading = true
        error = null
        scope.launch {
            // 反推是 vision 请求：先把图压到 1280 长边再编码，避免大图把请求撑爆/超时
            val b64 = withContext(Dispatchers.Default) {
                val (_, bytes) = ImageUtils.prepareForEdit(src, maxEdge = 1280)
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            }
            repository.reversePrompt(settings, b64, targetModel.ifBlank { settings.genModel })
                .onSuccess { text ->
                    // 反推完成直接放进首页输入框并关闭弹窗，不再多点一次「使用此提示词」
                    loading = false
                    onApply(text)
                    onDismiss()
                }
                .onFailure { e ->
                    error = e.message ?: "反推失败"
                    loading = false
                }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = com.lo.imagine.ui.theme.themedShape(PopRadius.sheet),
            border = BorderStroke(2.dp, celInk()),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 28.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "图像反推提示词",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "导入一张图，让 AI 帮你写出一段扩散模型可用的英文 prompt。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))

                // 图片预览 / 选择区
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = !loading) {
                            picker.launch()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val src = bitmap
                    if (src != null) {
                        Image(
                            bitmap = src.asImageBitmap(),
                            contentDescription = "待反推图片",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().padding(4.dp)
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PIcon(
                                PopImageAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "点击选择图片",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "支持 JPG / PNG",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (bitmap != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                picker.launch()
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !loading
                        ) {
                            PIcon(PopRefresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(5.dp))
                            Text("换一张")
                        }
                        OutlinedButton(
                            onClick = { runReverse() },
                            modifier = Modifier.weight(1f),
                            enabled = !loading
                        ) {
                            if (loading) {
                                PopBusySpinner(modifier = Modifier.size(16.dp))
                            } else {
                                PIcon(PopInspect, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.size(5.dp))
                            Text(if (loading) "反推中" else "反推提示词")
                        }
                    }
                }

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (bitmap == null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "选一张图点「反推提示词」，结果会直接填进输入框。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
