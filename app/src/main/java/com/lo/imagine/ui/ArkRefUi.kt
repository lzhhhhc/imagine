package com.lo.imagine.ui
import com.lo.imagine.ui.theme.RefHud
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lo.imagine.R
import com.lo.imagine.ui.theme.PopRadius
import com.lo.imagine.ui.theme.themedShape

/** 22dp alignment box with 1dp optical inset; rounded glyphs keep the original 58dp targets. */
@Composable
fun ArkTileButton(cn: String, en: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, icon: Int? = null, glyph: (@Composable () -> Unit)? = null,
    active: Boolean = false, enabled: Boolean = true) {
    val c = MaterialTheme.colorScheme
    val glyphColor = c.primary.copy(alpha = if (enabled) 1f else .42f)
    Surface(onClick = onClick, enabled = enabled, shape = themedShape(PopRadius.chip),
        color = if (active) c.primaryContainer else c.surface.copy(alpha = .62f),
        contentColor = c.onSurface.copy(alpha = if (enabled) 1f else .42f),
        border = BorderStroke(.8.dp, if (active) c.primary else c.outlineVariant),
        tonalElevation = 0.dp, modifier = modifier.heightIn(min = RefUiTokens.tileHeight)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CompositionLocalProvider(LocalContentColor provides glyphColor) {
                if (icon != null) PIcon(icon, null, Modifier.size(RefUiTokens.tileIcon).padding(1.dp)) else glyph?.invoke()
            }
            Spacer(Modifier.height(7.dp))
            Text(cn, fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Compact header capsule wearing the exact same skin as the mode switch
 *  beside it: translucent surface, fine outline, primary-tinted glyph. */
@Composable
fun ArkBlockAction(icon: Int, cn: String, en: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = CircleShape, color = c.surface.copy(alpha = .82f),
        border = BorderStroke(.7.dp, c.outlineVariant),
        modifier = modifier.heightIn(min = RefUiTokens.controlHeight).widthIn(max = 150.dp)) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PIcon(icon, null, Modifier.size(16.dp), tint = c.primary)
            Text(cn, fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false))
            PIcon(RefIcons.Chevron, null, Modifier.size(13.dp), tint = c.onSurfaceVariant)
        }
    }
}

/** Preset picker in the app's own "preset library" visual language:
 *  theme background, ink border, custom title row — not the stock alert style. */
@Composable
fun ArkPresetDialog(
    title: String,
    hint: String,
    emptyText: String,
    isEmpty: Boolean,
    onDismiss: () -> Unit,
    itemContent: @Composable () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = themedShape(PopRadius.sheet),
            border = BorderStroke(2.dp, celInk()),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 26.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isEmpty) {
                        Text(emptyText)
                    }
                    itemContent()
                }
            }
        }
    }
}

@Composable
fun ArkHeaderAction(icon: Int, label: String, onClick: () -> Unit) {
    ArkTileButton(label, "", onClick, Modifier.size(64.dp), icon = icon)
}

