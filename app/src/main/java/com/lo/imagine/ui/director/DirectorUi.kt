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
import androidx.compose.ui.graphics.luminance
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
import com.lo.imagine.ui.ArkGlassCard
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
internal fun DirectorHeader(shotCount: Int, onStoryboard: () -> Unit, onMaterials: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val dark = c.background.luminance() < .5f
    Box(
        Modifier.fillMaxWidth().heightIn(min = 188.dp, max = 218.dp)
            .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
    ) {
        ArkHeroArtwork("director", Modifier.matchParentSize())
        // 顶部只保留一层轻薄遮罩，保住素材构图，同时保证文字与按钮可读。
        Box(Modifier.matchParentSize().background(c.background.copy(alpha = if (dark) .18f else .30f)))
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp)) {
            // 标题行不写死高度：随字体缩放自然撑开，长字号也不会截断。
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("RHODES ISLAND", fontSize = 9.sp, lineHeight = 12.sp, letterSpacing = 1.3.sp,
                        fontWeight = FontWeight.Bold, color = c.onBackground.copy(alpha = .78f))
                    Text("导演台", fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold,
                        color = c.onBackground)
                }
                HeaderPillAction(onClick = onStoryboard, icon = PopFilm,
                    label = if (shotCount == 0) "分镜" else "分镜 $shotCount")
                HeaderPillAction(onClick = onMaterials, icon = PopGallery, label = "素材")
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.padding(bottom = 14.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("IDEAS TO VISUALS.", fontSize = 9.sp, lineHeight = 12.sp, letterSpacing = 1.4.sp,
                        color = c.onBackground.copy(alpha = .72f))
                    Spacer(Modifier.height(3.dp))
                    Text("先从一个画面开始。", fontSize = 15.sp, lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold, color = c.onBackground)
                }
                Text("03 / 05\nDIRECTOR", textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    fontSize = 9.sp, lineHeight = 13.sp, letterSpacing = 1.sp,
                    color = c.onBackground.copy(alpha = .68f))
            }
        }
    }
}

/** 右上角胶囊入口：沿用全应用已认可的描边 chip 皮肤（图标 + 短文字），不用突兀的方块大按钮。 */
@Composable
private fun HeaderPillAction(onClick: () -> Unit, icon: ImageVector, label: String) {
    val c = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = themedShape(PopRadius.chip),
        color = c.surface.copy(alpha = .78f), contentColor = c.onSurface,
        border = BorderStroke(.7.dp, c.outlineVariant),
        modifier = Modifier.heightIn(min = 44.dp)) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PIcon(icon, null, Modifier.size(18.dp), tint = c.primary)
            Text(label, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
internal fun DirectorEngineSwitch(engine: DirectorEngine, enabled: Boolean, onSelect: (DirectorEngine) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(themedShape(PopRadius.field))
        .background(c.surfaceContainer.copy(alpha = .94f)).selectableGroup().padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        DirectorEngine.entries.forEach { item ->
            val selected = item == engine
            Box(Modifier.weight(1f)
                .heightIn(min = 48.dp).clip(themedShape(PopRadius.chip))
                .background(if (selected) c.primary else c.surface.copy(alpha = .55f))
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
    ArkGlassCard(shape = themedShape(PopRadius.field), modifier = Modifier.fillMaxWidth()) {
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