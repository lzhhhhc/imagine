package com.lo.imagine.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lo.imagine.ui.*
import com.lo.imagine.ui.theme.PopRadius
import com.lo.imagine.ui.theme.themedShape

@Composable
private fun RowIcon(icon: ImageVector) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.size(32.dp).background(c.surface, themedShape(PopRadius.chip)), contentAlignment = Alignment.Center) {
        PIcon(icon, null, tint = c.onSurface, modifier = Modifier.size(20.dp))
    }
}

/** One switch semantic and one hit target for the whole row, including its label. */
@Composable
internal fun SettingsToggleRow(icon: ImageVector, title: String, description: String,
    checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = themedShape(PopRadius.card)
    Row(Modifier.fillMaxWidth().clip(shape).background(c.surface.copy(alpha = .68f))
        .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
        .heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        RowIcon(icon)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, color = c.onSurface)
            Text(description, fontSize = 10.sp, lineHeight = 15.sp, color = c.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        SettingsSwitchVisual(checked)
    }
}

@Composable
private fun SettingsSwitchVisual(checked: Boolean) {
    val c = MaterialTheme.colorScheme
    val thumb by animateDpAsState(if (checked) 23.dp else 2.dp, tween(160), label = "settingsSwitch")
    Box(Modifier.size(44.dp, 24.dp).clip(CircleShape).background(if (checked) c.primary else c.outline)
        .clearAndSetSemantics { }) {
        Text(if (checked) "ON" else "OFF", color = if (checked) c.onPrimary else c.surface,
            fontSize = 6.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(if (checked) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 4.dp))
        Box(Modifier.align(Alignment.CenterStart).offset(x = thumb).size(19.dp).background(Color.White, CircleShape))
    }
}

@Composable
internal fun SettingsConnectionRow(icon: ImageVector, title: String, subtitle: String,
    status: String, ready: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = themedShape(PopRadius.card), color = c.surface.copy(alpha = .68f),
        modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.padding(8.dp)) {
            val compact = maxWidth < 245.dp || LocalDensity.current.fontScale > 1.2f
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                RowIcon(icon)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    if (LocalDensity.current.fontScale > 1.4f) {
                        Text(title, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = c.onSurface)
                        ConnectionStatus(status, ready)
                    } else if (compact) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = c.onSurface, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(4.dp))
                        ConnectionStatus(status, ready)
                    } else Text(title, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = c.onSurface)
                    Text(subtitle, fontSize = 10.sp, lineHeight = 15.sp, color = c.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (!compact) {
                    Spacer(Modifier.width(8.dp))
                    ConnectionStatus(status, ready)
                }
                Spacer(Modifier.width(4.dp))
                PIcon(PopChevron, null, tint = c.onSurfaceVariant, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun ConnectionStatus(status: String, ready: Boolean) {
    val c = MaterialTheme.colorScheme
    val ink = if (ready) c.primary else c.onSurfaceVariant
    Row(Modifier.border(.7.dp, ink.copy(alpha = .7f), themedShape(PopRadius.chip))
        .padding(horizontal = 5.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(2.dp).background(ink))
        Spacer(Modifier.width(3.dp))
        Text(status, fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, color = ink)
    }
}

@Composable
internal fun SettingsActionRow(icon: ImageVector, title: String, subtitle: String,
    status: String, accent: Boolean, onClick: () -> Unit) {
    SettingsConnectionRow(icon, title, subtitle, status, accent, onClick)
}

@Composable
internal fun SettingsRule() {
    Box(Modifier.fillMaxWidth().height(.7.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .3f)))
}
