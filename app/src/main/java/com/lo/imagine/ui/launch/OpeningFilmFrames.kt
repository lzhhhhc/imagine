package com.lo.imagine.ui.launch

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log
import com.lo.imagine.R
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pre-decodes the first [TOTAL_MS] of the film with the sequential batch decoder
 * (getFramesAtIndex), which avoids the per-frame seek cost of getFrameAtTime and runs at
 * a multiple of realtime on this device. Frames wait in a bounded queue for the UI ticker.
 */
internal class OpeningFilmFrames(context: Context) {
    private val retriever = MediaMetadataRetriever()
    private val queue = LinkedBlockingQueue<Bitmap>(QUEUE_LIMIT)
    private val running = AtomicBoolean(false)
    private val decodedIndex = AtomicInteger(0)
    private val shownIndex = AtomicInteger(0)
    private var worker: Thread? = null

    init {
        context.resources.openRawResourceFd(R.raw.opening_film).use { fd ->
            retriever.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
        }
    }

    fun start(onFinished: () -> Unit) {
        running.set(true)
        worker = Thread {
            val totalFrames = TOTAL_MS / 1000 * SOURCE_FPS
            var index = 0
            while (running.get() && index < totalFrames) {
                val batch = minOf(BATCH_FRAMES, totalFrames - index)
                val frames: List<Bitmap> = try {
                    if (Build.VERSION.SDK_INT >= 28) {
                        @Suppress("DEPRECATION")
                        retriever.getFramesAtIndex(index, batch)
                    } else {
                        (0 until batch).mapNotNull { offset ->
                            retriever.getFrameAtTime((index + offset) * 1_000_000L / SOURCE_FPS,
                                MediaMetadataRetriever.OPTION_CLOSEST)
                        }
                    }
                } catch (error: Throwable) {
                    Log.w(TAG, "batch decode at frame $index failed", error)
                    emptyList()
                }
                if (frames.isEmpty()) break
                for (frame in frames) {
                    if (!running.get()) { frame.recycle(); break }
                    while (running.get() && queue.size >= QUEUE_LIMIT) {
                        try { Thread.sleep(6) } catch (_: InterruptedException) { return@Thread }
                    }
                    if (!running.get()) { frame.recycle(); break }
                    queue.put(frame)
                    decodedIndex.incrementAndGet()
                    index++
                }
            }
            Log.i(TAG, "Predecode done: $index frames queued in total")
            onFinished()
        }.also { it.start() }
    }

    /** Pops the next decoded frame, waiting up to [timeoutMs]; null when none in time. */
    fun nextFrame(timeoutMs: Long): Bitmap? = try {
        val frame = queue.poll(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        if (frame != null) shownIndex.incrementAndGet()
        frame
    } catch (_: InterruptedException) { null }

    fun bufferedCount(): Int = queue.size
    fun shownCount(): Int = shownIndex.get()

    fun stop() {
        running.set(false)
        worker?.interrupt()
        queue.clear()
    }

    fun close() {
        stop()
        try { retriever.release() } catch (_: Throwable) {}
    }

    companion object {
        private const val TAG = "OpeningFilm"
        internal const val SOURCE_FPS = 24
        internal const val TOTAL_MS = 5_000
        private const val QUEUE_LIMIT = 12
        private const val BATCH_FRAMES = 8
    }
}
