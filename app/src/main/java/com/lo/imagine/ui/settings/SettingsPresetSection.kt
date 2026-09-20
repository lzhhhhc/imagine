package com.lo.imagine.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lo.imagine.ui.DropdownField
import com.lo.imagine.ui.PopTextField
import com.lo.imagine.ui.theme.PopRadius
import com.lo.imagine.ui.theme.themedShape

/**
 * 连接预设：一个下拉选预设，新建 / 重命名 / 删除全部**行内**完成——不再折叠区、不再弹窗。
 *
 * 依据用户反馈重做：
 *  - 内置「常用通道」列表删除（对用户是噪音：地址模型自己填就行，写死的通道还会过期）；
 *  - 选择预设改为下拉，点一下即生效；
 *  - 新建 = 点「＋新建…」**立即**开一条空白预设：不弹命名、不复制当前连接、绝不覆盖已有预设；
 *  - 重命名 = 行内输入行；删除 = 行内二次确认；全程零弹窗。
 */
@Composable
internal fun PresetDropdown(
    title: String,
    /** 下拉当前显示：本地预设名 / 平台名 / 「自定义连接」 */
    selectedLabel: String,
    /** 当前连接的副说明（端点 · 模型） */
    selectedDetail: String,
    /** 下拉选项（平台 + 本地预设） */
    options: List<String>,
    onPick: (Int) -> Unit,
    /** 追加在下拉末尾的项，例如「＋新建空白预设…」 */
    newLabel: String,
    /** 新建：点一下即**直接**创建一条空白预设（由调用方生成唯一名、置为当前、清空表单） */
    onCreateBlank: () -> Unit,
    /** 当前选中的本地预设名（决定是否显示 存回/重命名/删除） */
    activeName: String?,
    activeSaveLabel: String?,
    onSaveToActive: () -> Unit,
    onRenameActive: ((String) -> Unit)?,
    onDeleteActive: (() -> Unit)?,
    hint: String?
) {
    var renaming by remember { mutableStateOf(false) }
    var nameText by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    SettingsConsoleGroup(title = title, subtitle = selectedDetail) {
        DropdownField(
            selected = selectedLabel,
            options = options + newLabel,
            onSelect = { picked ->
                val index = options.indexOf(picked)
                // 先收起行内编辑态，避免上一次的半截输入残留
                renaming = false
                confirmDelete = false
                if (index >= 0) {
                    onPick(index)
                } else {
                    // 选中「＋新建…」：直接开一条空白预设——
                    // 不弹命名、不复制当前连接、不碰任何已有预设
                    onCreateBlank()
                }
            },
            menuHeight = 320
        )

        // ===== 行内重命名（新建不再需要输入名）=====
        if (renaming) {
            PopTextField(
                value = nameText,
                onValueChange = { nameText = it },
                label = "新名称",
                placeholder = "例如：我的中转",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        val name = nameText.trim()
                        if (name.isNotEmpty()) {
                            onRenameActive?.invoke(name)
                            renaming = false
                        }
                    },
                    enabled = nameText.isNotBlank(),
                    shape = themedShape(PopRadius.field),
                    modifier = Modifier.weight(1f)
                ) { Text("确认重命名") }
                TextButton(onClick = { renaming = false }, modifier = Modifier.weight(1f)) { Text("取消") }
            }
        }

        // ===== 选中本地预设时的行内操作：存回 / 重命名 / 删除 =====
        if (!renaming && !confirmDelete && activeSaveLabel != null) {
            OutlinedButton(
                onClick = onSaveToActive,
                shape = themedShape(PopRadius.field),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) { Text(activeSaveLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (onRenameActive != null) {
                    TextButton(
                        onClick = {
                            renaming = true
                            nameText = activeName.orEmpty()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("重命名") }
                }
                if (onDeleteActive != null) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) { Text("删除") }
                }
            }
        }

        // ===== 删除的行内二次确认（不可撤销操作不放行）=====
        if (confirmDelete) {
            Text(
                "删除「${activeName ?: "当前预设"}」？此操作不可撤销。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        confirmDelete = false
                        onDeleteActive?.invoke()
                    },
                    shape = themedShape(PopRadius.field),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) { Text("确认删除") }
                TextButton(onClick = { confirmDelete = false }, modifier = Modifier.weight(1f)) { Text("取消") }
            }
        }

        hint?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            )
        }
    }
}
