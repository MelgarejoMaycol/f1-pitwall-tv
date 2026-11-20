package com.f1pitwall.tv.ui.pitwall

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils

class TrackMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class DriverMarker(
        val driverNumber: Int,
        val acronym: String,
        val colorHex: String,
        val rawX: Float,
        val rawY: Float
    )

    private var rawTrackPoints: List<PointF> = emptyList()
    private var markers: List<DriverMarker> = emptyList()
    var selectedDriverNumber: Int? = null

    // Bounds for coordinate normalization
    private var minX = 0f
    private var maxX = 0f
    private var minY = 0f
    private var maxY = 0f
    private var hasValidBounds = false

    // Paints
    private val trackGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        color = Color.parseColor("#1E293B")
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#38BDF8")
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val startFinishPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = Color.parseColor("#EF4444")
        strokeCap = Paint.Cap.BUTT
    }

    private val markerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val markerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
    }

    private val highlightRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#F59E0B")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC0B0F19")
        style = Paint.Style.FILL
    }

    private val infoTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textSize = 26f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    private val trackPath = Path()

    fun setTrackPoints(points: List<PointF>) {
        if (points.isEmpty()) return
        rawTrackPoints = points
        calculateBounds()
        rebuildTrackPath()
        invalidate()
    }

    fun setDriverPositions(newMarkers: List<DriverMarker>) {
        markers = newMarkers
        if (!hasValidBounds && markers.isNotEmpty()) {
            calculateBoundsFromMarkers()
            rebuildTrackPath()
        }
        invalidate()
    }

    private fun calculateBounds() {
        if (rawTrackPoints.isEmpty()) {
            hasValidBounds = false
            return
        }
        minX = rawTrackPoints.minOf { it.x }
        maxX = rawTrackPoints.maxOf { it.x }
        minY = rawTrackPoints.minOf { it.y }
        maxY = rawTrackPoints.maxOf { it.y }

        // Expand bounds by 10% margin
        val dx = (maxX - minX) * 0.1f
        val dy = (maxY - minY) * 0.1f
        minX -= dx
        maxX += dx
        minY -= dy
        maxY += dy
        hasValidBounds = (maxX - minX > 10f) && (maxY - minY > 10f)
    }

    private fun calculateBoundsFromMarkers() {
        if (markers.isEmpty()) return
        val xs = markers.map { it.rawX }.filter { it != 0f }
        val ys = markers.map { it.rawY }.filter { it != 0f }
        if (xs.isEmpty() || ys.isEmpty()) return

        minX = xs.minOrNull() ?: 0f
        maxX = xs.maxOrNull() ?: 0f
        minY = ys.minOrNull() ?: 0f
        maxY = ys.maxOrNull() ?: 0f

        val dx = ((maxX - minX) * 0.2f).coerceAtLeast(100f)
        val dy = ((maxY - minY) * 0.2f).coerceAtLeast(100f)
        minX -= dx
        maxX += dx
        minY -= dy
        maxY += dy
        hasValidBounds = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildTrackPath()
    }

    private fun rebuildTrackPath() {
        trackPath.reset()
        if (width <= 0 || height <= 0 || !hasValidBounds) return

        if (rawTrackPoints.size > 2) {
            val first = transformPoint(rawTrackPoints[0].x, rawTrackPoints[0].y)
            trackPath.moveTo(first.x, first.y)
            for (i in 1 until rawTrackPoints.size) {
                val pt = transformPoint(rawTrackPoints[i].x, rawTrackPoints[i].y)
                trackPath.lineTo(pt.x, pt.y)
            }
            trackPath.close()
        }
    }

    private fun transformPoint(rawX: Float, rawY: Float): PointF {
        val rangeX = (maxX - minX).coerceAtLeast(1f)
        val rangeY = (maxY - minY).coerceAtLeast(1f)

        val padding = 40f
        val availW = width - (padding * 2)
        val availH = height - (padding * 2)

        val scale = (availW / rangeX).coerceAtMost(availH / rangeY)

        val offsetX = padding + (availW - (rangeX * scale)) / 2f
        val offsetY = padding + (availH - (rangeY * scale)) / 2f

        val screenX = offsetX + ((rawX - minX) * scale)
        // Flip Y axis because F1 GPS coordinates usually have positive Y pointing North
        val screenY = offsetY + ((maxY - rawY) * scale)

        return PointF(screenX, screenY)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!hasValidBounds || (rawTrackPoints.isEmpty() && markers.isEmpty())) {
            // Draw sleek placeholder circuit
            drawPlaceholderCircuit(canvas)
            return
        }

        // Draw track outline glow + main asphalt line
        if (!trackPath.isEmpty) {
            canvas.drawPath(trackPath, trackGlowPaint)
            canvas.drawPath(trackPath, trackPaint)

            // Draw start / finish line at first point
            val startPt = transformPoint(rawTrackPoints.first().x, rawTrackPoints.first().y)
            canvas.drawCircle(startPt.x, startPt.y, 8f, startFinishPaint)
        }

        // Draw drivers on track
        for (marker in markers) {
            if (marker.rawX == 0f && marker.rawY == 0f) continue
            val pt = transformPoint(marker.rawX, marker.rawY)

            // Team color
            val color = try {
                val hex = if (marker.colorHex.startsWith("#")) marker.colorHex else "#${marker.colorHex}"
                Color.parseColor(hex)
            } catch (e: Exception) {
                Color.parseColor("#E10600")
            }
            markerFillPaint.color = color

            // Highlight ring if selected
            val isSelected = marker.driverNumber == selectedDriverNumber
            if (isSelected) {
                canvas.drawCircle(pt.x, pt.y, 22f, highlightRingPaint)
            }

            // Dot
            canvas.drawCircle(pt.x, pt.y, 14f, markerFillPaint)
            canvas.drawCircle(pt.x, pt.y, 14f, markerStrokePaint)

            // Label pill
            val label = marker.acronym
            val textWidth = textPaint.measureText(label)
            val rect = RectF(
                pt.x - textWidth / 2f - 6f,
                pt.y - 38f,
                pt.x + textWidth / 2f + 6f,
                pt.y - 16f
            )
            canvas.drawRoundRect(rect, 4f, 4f, labelBgPaint)
            canvas.drawText(label, pt.x, pt.y - 20f, textPaint)
        }
    }

    private fun drawPlaceholderCircuit(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val rx = (width / 2f - 60f).coerceAtLeast(50f)
        val ry = (height / 2f - 80f).coerceAtLeast(50f)

        val placeholderPath = Path().apply {
            addRoundRect(
                RectF(cx - rx, cy - ry, cx + rx, cy + ry),
                60f, 60f,
                Path.Direction.CW
            )
        }
        canvas.drawPath(placeholderPath, trackGlowPaint)
        canvas.drawPath(placeholderPath, trackPaint)
        canvas.drawText("🗺️ CONECTANDO AL GPS DE MONOPLAZAS...", cx, cy + 10f, infoTextPaint)
    }
}
