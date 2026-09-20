package com.lo.imagine.ui.launch

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import com.lo.imagine.R
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Plays the pre-decoded 5s frame queue at the source 24fps, paced by Choreographer frames.
 */
internal class OpeningFilmView(context: Context, private val session: OpeningSession) : FrameLayout(context) {
    var onCompleted: () -> Unit = {}
    var onError: () -> Unit = {}
    private val film = FrameView(context)
    private val poster = PosterView(context)
    private val frames = OpeningFilmFrames(context)
    private val main = Handler(Looper.getMainLooper())
    private var active = false
    private var finished = false
    private var released = false
    private var fadeStarted = false
    private var started = false
    private var shownFrames = 0
    private var playStartTimeNs = 0L

    private val choreographerCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (released || finished || !active) return
            Choreographer.getInstance().postFrameCallback(this)
            // Present exactly one source frame per 1/24s of playback time.
            val elapsedMs = (frameTimeNanos - playStartTimeNs) / 1_000_000L
            val targetFrame = (elapsedMs * OpeningFilmFrames.SOURCE_FPS / 1000L).toInt()
            while (shownFrames < TOTAL_FRAMES) {
                if (shownFrames >= targetFrame) return
                val frame = frames.nextFrame(0) ?: return
                shownFrames++
                film.show(frame)
                if (shownFrames >= TOTAL_FRAMES) {
                    Log.i(TAG, "Last frame ($TOTAL_FRAMES) presented; ending now")
                    fadeAway()
                    return
                }
            }
        }
    }

    init {
        poster.visibility = VISIBLE
        addView(film, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(poster, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        frames.start { Log.i(TAG, "Predecode finished: ${frames.shownCount() + frames.bufferedCount()} frames total") }
        Log.i(TAG, "FilmView ready (batch predecode, 24fps Choreographer pacing)")
    }

    fun fadeAway() {
        if (fadeStarted || released) return
        fadeStarted = true
        finished = true
        Choreographer.getInstance().removeFrameCallback(choreographerCallback)
        keepScreenOn = false
        Log.i(TAG, "Fade out after $shownFrames frames")
        val animators = buildList {
            add(ObjectAnimator.ofFloat(film, View.ALPHA, 0f).setDuration(OPENING_FADE_MS.toLong()))
            if (poster.visibility == VISIBLE) {
                add(ObjectAnimator.ofFloat(poster, View.ALPHA, 0f).setDuration(OPENING_FADE_MS.toLong()))
            }
        }
        AnimatorSet().apply {
            playTogether(animators)
            interpolator = DecelerateInterpolator(1.4f)
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    visibility = GONE
                    if (!cancelled) onCompleted()
                }
            })
            start()
        }
    }

    fun pause() {
        if (released || finished) return
        active = false
        Choreographer.getInstance().removeFrameCallback(choreographerCallback)
        keepScreenOn = false
        Log.i(TAG, "Paused after $shownFrames frames")
    }

    fun resume() {
        if (released || finished) return
        active = true
        keepScreenOn = true
        if (!started) {
            started = true
            playStartTimeNs = System.nanoTime()
            Choreographer.getInstance().postFrameCallback(choreographerCallback)
        }
        Log.i(TAG, "Resume, showing $shownFrames frames so far")
    }

    fun release() {
        if (released) return
        released = true
        Choreographer.getInstance().removeFrameCallback(choreographerCallback)
        frames.close()
        film.clearFrame()
        onCompleted = {}
        onError = {}
    }

    private inner class FrameView(context: Context) : View(context) {
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private var frame: Bitmap? = null

        fun show(newFrame: Bitmap) {
            frame = newFrame
            invalidate()
            if (poster.visibility == VISIBLE) {
                Log.i(TAG, "First frame drawn; revealing film")
                poster.animate().alpha(0f).setDuration(POSTER_FADE_MS)
                    .withEndAction { poster.visibility = GONE }.start()
            }
        }

        fun clearFrame() {
            frame = null
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val source = frame ?: return
            canvas.drawColor(Color.BLACK)
            val (drawWidth, drawHeight) = filmSurfaceSize(width, height, source.width.toFloat(), source.height.toFloat())
            canvas.drawBitmap(source, null,
                Rect((width - drawWidth) / 2, (height - drawHeight) / 2, (width + drawWidth) / 2, (height + drawHeight) / 2),
                paint)
        }
    }

    private inner class PosterView(context: Context) : View(context) {
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private var bitmap: Bitmap? = null

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

        override fun onDraw(canvas: Canvas) {
            val loaded = bitmap ?: BitmapFactory.decodeResource(resources, R.drawable.launch_bg)?.also { bitmap = it }
            canvas.drawColor(Color.BLACK)
            val source = loaded ?: return
            val (drawWidth, drawHeight) = filmSurfaceSize(width, height, source.width.toFloat(), source.height.toFloat())
            canvas.drawBitmap(source, null,
                Rect((width - drawWidth) / 2, (height - drawHeight) / 2, (width + drawWidth) / 2, (height + drawHeight) / 2),
                paint)
        }
    }

    companion object {
        internal const val TAG = "OpeningFilm"
        internal const val OPENING_FADE_MS = 450
        internal const val POSTER_FADE_MS = 120L
        internal const val TOTAL_FRAMES = OpeningFilmFrames.TOTAL_MS / 1000 * OpeningFilmFrames.SOURCE_FPS
    }
}

/** Cover-fit output size for a viewport, keeping source pixels square. */
internal fun filmSurfaceSize(width: Int, height: Int, sourceWidth: Float, sourceHeight: Float): Pair<Int, Int> {
    require(width > 0 && height > 0 && sourceWidth > 0 && sourceHeight > 0)
    val scale = max(width / sourceWidth, height / sourceHeight)
    return (sourceWidth * scale).toInt().coerceAtLeast(width) to (sourceHeight * scale).toInt().coerceAtLeast(height)
}
