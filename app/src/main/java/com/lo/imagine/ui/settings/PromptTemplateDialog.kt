package com.lo.imagine.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lo.imagine.data.AppSettings
import com.lo.imagine.data.PromptTemplates
import com.lo.imagine.ui.PopAlertDialog
import com.lo.imagine.ui.PopTextField

/**
 * 反推 / 润色的系统提示词模板。
 *
 * 刻意精简：两段文字、两个输入框、每框两个文字动作，没有图标、徽章堆叠或额外说明块。
 * 留空即用内置默认（占位符直接显示内置全文，方便对照）。只在点「保存」时落盘一次。
 */
@Composable
fun PromptTemplateDialog(
    settings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onClose: () -> Unit
) {
    val targetModel = settings.genModel
    val defaultReverse = remember(targetModel) { PromptTemplates.defaultReversePromptText(targetModel) }
    val defaultPolish = remember(targetModel) { PromptTemplates.defaultPolishPromptText(targetModel) }
    // 内置反推模板已长达数千字，占位符只做开头预览，完整内容用「填入默认」查看，避免撑爆输入框
    val reverseHint = remember(defaultReverse) { templatePreview(defaultReverse) }
    val polishHint = remember(defaultPolish) { templatePreview(defaultPolish) }
    var reverse by remember { mutableStateOf(settings.reversePromptTemplate) }
    var polish by remember { mutableStateOf(settings.polishPromptTemplate) }

    PopAlertDialog(
        title = "提示词模板",
        onDismissRequest = onClose,
        confirmLabel = "保存",
        onConfirm = {
            onSave(settings.copy(reversePromptTemplate = reverse.trim(), polishPromptTemplate = polish.trim()))
            onClose()
        },
        dismissLabel = "取消",
        onDismiss = onClose,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Field(
                    label = "反推",
                    custom = reverse.isNotBlank(),
                    value = reverse,
                    onValueChange = { reverse = it },
                    placeholder = reverseHint,
                    onFillDefault = { reverse = defaultReverse },
                    onClear = { reverse = "" }
                )
                Field(
                    label = "润色",
                    custom = polish.isNotBlank(),
                    value = polish,
                    onValueChange = { polish = it },
                    placeholder = polishHint,
                    onFillDefault = { polish = defaultPolish },
                    onClear = { polish = "" }
                )
                Text(
                    "留空 = 用内置默认，填入即整段覆盖。目标模型：${targetModel.ifBlank { "未选" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

/** 占位符预览：内置模板已长达数千字，只展示开头并注明总字数。 */
private fun templatePreview(text: String, limit: Int = 120): String {
    val flat = text.replace("\n", " ").trim()
    return if (flat.length <= limit) flat
    else "内置默认（共 ${text.length} 字）：${flat.take(limit).trimEnd()}…"
}

@Composable
private fun Field(
    label: String,
    custom: Boolean,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onFillDefault: () -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(8.dp))
            Text(
                if (custom) "已自定义 ${value.length} 字" else "内置",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onFillDefault) { Text("填入默认", style = MaterialTheme.typography.labelSmall) }
            TextButton(onClick = onClear) { Text("清空", style = MaterialTheme.typography.labelSmall) }
        }
        PopTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            minLines = 4,
            maxLines = 10,
            modifier = Modifier.fillMaxWidth()
        )
    }
}