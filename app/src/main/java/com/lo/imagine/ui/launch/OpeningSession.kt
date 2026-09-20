package com.lo.imagine.ui.launch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * The film plays once per COLD START of the process.
 *
 * Cold start: a fresh process/Activity (no saved state) arms the film once. Warm scenarios
 * never replay it:
 *  - returning from background with the process alive (ViewModel survives),
 *  - launcher re-taps delivered via onNewIntent once the opening already finished,
 *  - configuration changes (rotation, dark mode) — the ViewModel persists.
 * If the user leaves BEFORE the film ends, a launcher tap resumes the still-active session;
 * once it finished, all warm entries go straight home. Killing the app re-arms it.
 */
internal class OpeningSession : ViewModel() {
    var visible by mutableStateOf(true)
        private set
    var sequence by mutableIntStateOf(0)
        private set
    var leaving by mutableStateOf(false)
    var muted by mutableStateOf(true)
    var playbackPositionMs = 0

    /** True while this process has never completed its opening. */
    var finishedOnce by mutableStateOf(false)
        private set

    fun enterFromLauncher() {
        if (visible) return
        // After the opening finished once, warm launcher taps go straight home.
        if (finishedOnce) return
        sequence += 1
        leaving = false
        muted = true
        playbackPositionMs = 0
        visible = true
    }

    fun finish() {
        if (!visible) {
            // finish() after a warm return is a no-op bookkeeping guard.
            return
        }
        visible = false
        finishedOnce = true
    }
}
