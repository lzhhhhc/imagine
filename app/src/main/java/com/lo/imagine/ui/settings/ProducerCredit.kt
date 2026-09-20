package com.lo.imagine.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lo.imagine.ui.PIcon
import com.lo.imagine.ui.RefIcons
import com.lo.imagine.ui.theme.PopRadius
import com.lo.imagine.ui.theme.RefHud
import com.lo.imagine.ui.theme.themedShape
import kotlinx.coroutines.delay

/** User-supplied attribution. Preserve the requested spelling and the full ID as text. */
internal object ProducerCredit {
    const val NAME = "长乐未央"
    const val PLATFORM_LABEL = "Discode ID"
    const val ID = "1466791961003294804"
    val contactLine: String get() = "$PLATFORM_LABEL:$ID"
}

@Composable
internal fun ProducerCreditCard(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2500)
            copied = false
        }
    }
    Surface(
        shape = themedShape(PopRadius.card),
        color = c.surface,
        border = BorderStroke(.8.dp, c.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).background(c.primaryContainer, themedShape(PopRadius.chip)),
                    contentAlignment = Alignment.Center) {
                    Text("长", color = c.onPrimaryContainer, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("制作人", color = c.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
                    Text("PRODUCER", fontFamily = RefHud, fontSize = 9.sp, lineHeight = 12.sp,
                        letterSpacing = 1.sp, color = c.primary)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(ProducerCredit.NAME, fontSize = 21.sp, lineHeight = 29.sp,
                fontWeight = FontWeight.SemiBold, color = c.onSurface)
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(.7.dp).background(c.outlineVariant))
            Spacer(Modifier.height(12.dp))
            Text(ProducerCredit.PLATFORM_LABEL, color = c.onSurfaceVariant,
                fontFamily = RefHud, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(5.dp))
            SelectionContainer {
                // Never truncate a contact identifier; wrap at larger system font scales.
                Text(ProducerCredit.ID, color = c.onSurface, fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp, lineHeight = 19.sp, softWrap = true,
                    modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(14.dp))
            Surface(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(ProducerCredit.PLATFORM_LABEL, ProducerCredit.ID))
                    copied = true
                },
                shape = themedShape(PopRadius.field),
                color = c.primaryContainer,
                contentColor = c.onPrimaryContainer,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .semantics {
                        contentDescription = "复制制作人的 ${ProducerCredit.PLATFORM_LABEL}"
                        liveRegion = LiveRegionMode.Polite
                    }
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    PIcon(if (copied) RefIcons.Check else RefIcons.Copy, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (copied) "已复制 ID" else "复制 ID", fontSize = 12.sp,
                        lineHeight = 18.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}