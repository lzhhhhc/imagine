package com.lo.imagine.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object StudioMode {
    const val NORMAL = "studio"
    const val NAI = "nai"
}

@Composable
fun StudioModeSwitch(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = c.surface.copy(alpha = .82f),
        border = BorderStroke(.7.dp, c.outlineVariant), modifier = modifier) {
        Row(Modifier.padding(2.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf("标准" to StudioMode.NORMAL, "NAI" to StudioMode.NAI).forEach { (label, id) ->
                val active = selected == id
                Row(Modifier.widthIn(min = 60.dp).heightIn(min = 48.dp).clip(CircleShape)
                    .background(if (active) c.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(id) })
                    .padding(horizontal = 11.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    if (id == StudioMode.NAI) {
                        PIcon(RefIcons.Nai, null, Modifier.size(16.dp), tint = c.primary)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(label, fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (active) c.onPrimaryContainer else c.onSurfaceVariant)
                }
            }
        }
    }
}
