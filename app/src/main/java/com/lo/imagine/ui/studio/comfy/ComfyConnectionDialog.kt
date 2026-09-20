package com.lo.imagine.ui.studio.comfy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lo.imagine.data.comfy.*
import com.lo.imagine.ui.PopTextField
import com.lo.imagine.ui.celInk
import com.lo.imagine.ui.theme.PopRadius
import com.lo.imagine.ui.theme.themedShape
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ComfyDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.background, shape = themedShape(PopRadius.sheet),
            border = BorderStroke(2.dp, celInk()), modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding().heightIn(max = 680.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            }
        }
    }
}

@Composable
fun ComfyConnectionDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val runtime = remember { ComfyRuntime.get(context) }
    val state by runtime.repository.state.collectAsStateWithLifecycle()
    ComfyDialog("ComfyUI 服务器", onDismiss) {
        if (!state.ready) {
            Text(state.error ?: "正在读取连接配置…", color = if (state.error == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
            if (state.error != null) TextButton(onClick = runtime.repository::reload) { Text("重新读取") }
        } else ComfyConnectionForm(runtime, state.connection, onDismiss)
    }
}

@Composable
private fun ComfyConnectionForm(runtime: ComfyRuntime, connection: ComfyConnection, onDismiss: () -> Unit) {
    var url by rememberSaveable { mutableStateOf(connection.baseUrl) }
    var key by remember { mutableStateOf(connection.bearerToken) }
    var minutes by rememberSaveable { mutableStateOf(connection.waitMinutes.toString()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun config(): ComfyConnection = ComfyConnection(baseUrl = comfyBaseUrl(url).toString(), bearerToken = key.trim(),
        waitMinutes = minutes.toIntOrNull()?.takeIf { it in 1..180 } ?: throw IllegalArgumentException("等待时间应为 1–180 分钟"))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("连接你的电脑或服务器，工作流在服务器上执行。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PopTextField(url, { url = it; message = null }, label = "服务器地址", placeholder = "http://电脑IP:8188", singleLine = true, enabled = !busy)
        Text("手机不能用电脑的 127.0.0.1。支持带路径前缀的反向代理。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PopTextField(key, { key = it; message = null }, label = "Bearer Key（可选）", singleLine = true, visualTransformation = PasswordVisualTransformation(), enabled = !busy)
        PopTextField(minutes, { minutes = it }, label = "最长等待（分钟）", singleLine = true, enabled = !busy)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                scope.launch {
                    busy = true; error = null; message = null
                    try { message = runtime.backend.inspect(config()) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message }
                    finally { busy = false }
                }
            }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (busy) "处理中…" else "测试连接") }
            Button(onClick = {
                scope.launch {
                    busy = true; error = null
                    try { runtime.repository.saveConnection(config()); onDismiss() }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message }
                    finally { busy = false }
                }
            }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("保存") }
        }
    }
}