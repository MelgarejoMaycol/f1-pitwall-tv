package com.f1pitwall.tv.ui.pitwall

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

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

    // Simulation state for pre-session / offline
    private var isSimulating = false
    private var simProgress = 0f
    private var pulseRadius = 22f
    private var pulseDirection = 0.25f

    // Sector Paths
    private val pathSector1 = Path()
    private val pathSector2 = Path()
    private val pathSector3 = Path()
    private val fullTrackGlowPath = Path()

    // Paints
    private val trackGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 16f
        color = Color.parseColor("#1E293B")
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val trackPaintS1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#38BDF8") // Cyan Sector 1
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val trackPaintS2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#A855F7") // Purple Sector 2
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val trackPaintS3 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#F59E0B") // Amber Sector 3
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val startFinishPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#EF4444")
    }

    private val startFinishTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 18f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
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
        textSize = 20f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC0B0F19")
        style = Paint.Style.FILL
    }

    private val selectedLabelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6F59E0B")
        style = Paint.Style.FILL
    }

    private val selectedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 20f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    // Default simulation drivers
    private val simDrivers = listOf(
        DriverMarker(1, "VER", "#3671C6", 0f, 0f),
        DriverMarker(11, "PER", "#3671C6", 0f, 0f),
        DriverMarker(4, "NOR", "#FF8000", 0f, 0f),
        DriverMarker(81, "PIA", "#FF8000", 0f, 0f),
        DriverMarker(16, "LEC", "#E80020", 0f, 0f),
        DriverMarker(55, "SAI", "#E80020", 0f, 0f),
        DriverMarker(63, "RUS", "#27F4D2", 0f, 0f),
        DriverMarker(44, "HAM", "#27F4D2", 0f, 0f),
        DriverMarker(14, "ALO", "#229971", 0f, 0f),
        DriverMarker(18, "STR", "#229971", 0f, 0f),
        DriverMarker(10, "GAS", "#0093CC", 0f, 0f),
        DriverMarker(31, "OCO", "#0093CC", 0f, 0f),
        DriverMarker(23, "ALB", "#64C4FF", 0f, 0f),
        DriverMarker(43, "COL", "#64C4FF", 0f, 0f),
        DriverMarker(22, "TSU", "#6692FF", 0f, 0f),
        DriverMarker(30, "LAW", "#6692FF", 0f, 0f),
        DriverMarker(27, "HUL", "#B6BABD", 0f, 0f),
        DriverMarker(20, "MAG", "#B6BABD", 0f, 0f),
        DriverMarker(77, "BOT", "#52E252", 0f, 0f),
        DriverMarker(24, "ZHO", "#52E252", 0f, 0f)
    )

    init {
        // Load default Grand Prix circuit outline (Las Vegas Strip Circuit template)
        loadDefaultCircuitOutline()
    }

    private fun loadDefaultCircuitOutline() {
        if (rawTrackPoints.isNotEmpty()) return
        val defaultPts = listOf(
            PointF(2266f, -202f), PointF(2500f, 1000f), PointF(2700f, 2500f),
            PointF(2800f, 4000f), PointF(2600f, 5500f), PointF(1800f, 6500f),
            PointF(1000f, 7500f), PointF(200f, 8500f), PointF(-500f, 9500f),
            PointF(-1500f, 10500f), PointF(-2800f, 11800f), PointF(-4200f, 13200f),
            PointF(-5500f, 14500f), PointF(-5942f, 15194f), PointF(-6800f, 15500f),
            PointF(-7500f, 15200f), PointF(-7700f, 14000f), PointF(-7500f, 12500f),
            PointF(-6800f, 10000f), PointF(-5800f, 7500f), PointF(-4500f, 5000f),
            PointF(-3200f, 2500f), PointF(-1800f, 500f), PointF(-500f, -500f),
            PointF(500f, -1000f), PointF(1500f, -800f), PointF(2266f, -202f)
        )
        setTrackPoints(defaultPts)
    }

    fun setTrackPoints(points: List<PointF>) {
        if (points.isEmpty()) return
        rawTrackPoints = points
        calculateBounds()
        rebuildTrackPaths()
        invalidate()
    }

    fun setDriverPositions(newMarkers: List<DriverMarker>) {
        if (newMarkers.isNotEmpty()) {
            markers = newMarkers
            isSimulating = false
            if (!hasValidBounds && markers.isNotEmpty()) {
                calculateBoundsFromMarkers()
                rebuildTrackPaths()
            }
        } else {
            isSimulating = true
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
        rebuildTrackPaths()
    }

    private fun rebuildTrackPaths() {
        fullTrackGlowPath.reset()
        pathSector1.reset()
        pathSector2.reset()
        pathSector3.reset()

        if (width <= 0 || height <= 0 || !hasValidBounds || rawTrackPoints.size < 3) return

        val n = rawTrackPoints.size
        val s1End = n / 3
        val s2End = (2 * n) / 3

        val first = transformPoint(rawTrackPoints[0].x, rawTrackPoints[0].y)
        fullTrackGlowPath.moveTo(first.x, first.y)
        pathSector1.moveTo(first.x, first.y)

        for (i in 1 until n) {
            val pt = transformPoint(rawTrackPoints[i].x, rawTrackPoints[i].y)
            fullTrackGlowPath.lineTo(pt.x, pt.y)

            when {
                i <= s1End -> pathSector1.lineTo(pt.x, pt.y)
                i == s1End + 1 -> {
                    pathSector2.moveTo(transformPoint(rawTrackPoints[s1End].x, rawTrackPoints[s1End].y).x,
                                       transformPoint(rawTrackPoints[s1End].x, rawTrackPoints[s1End].y).y)
                    pathSector2.lineTo(pt.x, pt.y)
                }
                i <= s2End -> pathSector2.lineTo(pt.x, pt.y)
                i == s2End + 1 -> {
                    pathSector3.moveTo(transformPoint(rawTrackPoints[s2End].x, rawTrackPoints[s2End].y).x,
                                       transformPoint(rawTrackPoints[s2End].x, rawTrackPoints[s2End].y).y)
                    pathSector3.lineTo(pt.x, pt.y)
                }
                else -> pathSector3.lineTo(pt.x, pt.y)
            }
        }
        fullTrackGlowPath.close()
        pathSector3.lineTo(first.x, first.y)
    }

    private fun transformPoint(rawX: Float, rawY: Float): PointF {
        val rangeX = (maxX - minX).coerceAtLeast(1f)
        val rangeY = (maxY - minY).coerceAtLeast(1f)

        val padding = 44f
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

        if (!hasValidBounds || rawTrackPoints.size < 3) {
            return
        }

        // 1. Draw glowing background track
        if (!fullTrackGlowPath.isEmpty) {
            canvas.drawPath(fullTrackGlowPath, trackGlowPaint)
            // Draw official 3 sectors
            canvas.drawPath(pathSector1, trackPaintS1)
            canvas.drawPath(pathSector2, trackPaintS2)
            canvas.drawPath(pathSector3, trackPaintS3)

            // Start / Finish Line marker
            val startPt = transformPoint(rawTrackPoints.first().x, rawTrackPoints.first().y)
            canvas.drawCircle(startPt.x, startPt.y, 8f, startFinishPaint)
            canvas.drawText("META", startPt.x, startPt.y + 24f, startFinishTextPaint)
        }

        // Pulse ring animation for selected driver
        pulseRadius += pulseDirection
        if (pulseRadius > 27f || pulseRadius < 21f) {
            pulseDirection = -pulseDirection
        }

        // 2. Draw drivers (Live or Simulated)
        val activeMarkers = if (!isSimulating && markers.isNotEmpty()) {
            markers
        } else {
            getSimulatedMarkers()
        }

        for (marker in activeMarkers) {
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
                canvas.drawCircle(pt.x, pt.y, pulseRadius, highlightRingPaint)
            }

            // Dot
            canvas.drawCircle(pt.x, pt.y, if (isSelected) 15f else 12f, markerFillPaint)
            canvas.drawCircle(pt.x, pt.y, if (isSelected) 15f else 12f, markerStrokePaint)

            // Label pill
            val label = marker.acronym
            val currentTextPaint = if (isSelected) selectedTextPaint else textPaint
            val currentBgPaint = if (isSelected) selectedLabelBgPaint else labelBgPaint
            val textWidth = currentTextPaint.measureText(label)

            val rect = RectF(
                pt.x - textWidth / 2f - 6f,
                pt.y - 38f,
                pt.x + textWidth / 2f + 6f,
                pt.y - 16f
            )
            canvas.drawRoundRect(rect, 4f, 4f, currentBgPaint)
            canvas.drawText(label, pt.x, pt.y - 20f, currentTextPaint)
        }

        // Animate simulation cars smoothly
        if (isSimulating || markers.isEmpty()) {
            simProgress = (simProgress + 0.0012f) % 1f
            postInvalidateOnAnimation()
        } else if (selectedDriverNumber != null) {
            // Keep pulse alive for selected driver
            postInvalidateOnAnimation()
        }
    }

    private fun getSimulatedMarkers(): List<DriverMarker> {
        if (rawTrackPoints.size < 3) return emptyList()
        val n = rawTrackPoints.size
        return simDrivers.mapIndexed { idx, d ->
            val fraction = (idx.toFloat() / simDrivers.size.toFloat() + simProgress) % 1f
            val ptIndex = ((fraction * n).toInt()).coerceIn(0, n - 1)
            val pt = rawTrackPoints[ptIndex]
            d.copy(rawX = pt.x, rawY = pt.y)
        }
    }
}
