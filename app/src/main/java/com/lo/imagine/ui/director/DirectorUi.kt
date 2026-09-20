package com.lo.imagine.ui.director

import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lo.imagine.data.DIRECTOR_STAGES
import com.lo.imagine.data.DirectorEngine
import com.lo.imagine.data.DirectorInterviewState
import com.lo.imagine.ui.ArkHeroArtwork
import com.lo.imagine.ui.PIcon
import com.lo.imagine.ui.PopFilm
import com.lo.imagine.ui.PopGallery
import com.lo.imagine.ui.theme.PopRadius
import com.lo.imagine.ui.theme.themedShape

/** Keep text editing in the page. IMEs must not replace it with a fullscreen/extract editor. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun DirectorInlineInput(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(interceptor = { request, next ->
        next.startInputMethod(object : PlatformTextInputMethodRequest {
            override fun createInputConnection(outAttributes: EditorInfo): android.view.inputmethod.InputConnection {
                val connection = request.createInputConnection(outAttributes)
                outAttributes.imeOptions = outAttributes.imeOptions or
                    EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
                return connection
            }
        })
    }, content = content)
}

@Composable
internal fun DirectorHeader(compact: Boolean, onStoryboard: () -> Unit, onMaterials: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth()) {
        if (!compact) ArkHeroArtwork("director", Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp)) {
            if (!compact) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("RHODES ISLAND  /  CREATIVE", fontSize = 10.sp, lineHeight = 14.sp,
                        fontWeight = FontWeight.SemiBold, letterSpacing = .6.sp, color = c.primary)
                    Text("03 / 05", fontSize = 10.sp, color = c.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("导演台", fontSize = if (compact) 20.sp else 26.sp,
                        lineHeight = if (compact) 26.sp else 32.sp, fontWeight = FontWeight.Bold, color = c.onBackground)
                    if (!compact) Text("一步一问，把想法变成镜头", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                }
                TextButton(onClick = onStoryboard, modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)) {
                    PIcon(PopFilm, null, Modifier.size(18.dp), tint = c.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("分镜", fontSize = 12.sp)
                }
                TextButton(onClick = onMaterials, modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)) {
                    PIcon(PopGallery, null, Modifier.size(18.dp), tint = c.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("素材", fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(if (compact) 4.dp else 12.dp))
            Box(Modifier.fillMaxWidth().height(.7.dp).background(c.outlineVariant))
        }
    }
}

@Composable
internal fun DirectorEngineSwitch(engine: DirectorEngine, enabled: Boolean, onSelect: (DirectorEngine) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(themedShape(PopRadius.field))
        .background(c.surfaceContainerLow).selectableGroup().padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        DirectorEngine.entries.forEach { item ->
            val selected = item == engine
            Box(Modifier.weight(1f)
                .heightIn(min = 48.dp).clip(themedShape(PopRadius.chip))
                .background(if (selected) c.primary else c.surfaceContainerLow)
                .selectable(selected, enabled = enabled, role = Role.Tab, onClick = { onSelect(item) })
                .semantics { contentDescription = "${item.fullName} 提示词工程" }
                .padding(horizontal = 4.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(item.brandLogo),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = (if (selected) c.onPrimary else c.onSurfaceVariant).copy(alpha = if (enabled) 1f else .55f)
                )
            }
        }
    }
}
@Composable
internal fun DirectorStepProgress(state: DirectorInterviewState, enabled: Boolean, onRevisit: (Int) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DIRECTOR_STAGES.forEachIndexed { index, stage ->
            val active = state.stageIndex == index
            val done = index < state.confirmedCount
            Column(Modifier.weight(1f).clip(themedShape(PopRadius.chip))
                .background(if (active) c.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                .selectable(active, enabled = enabled && done, role = Role.Tab,
                    onClick = { onRevisit(index) })
                .semantics { contentDescription = "第${index + 1}阶段，${stage.label}，${if (done) "已确认，点按修改" else if (active) "进行中" else "未开始"}" }
                .padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (done) "✓" else "0${index + 1}", fontSize = 10.sp, lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold, color = if (done || active) c.primary else c.onSurfaceVariant)
                Text(stage.label, fontSize = 12.sp, lineHeight = 18.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) c.onPrimaryContainer else c.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun DirectorPrimaryAction(text: String, enabled: Boolean = true, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, enabled = enabled, shape = themedShape(PopRadius.field),
        modifier = modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun DirectorSummaryCard(title: String, text: String, action: String? = null, onAction: () -> Unit = {}) {
    val c = MaterialTheme.colorScheme
    Surface(color = c.surfaceContainerLow, shape = themedShape(PopRadius.field),
        border = BorderStroke(.7.dp, c.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                    color = c.primary, modifier = Modifier.weight(1f))
                if (action != null) TextButton(onClick = onAction) { Text(action) }
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = c.onSurface)
        }
    }
}