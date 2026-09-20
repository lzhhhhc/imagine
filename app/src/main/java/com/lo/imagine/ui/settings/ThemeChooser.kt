package com.lo.imagine.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lo.imagine.R
import com.lo.imagine.data.ThemeMode
import com.lo.imagine.data.UiMood
import com.lo.imagine.ui.*
import com.lo.imagine.ui.theme.PopRadius
import com.lo.imagine.ui.theme.themedShape

@Composable
fun ThemeChooser(selectedId: String, onSelect: (String) -> Unit) {
    Column {
        AppearanceLabel("界面主题", "选择你喜欢的界面风格")
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
            val stacked = maxWidth < 205.dp || LocalDensity.current.fontScale > 1.4f
            if (stacked) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeMode.entries.forEach { mode ->
                    ThemePreviewCard(mode, mode == ThemeMode.fromId(selectedId), { onSelect(mode.id) }, Modifier.fillMaxWidth())
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeMode.entries.forEach { mode ->
                    ThemePreviewCard(mode, mode == ThemeMode.fromId(selectedId), { onSelect(mode.id) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(mode: ThemeMode, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val night = mode == ThemeMode.ARKNIGHTS_DARK
    val paper = if (night) Color(0xFF1D242A) else Color(0xFFFCFEFF)
    val ink = if (night) Color(0xFFF1F5F7) else Color(0xFF222E38)
    val muted = if (night) Color(0xFFB2BEC7) else Color(0xFF526473)
    val shape = themedShape(PopRadius.card)
    Column(modifier.shadow(if (selected) 5.dp else 2.dp, shape).clip(shape).background(paper)
        .selectable(selected, role = Role.RadioButton, onClick = onClick)
        .border(if (selected) 1.2.dp else .6.dp, if (selected) c.primary else muted.copy(alpha = .25f), shape)) {
        Box(Modifier.fillMaxWidth().height(66.dp)) {
            Image(painterResource(if (night) R.drawable.ark_theme_dark else R.drawable.ark_theme_light), null,
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (selected) SelectionCheck(Modifier.align(Alignment.TopEnd).padding(5.dp))
        }
        Row(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon(if (night) Icons.Outlined.DarkMode else Icons.Outlined.LightMode, null, tint = muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(mode.label, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, color = ink)
                Text(mode.description, fontSize = 8.sp, lineHeight = 12.sp, color = muted)
            }
        }
    }
}

internal fun MoodGlyph(mood: UiMood): ImageVector = when (mood) {
    UiMood.COOL_WHITE -> Icons.Outlined.Hexagon
    UiMood.DARK_TACTIC -> Icons.Outlined.ChangeHistory
    UiMood.SOFT_ILLUST -> Icons.Outlined.FilterVintage
}

@Composable
internal fun MoodChooser(selectedKey: String, onSelect: (String) -> Unit) {
    Column {
        AppearanceLabel("界面气质", "调整界面表现风格")
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
            val stacked = maxWidth < 205.dp || LocalDensity.current.fontScale > 1.4f
            if (stacked) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UiMood.entries.forEach { mood ->
                    MoodCard(mood, mood == UiMood.fromId(selectedKey), { onSelect(mood.id) }, Modifier.fillMaxWidth(), true)
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UiMood.entries.forEach { mood ->
                    MoodCard(mood, mood == UiMood.fromId(selectedKey), { onSelect(mood.id) }, Modifier.weight(1f), false)
                }
            }
        }
    }
}

@Composable
private fun MoodCard(mood: UiMood, selected: Boolean, onClick: () -> Unit, modifier: Modifier, horizontal: Boolean) {
    val c = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (selected) c.primaryContainer else c.surface.copy(alpha = .55f), label = "moodFill")
    val shape = themedShape(PopRadius.chip)
    Box(modifier.clip(shape).background(fill).border(if (selected) 1.dp else .6.dp,
        if (selected) c.primary else c.outlineVariant, shape)
        .selectable(selected, role = Role.RadioButton, onClick = onClick)) {
        if (horizontal) Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon(MoodGlyph(mood), null, tint = c.onSurface, modifier = Modifier.size(25.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(mood.label, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, color = c.onSurface)
                Text(mood.description, fontSize = 10.sp, lineHeight = 15.sp, color = c.onSurfaceVariant)
            }
        } else Column(Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PIcon(MoodGlyph(mood), null, tint = c.onSurface, modifier = Modifier.size(25.dp))
            Spacer(Modifier.height(10.dp))
            Text(mood.label, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = c.onSurface, textAlign = TextAlign.Center)
            Text(mood.description, fontSize = 9.sp, lineHeight = 14.sp, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (selected) SelectionCheck(Modifier.align(Alignment.TopEnd).padding(4.dp), small = true)
    }
}

@Composable
private fun AppearanceLabel(title: String, detail: String) {
    val c = MaterialTheme.colorScheme
    Text(title, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, color = c.onSurface,
        modifier = Modifier.semantics { heading() })
    Text(detail, fontSize = 10.sp, lineHeight = 15.sp, color = c.onSurfaceVariant)
}

@Composable
private fun SelectionCheck(modifier: Modifier, small: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Box(modifier.size(if (small) 12.dp else 16.dp).background(c.primary, CircleShape), contentAlignment = Alignment.Center) {
        PIcon(PopCheck, null, tint = c.onPrimary, modifier = Modifier.size(if (small) 8.dp else 11.dp))
    }
}