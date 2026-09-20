package com.lo.imagine.ui.settings
import androidx.compose.foundation.border
import com.lo.imagine.ui.theme.PopRadius
import com.lo.imagine.ui.theme.themedShape

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lo.imagine.ui.*
import com.lo.imagine.ui.theme.LocalUiMood
import com.lo.imagine.data.UiMood

internal enum class SettingsSection(val title: String, val code: String, val index: Int) {
    APPEARANCE("外观", "APPEARANCE", 1),
    OUTPUT("生成", "OUTPUT", 2),
    CHANNELS("连接", "CHANNELS", 3),
    ABOUT("关于", "ABOUT", 4)
}

/** Keep the last section selected at the bottom, even when it is shorter than the viewport. */
internal fun settingsSectionAt(firstIndex: Int, atBottom: Boolean): SettingsSection =
    if (atBottom) SettingsSection.ABOUT
    else SettingsSection.entries.lastOrNull { it.index <= firstIndex } ?: SettingsSection.APPEARANCE

internal object SettingsPosterInk {
    val backdrop = Color(0xFF1B2026)
    val white = Color(0xFFF1F4F7)
    val muted = Color(0xFFB7C1CA)
    val blue = Color(0xFF6BBBE8)
}

/** Independent saved-state tabs; full-page art belongs to the app backdrop. */
@Composable
internal fun SettingsPoster(
    appearance: @Composable () -> Unit,
    output: @Composable () -> Unit,
    channels: @Composable () -> Unit,
    about: @Composable () -> Unit
) {
    var active by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(SettingsSection.APPEARANCE) }
    val holder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val c = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val short = maxHeight < 420.dp
        val headerSpace = if (short) 8.dp else (maxHeight * .20f).coerceAtMost(170.dp)
        val railWidth = if (maxWidth < 360.dp) 64.dp else 96.dp
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Spacer(Modifier.height(headerSpace))
            Text("设置  /  SETTINGS", color = c.onSurface, fontSize = 18.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            Row(Modifier.weight(1f).padding(bottom = 12.dp)) {
                Column(Modifier.width(railWidth).selectableGroup()) {
                    SettingsSection.entries.forEach { section ->
                        SettingsRailItem(section, section == active, railWidth >= 84.dp, short) { active = section }
                    }
                }
                holder.SaveableStateProvider(active.name) {
                    val scroll = androidx.compose.foundation.rememberScrollState()
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(scroll)) {
                        PosterSection(active, 0.dp, first = true, last = true) {
                            when (active) {
                                SettingsSection.APPEARANCE -> appearance()
                                SettingsSection.OUTPUT -> output()
                                SettingsSection.CHANNELS -> channels()
                                SettingsSection.ABOUT -> about()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRailItem(section: SettingsSection, selected: Boolean, wide: Boolean, compact: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val ink by animateColorAsState(if (selected) c.primary else c.onSurface, label = "railInk")
    val icon = when (section) {
        SettingsSection.APPEARANCE -> RefIcons.Palette
        SettingsSection.OUTPUT -> RefIcons.Frame
        SettingsSection.CHANNELS -> PopPlug
        SettingsSection.ABOUT -> PopInfo
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
        .heightIn(min = if (compact) 48.dp else if (wide) 56.dp else 64.dp).clip(themedShape(PopRadius.chip))
        .background(if (selected) Brush.horizontalGradient(listOf(c.surface, c.surface.copy(alpha = .9f)))
            else Brush.horizontalGradient(listOf(c.surface.copy(alpha = .78f), c.surface.copy(alpha = .78f))))
        .selectable(selected, role = Role.Tab, onClick = onClick)) {
        if (selected) Box(Modifier.matchParentSize().wrapContentWidth(Alignment.Start).width(3.dp).background(c.primary))
        if (compact && !wide) {
            Box(Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = section.title }, contentAlignment = Alignment.Center) {
                PIcon(icon, null, tint = ink, modifier = Modifier.size(20.dp))
            }
        } else if (wide) Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon(icon, null, tint = ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(section.title, color = ink, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
                if (!compact) Text(section.code, color = ink.copy(alpha = .85f), fontSize = 6.sp, lineHeight = 9.sp)
            }
        } else Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PIcon(icon, null, tint = ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(section.title, color = ink, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PosterSection(section: SettingsSection, railWidth: androidx.compose.ui.unit.Dp,
    first: Boolean = false, last: Boolean = false, content: @Composable () -> Unit) {
    val c = MaterialTheme.colorScheme
    val dark = c.background.luminance() < .5f
    val shape = themedShape(PopRadius.sheet)
    val panelColors = if (dark) c else c.copy(onSurfaceVariant =
        if (LocalUiMood.current == UiMood.SOFT_ILLUST) Color(0xFF6F5966) else Color(0xFF52616F))
    val glass = c.surfaceContainer.copy(alpha = .97f)
    val edge = if (dark) Color.White.copy(alpha = .16f) else Color.White.copy(alpha = .65f)
    MaterialTheme(colorScheme = panelColors) {
    Column(Modifier.fillMaxWidth().padding(start = railWidth + 4.dp, end = 8.dp)
        .clip(shape).background(glass).border(.7.dp, edge, shape).padding(horizontal = 16.dp)) {
        if (!first) SettingsRule()
        Spacer(Modifier.height(16.dp))
        SettingsSectionHeading(section)
        Spacer(Modifier.height(18.dp))
        content()
        Spacer(Modifier.height(if (last) 24.dp else 16.dp))
    }
    }
}

@Composable
private fun SettingsSectionHeading(section: SettingsSection) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(16.dp).background(c.primary))
        Spacer(Modifier.width(6.dp))
        Text(section.title, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = c.onSurface)
        Spacer(Modifier.width(7.dp))
        Text("// ${section.code}", modifier = Modifier.weight(1f), fontSize = 9.sp, lineHeight = 13.sp, letterSpacing = .5.sp,
            fontWeight = FontWeight.Bold, color = c.onSurfaceVariant)
    }
}

@Composable
internal fun SettingsPosterFooter() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("RHODES ISLAND", fontSize = 7.sp, lineHeight = 10.sp, letterSpacing = .9.sp, color = SettingsPosterInk.muted)
        Spacer(Modifier.width(22.dp))
        Box(Modifier.weight(1f).height(.6.dp).background(SettingsPosterInk.muted.copy(alpha = .25f)))
        Spacer(Modifier.width(22.dp))
        Text("ARTIFICIAL INTELLIGENCE\nFOR A BRIGHTER TOMORROW  +", fontSize = 5.sp, lineHeight = 7.sp,
            textAlign = TextAlign.End, color = SettingsPosterInk.muted)
    }
}
