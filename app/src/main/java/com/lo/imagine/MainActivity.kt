package com.lo.imagine

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.key
import androidx.lifecycle.ViewModelProvider
import com.lo.imagine.ui.launch.OpeningSession
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import com.lo.imagine.data.AppSettings
import com.lo.imagine.data.ImageRepository
import com.lo.imagine.data.SettingsRepository
import com.lo.imagine.data.TaskScheduler
import com.lo.imagine.data.ThemeMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.lo.imagine.ui.launch.OpeningIntro
import com.lo.imagine.ui.ImagineApp
import com.lo.imagine.ui.settings.UpdatePrompt
import com.lo.imagine.ui.theme.ImagineTheme
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    private val openingSession by lazy { ViewModelProvider(this)[OpeningSession::class.java] }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Launcher taps only REPLAY when the film never finished in this process
        // (i.e. the user left before it ended). Otherwise background returns go home.
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            openingSession.enterFromLauncher()
            android.util.Log.d("OpeningFilm", "Launcher entry session=${openingSession.sequence} finishedOnce=${openingSession.finishedOnce}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // A new activity/VM starts the short film even when Android restores a task.
        // ViewModel alone preserves completed/in-progress state across configuration changes.
        android.util.Log.d("OpeningFilm", "Activity created intro=${openingSession.visible} restored=${savedInstanceState != null}")

        // 系统开屏直接衔接视频首帧，片尾由 OpeningIntro 整体淡出到已准备好的首页。
        splashScreen.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .setDuration(160)
                .withEndAction { provider.remove() }
                .start()
        }

        setContent {
            val settingsRepository = remember { SettingsRepository(applicationContext) }
            val imageRepository = remember { ImageRepository(applicationContext) }
            val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
            ImagineTheme(themeMode = ThemeMode.fromId(settings.themeMode), moodKey = settings.moodKey) {
                val introVisible = openingSession.visible
                SideEffect {
                    if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
                }
                LaunchedEffect(Unit) {
                    val s = settingsRepository.settings.first()
                    TaskScheduler.configure(s.maxParallel)
                }
                // 权限弹窗延后到片尾，避免打断视频或盖住第一帧。
                LaunchedEffect(introVisible) {
                    if (!introVisible &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ActivityCompat.checkSelfPermission(
                            applicationContext, Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        ActivityCompat.requestPermissions(
                            this@MainActivity,
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            100
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(if (introVisible) Modifier.fillMaxSize().clearAndSetSemantics { } else Modifier.fillMaxSize()) {
                        ImagineApp(
                            settingsRepository = settingsRepository,
                            imageRepository = imageRepository
                        )
                    }
                    if (introVisible) key(openingSession.sequence) {
                        OpeningIntro(session = openingSession, onFinished = openingSession::finish)
                    }
                    UpdatePrompt(enabled = !introVisible)
                }
            }
        }
    }
}
