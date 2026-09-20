package com.lo.imagine.ui.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 原生遮罩画布。
 *
 * 手绘只落在最长边不超过 1500px 的小位图上，View 自己负责 Fit 显示和坐标映射。
 * 这样拖动时不会经过 Compose Canvas、ImageBitmap 包装或大图重组；发送前再由
 * ImageUtils 将这张预览遮罩升采样到原图尺寸。
 */
class MaskDrawView(
    context: Context,
    private val source: Bitmap,
    initialMask: Bitmap? = null
) : View(context) {

    companion object {
        private const val PREVIEW_MAX_EDGE = 1500
    }

    private val previewScale: Float = run {
        val maxDim = max(source.width, source.height)
        if (maxDim > PREVIEW_MAX_EDGE) PREVIEW_MAX_EDGE.toFloat() / maxDim else 1f
    }
    private val previewWidth: Int = (source.width * previewScale).roundToInt().coerceAtLeast(1)
    private val previewHeight: Int = (source.height * previewScale).roundToInt().coerceAtLeast(1)
    private val previewXScale: Float = previewWidth.toFloat() / source.width.toFloat()
    private val previewYScale: Float = previewHeight.toFloat() / source.height.toFloat()

    private var maskBitmap: Bitmap? = normalizeMask(initialMask)
    private var maskCanvas: Canvas? = maskBitmap?.let(::Canvas)

    private val fitRect = RectF()
    private var fitScale = 1f
    private var displayBitmap: Bitmap? = null

    private val sourcePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        alpha = 128
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.RED
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.RED
        style = Paint.Style.FILL
    }
    private val segmentPath = Path()

    private var requestedStrokeWidth = 40f
    private var lastBitmapX = 0f
    private var lastBitmapY = 0f
    private var drawing = false
    private var changedDuringStroke = false

    /** 抬笔或清除时通知 Compose；拖动中的每一帧不会触发 Compose 状态更新。 */
    var onMaskChanged: ((Bitmap?) -> Unit)? = null

    init {
        isClickable = true
        updateStrokeWidth()
    }

    /** 当前预览遮罩实例，仅供 AndroidView 的同步逻辑比较引用。 */
    fun currentMask(): Bitmap? = maskBitmap

    fun setStrokeWidth(width: Float) {
        requestedStrokeWidth = width.coerceAtLeast(1f)
        updateStrokeWidth()
    }

    /** 清除画布上的遮罩并立即通知外层状态。 */
    fun clearMask() {
        drawing = false
        changedDuringStroke = false
        maskBitmap = null
        maskCanvas = null
        invalidate()
        onMaskChanged?.invoke(null)
    }

    /** 由 Compose 外部状态同步遮罩；同一实例不重复替换，避免打断手绘。 */
    fun setMaskBitmap(value: Bitmap?) {
        if (value === maskBitmap) return
        maskBitmap = normalizeMask(value)
        maskCanvas = maskBitmap?.let(::Canvas)
        invalidate()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0 || height <= 0) return

        val scaleX = width.toFloat() / source.width.toFloat()
        val scaleY = height.toFloat() / source.height.toFloat()
        fitScale = min(scaleX, scaleY).coerceAtLeast(0.0001f)
        val dstWidth = max(1, (source.width * fitScale).roundToInt())
        val dstHeight = max(1, (source.height * fitScale).roundToInt())
        val left = (width - dstWidth) / 2f
        val top = (height - dstHeight) / 2f
        fitRect.set(left, top, left + dstWidth, top + dstHeight)

        displayBitmap?.let { cached ->
            if (cached !== source && !cached.isRecycled) cached.recycle()
        }
        displayBitmap = if (dstWidth == source.width && dstHeight == source.height) {
            source
        } else {
            Bitmap.createScaledBitmap(source, dstWidth, dstHeight, true)
        }
        updateStrokeWidth()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val displayed = displayBitmap
        if (displayed != null && !displayed.isRecycled) {
            canvas.drawBitmap(displayed, null, fitRect, sourcePaint)
        } else {
            canvas.drawBitmap(source, null, fitRect, sourcePaint)
        }
        maskBitmap?.takeUnless { it.isRecycled }?.let { mask ->
            // mask 的背景透明，只有红色笔迹会叠加到原图上。
            canvas.drawBitmap(mask, null, fitRect, maskPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                if (!fitRect.contains(event.x, event.y)) {
                    drawing = false
                    changedDuringStroke = false
                    return true
                }
                val point = toBitmapPoint(event.x, event.y)
                ensureMask()
                lastBitmapX = point.first
                lastBitmapY = point.second
                maskCanvas?.drawCircle(
                    lastBitmapX,
                    lastBitmapY,
                    strokePaint.strokeWidth / 2f,
                    dotPaint
                )
                drawing = true
                changedDuringStroke = true
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!drawing) return true
                val point = toBitmapPoint(event.x, event.y)
                val currentX = point.first
                val currentY = point.second
                segmentPath.reset()
                segmentPath.moveTo(lastBitmapX, lastBitmapY)
                segmentPath.quadTo(
                    lastBitmapX,
                    lastBitmapY,
                    (lastBitmapX + currentX) / 2f,
                    (lastBitmapY + currentY) / 2f
                )
                maskCanvas?.drawPath(segmentPath, strokePaint)
                lastBitmapX = currentX
                lastBitmapY = currentY
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                finishStroke()
                return true
            }
        }
        return true
    }

    private fun finishStroke() {
        if (drawing && changedDuringStroke) {
            onMaskChanged?.invoke(maskBitmap)
        }
        drawing = false
        changedDuringStroke = false
    }

    private fun ensureMask() {
        if (maskBitmap == null || maskBitmap?.isRecycled == true) {
            maskBitmap = Bitmap.createBitmap(
                previewWidth,
                previewHeight,
                Bitmap.Config.ARGB_8888
            )
            maskCanvas = Canvas(maskBitmap!!)
        }
        updateStrokeWidth()
    }

    private fun updateStrokeWidth() {
        // 滑杆单位是屏幕像素；先换算到原图坐标，再换算到预览位图坐标。
        val bitmapWidth = (requestedStrokeWidth * previewScale / fitScale).coerceAtLeast(1f)
        strokePaint.strokeWidth = bitmapWidth
        dotPaint.set(strokePaint)
        dotPaint.style = Paint.Style.FILL
    }

    private fun toBitmapPoint(x: Float, y: Float): Pair<Float, Float> {
        // fitRect/fitScale 以原图为坐标系，落笔前必须再乘预览缩放，
        // 否则大图会把坐标直接写到预览位图的错误位置。
        val sourceX = ((x - fitRect.left) / fitScale)
            .coerceIn(0f, (source.width - 1).toFloat())
        val sourceY = ((y - fitRect.top) / fitScale)
            .coerceIn(0f, (source.height - 1).toFloat())
        val bx = (sourceX * previewXScale).coerceIn(0f, (previewWidth - 1).toFloat())
        val by = (sourceY * previewYScale).coerceIn(0f, (previewHeight - 1).toFloat())
        return bx to by
    }

    private fun normalizeMask(value: Bitmap?): Bitmap? {
        if (value == null || value.isRecycled) return null
        return if (value.width == previewWidth && value.height == previewHeight) {
            value
        } else {
            Bitmap.createScaledBitmap(value, previewWidth, previewHeight, true)
        }
    }
}
