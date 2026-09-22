package com.lo.imagine.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lo.imagine.R

object StudioMode {
    const val NORMAL = "studio"
    const val NAI = "nai"
    const val COMFY = "comfy"
    val options = listOf("标准" to NORMAL, "NAI" to NAI, "ComfyUI" to COMFY)
    fun isStudio(route: String?): Boolean = options.any { it.second == route }
    fun dockRoute(route: String?): String? = if (isStudio(route)) NORMAL else route
}

private data class ModeMark(val id: String, val icon: Int, val label: String)

@Composable
fun StudioModeSwitch(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val modes = listOf(
        ModeMark(StudioMode.NORMAL, R.drawable.ic_brand_gpt, "标准"),
        ModeMark(StudioMode.NAI, R.drawable.ic_brand_nai, "NAI"),
        ModeMark(StudioMode.COMFY, R.drawable.ic_brand_comfy, "ComfyUI")
    )
    Surface(
        shape = CircleShape,
        color = c.surface.copy(alpha = .82f),
        border = BorderStroke(.7.dp, c.outlineVariant),
        modifier = modifier
    ) {
        Row(
            Modifier.padding(3.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            modes.forEach { mode ->
                val active = selected == mode.id
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (active) c.primaryContainer else Color.Transparent)
                        .selectable(selected = active, role = Role.Tab, onClick = { onSelect(mode.id) }),
                    contentAlignment = Alignment.Center
                ) {
                    PIcon(
                        mode.icon,
                        mode.label,
                        Modifier.size(18.dp),
                        tint = if (active) c.onPrimaryContainer else c.onSurfaceVariant
                    )
                }
            }
        }
    }
}