/** Shared home/edit action: reference-cut glass frame, geometric emblem and square arrow. */
@Composable
fun ArkGenerateBar(loading: Boolean, enabled: Boolean, label: String, sub: String,
    onClick: () -> Unit, modifier: Modifier = Modifier) {
    FrostedActionButton(loading, enabled, label, sub, onClick, modifier)
}
@Composable
fun ArkTabRow(tabs: List<Triple<String, String, String>>, selectedKey: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tabs.forEach { (cn, en, key) ->
            val active = key == selectedKey
            Surface(onClick = { onSelect(key) }, shape = themedShape(PopRadius.chip),
                color = if (active) c.primaryContainer else c.surface, contentColor = if (active) c.primary else c.onSurface,
                border = BorderStroke(.8.dp, if (active) c.primary else c.outlineVariant),
                modifier = Modifier.weight(1f).heightIn(min = RefUiTokens.controlHeight)) {
                Column(Modifier.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(cn, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(en, fontSize = 6.sp, lineHeight = 9.sp, letterSpacing = .15.em, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun ArkInkPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(shape = themedShape(PopRadius.card), color = c.surface.copy(alpha = .68f), contentColor = c.onSurface,
        tonalElevation = 0.dp, border = BorderStroke(.8.dp, c.outlineVariant), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), content = content)
    }
}

@Composable
fun ArkTranslateGlyph() = PIcon(RefIcons.Translate, null, Modifier.size(RefUiTokens.tileIcon).padding(1.dp))

@Composable
fun RefSectionHeading(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(modifier.semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(25.dp).background(c.primary, RoundedCornerShape(1.dp)))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontFamily = FontFamily.SansSerif, fontSize = 17.sp, lineHeight = 23.sp,
                fontWeight = FontWeight.Bold, color = c.onSurface, maxLines = 1)
            Text(subtitle, fontFamily = RefHud, fontSize = 8.sp, lineHeight = 12.sp,
                letterSpacing = .12.em, color = c.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** Keep the reference one-line heading on phones; large type gets its own action row. */
@Composable
fun RefPromptHeading(actions: @Composable RowScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 300.dp || LocalDensity.current.fontScale > 1.2f
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RefSectionHeading("描述你的画面", "PROMPT INPUT")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RefSectionHeading("描述你的画面", "PROMPT INPUT", Modifier.weight(1f))
                actions()
            }
        }
    }
}

@Composable
fun RefOutlineAction(icon: ImageVector, label: String, description: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = themedShape(PopRadius.chip), color = c.surface.copy(alpha = .65f),
        contentColor = c.onSurface, border = BorderStroke(.8.dp, c.outlineVariant),
        modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 58.dp, max = 84.dp)
            .semantics { contentDescription = description }) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            PIcon(icon, null, Modifier.size(18.dp), tint = c.primary)
            Text(label, fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 17.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
    }
}

@Composable
fun RefResolutionBadge(text: String, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = Color.Transparent, border = BorderStroke(.7.dp, c.primary.copy(alpha = .45f)), modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PIcon(RefIcons.Crop, null, Modifier.size(16.dp), tint = c.primary)
            Text(text, fontFamily = FontFamily.SansSerif, fontSize = 11.sp, lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold, color = c.primary, maxLines = 1)
        }
    }
}

@Composable
fun RefFieldLabel(title: String, english: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
        Text(english, fontFamily = RefHud, fontSize = 7.sp, lineHeight = 10.sp,
            letterSpacing = .10.em, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
    }
}

/** Art belongs to the scrollable hero, never a full-height Crop. */
@Composable
fun ArkPageBackdrop(route: String?, modifier: Modifier = Modifier) {
    if (route == "settings") {
        val dark = MaterialTheme.colorScheme.background.luminance() < .5f
        Image(painterResource(if (dark) R.drawable.settings_full_dark else R.drawable.settings_full_light),
            null, modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
    } else Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
}

@Composable
fun ArkHeroArtwork(route: String?, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val dark = c.background.luminance() < .5f
    Box(modifier.clip(androidx.compose.ui.graphics.RectangleShape)) {
        Image(painterResource(backdropRes(route, dark)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            alignment = when {
                route == "settings" && dark -> Alignment.BottomCenter
                route == "edit" && dark -> androidx.compose.ui.BiasAlignment(0f, -.35f)
                route == "director" -> androidx.compose.ui.BiasAlignment(0f, .5f)
                route == "studio" -> Alignment.TopCenter
                else -> Alignment.Center
            })
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            0f to c.background.copy(alpha = if (dark) .90f else .94f), .24f to c.background.copy(alpha = if (dark) .55f else .7f),
            .55f to c.background.copy(alpha = .04f), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to c.background.copy(alpha = .04f), .55f to Color.Transparent, .83f to c.background.copy(alpha = .62f), 1f to c.background)))
    }
}

internal fun backdropRes(route: String?, isDark: Boolean): Int = when (route) {
    "edit" -> if (isDark) R.drawable.bg_edit_dark else R.drawable.bg_edit_light
    "director" -> if (isDark) R.drawable.bg_director_dark else R.drawable.bg_director_light
    "settings" -> if (isDark) R.drawable.bg_settings_dark else R.drawable.bg_settings_light
    else -> if (isDark) R.drawable.bg_studio_dark else R.drawable.bg_studio_light
}
