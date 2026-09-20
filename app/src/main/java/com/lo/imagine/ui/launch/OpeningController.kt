package com.lo.imagine.ui.launch

import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope

/**
 * Compose draws only the overlay controls. The film view hangs directly on the window,
 * above the whole Compose tree, so no composition layer or hardware layer can hide it.
 */
@Composable
internal fun OpeningIntro(session: OpeningSession, onFinished: () -> Unit) {
    val context = LocalContext.current as ComponentActivity
    val finishedCallback by rememberUpdatedState(onFinished)
    val leaving = session.leaving
    val muted = session.muted

    val filmView = remember {
        OpeningFilmView(context, session).apply {
            onCompleted = { finishedCallback() }
            onError = {
                Toast.makeText(context, "开场视频暂时无法播放，正在进入首页", Toast.LENGTH_SHORT).show()
                finishedCallback()
            }
            // Attach inside the activity's content view, above the Compose host. A
            // decor-root SurfaceView ended up BELOW the window surface on this device
            // (black screen); the content view composes it in the normal view order.
            (context.findViewById<ViewGroup>(android.R.id.content)).addView(
                this, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            Log.d(OpeningFilmView.TAG, "Attached to content view")
        }
    }
    DisposableEffect(filmView) {
        onDispose {
            filmView.release()
            (filmView.parent as? ViewGroup)?.removeView(filmView)
        }
    }
    DisposableEffect(context) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> filmView.pause()
                Lifecycle.Event.ON_RESUME -> filmView.resume()
                else -> Unit
            }
        }
        (context as LifecycleOwner).lifecycle.addObserver(observer)
        // Composition can attach after the activity is already RESUMED; the ON_RESUME event
        // would never arrive again and a cold start would sit on the poster forever.
        // onDispose must stay the last statement of this effect body.
        if ((context as LifecycleOwner).lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            Log.i(OpeningFilmView.TAG, "Initial resume sync applied")
            filmView.resume()
        }
        onDispose { (context as LifecycleOwner).lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(leaving) {
        if (leaving) filmView.fadeAway()
    }
    BackHandler { session.leaving = true }

    Box(
        Modifier.fillMaxSize()
            .semantics { paneTitle = "开场视频" }
            .pointerInput(Unit) {
                // Blank areas are part of the film; they must not reach the home beneath.
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
    ) {
        Box(
            Modifier.fillMaxWidth().height(136.dp).background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = .18f), Color.Transparent))
            )
        )
        Row(
            Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = { session.muted = !session.muted }, enabled = !leaving,
                color = MaterialTheme.colorScheme.surface.copy(alpha = .90f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(.7.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.size(48.dp).semantics {
                    contentDescription = if (muted) "开启开场声音" else "静音开场声音"
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                        null, Modifier.size(21.dp)
                    )
                }
            }
            Surface(
                onClick = { session.leaving = true }, enabled = !leaving,
                color = MaterialTheme.colorScheme.surface.copy(alpha = .90f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(.7.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "跳过开场，进入首页" }
            ) {
                Box(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text("跳过", fontSize = 14.sp)
                }
            }
        }
    }
}