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

    class AnimatedDriver(
        val driverNumber: Int,
        var acronym: String,
        var colorHex: String,
        var currentRawX: Float,
        var currentRawY: Float,
        var startRawX: Float = currentRawX,
        var startRawY: Float = currentRawY,
        var targetRawX: Float = currentRawX,
        var targetRawY: Float = currentRawY,
        var animStartTime: Long = 0L,
        var animDurationMs: Long = 200L,
        var lastUpdateUptime: Long = 0L
    ) {
        constructor(
            driverNumber: Int,
            acronym: String,
            colorHex: String,
            currentRawX: Float,
            currentRawY: Float,
            targetRawX: Float,
            targetRawY: Float
        ) : this(
            driverNumber = driverNumber,
            acronym = acronym,
            colorHex = colorHex,
            currentRawX = currentRawX,
            currentRawY = currentRawY,
            startRawX = currentRawX,
            startRawY = currentRawY,
            targetRawX = targetRawX,
            targetRawY = targetRawY,
            animStartTime = android.os.SystemClock.uptimeMillis(),
            animDurationMs = 200L,
            lastUpdateUptime = android.os.SystemClock.uptimeMillis()
        )
    }

    data class SimTrackDriver(
        val driverNumber: Int,
        val acronym: String,
        val colorHex: String,
        var trackProgress: Float
    )

    var isSimulationMode: Boolean = false
    private val simTrackDrivers = mutableListOf<SimTrackDriver>()
    private var lastSimFrameUptime: Long = 0L
    private val tempSplinePoint = PointF()

    private var rawTrackPoints: List<PointF> = emptyList()
    private var markers: List<DriverMarker> = emptyList()
    private val animatedDrivers = mutableMapOf<Int, AnimatedDriver>()
    private val tempScreenPoint = PointF()
    private val tempStartFinishPoint = PointF()
    private val tempAnnPoint = PointF()
    var selectedDriverNumber: Int? = null
    var circuitName: String = "Las Vegas"
    private var turnAnnotations: List<CircuitOutlinesCatalog.CircuitTurnAnnotation> = emptyList()
    private val tempRectF = RectF()

    fun getTrackPoints(): List<PointF> = rawTrackPoints
    fun getAnimatedDrivers(): Map<Int, AnimatedDriver> = animatedDrivers
    fun getSimulationDrivers(): List<SimTrackDriver> = simTrackDrivers

    fun setSimulationDrivers(drivers: List<SimTrackDriver>) {
        simTrackDrivers.clear()
        simTrackDrivers.addAll(drivers)
        lastSimFrameUptime = android.os.SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    // Bounds for coordinate normalization
    private var minX = 0f
    private var maxX = 0f
    private var minY = 0f
    private var maxY = 0f
    private var hasValidBounds = false

    // Smooth Sector Paths
    private val pathSector1 = Path()
    private val pathSector2 = Path()
    private val pathSector3 = Path()
    private val fullTrackBedPath = Path()

    // Paints
    private val trackBedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 18f
        color = Color.parseColor("#141E33")
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

    private val annotationBadgeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#D90F172A")
    }

    private val annotationTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        textSize = 18f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val drsBadgeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#D915803D")
    }

    private val drsTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 17f
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

    init {
        loadCircuit("Las Vegas")
    }

    fun loadCircuit(circuitOrGpName: String) {
        circuitName = circuitOrGpName
        val data = CircuitOutlinesCatalog.getCircuitData(circuitOrGpName)
        turnAnnotations = data.annotations
        rawTrackPoints = data.outlinePoints
        calculateBounds()
        rebuildTrackPaths()
        animatedDrivers.clear()
        if (isSimulationMode && simTrackDrivers.isNotEmpty()) {
            val n = rawTrackPoints.size.coerceAtLeast(1)
            simTrackDrivers.forEachIndexed { idx, d ->
                d.trackProgress = idx * (n.toFloat() / simTrackDrivers.size.toFloat())
            }
        }
        invalidate()
    }

    private fun getTrackPointAtProgress(progress: Float, outPoint: PointF) {
        val n = rawTrackPoints.size
        if (n == 0) {
            outPoint.set(0f, 0f)
            return
        }
        val p = (progress % n + n) % n
        val i0 = p.toInt() % n
        val i1 = (i0 + 1) % n
        val frac = p - i0.toFloat()

        val p0 = rawTrackPoints[i0]
        val p1 = rawTrackPoints[i1]

        outPoint.x = p0.x + (p1.x - p0.x) * frac
        outPoint.y = p0.y + (p1.y - p0.y) * frac
    }

    fun setTrackPoints(points: List<PointF>) {
        if (points.isEmpty()) return
        rawTrackPoints = points
        if (turnAnnotations.isEmpty()) {
            turnAnnotations = CircuitOutlinesCatalog.getCircuitData(circuitName).annotations
        }
        calculateBounds()
        rebuildTrackPaths()
        invalidate()
    }

    fun setDriverPositions(newMarkers: List<DriverMarker>) {
        markers = newMarkers
        if (!hasValidBounds && markers.isNotEmpty()) {
            calculateBoundsFromMarkers()
            rebuildTrackPaths()
        }

        if (newMarkers.isEmpty()) {
            animatedDrivers.clear()
            invalidate()
            return
        }

        val now = android.os.SystemClock.uptimeMillis()
        val activeDriverNumbers = HashSet<Int>(newMarkers.size)
        val maxTrackDimension = (maxX - minX).coerceAtLeast(maxY - minY)

        for (marker in newMarkers) {
            activeDriverNumbers.add(marker.driverNumber)
            val driver = animatedDrivers[marker.driverNumber]
            if (driver == null) {
                animatedDrivers[marker.driverNumber] = AnimatedDriver(
                    driverNumber = marker.driverNumber,
                    acronym = marker.acronym,
                    colorHex = marker.colorHex,
                    currentRawX = marker.rawX,
                    currentRawY = marker.rawY,
                    startRawX = marker.rawX,
                    startRawY = marker.rawY,
                    targetRawX = marker.rawX,
                    targetRawY = marker.rawY,
                    animStartTime = now,
                    animDurationMs = 200L,
                    lastUpdateUptime = now
                )
            } else {
                driver.acronym = marker.acronym
                driver.colorHex = marker.colorHex

                val interval = if (driver.lastUpdateUptime > 0L) {
                    (now - driver.lastUpdateUptime).coerceIn(60L, 2000L)
                } else {
                    200L
                }

                // Check for sudden teleport / first initialization
                val dx = marker.rawX - driver.currentRawX
                val dy = marker.rawY - driver.currentRawY
                val distSq = dx * dx + dy * dy
                val maxAllowedDistSq = (maxTrackDimension * 0.45f) * (maxTrackDimension * 0.45f)

                if ((driver.currentRawX == 0f && driver.currentRawY == 0f) ||
                    (maxTrackDimension > 10f && distSq > maxAllowedDistSq)) {
                    // Snap directly if distance is abnormally huge (e.g. initial spawn or pit teleport)
                    driver.currentRawX = marker.rawX
                    driver.currentRawY = marker.rawY
                    driver.startRawX = marker.rawX
                    driver.startRawY = marker.rawY
                } else {
                    driver.startRawX = driver.currentRawX
                    driver.startRawY = driver.currentRawY
                }

                driver.targetRawX = marker.rawX
                driver.targetRawY = marker.rawY
                driver.animStartTime = now
                driver.animDurationMs = interval
                driver.lastUpdateUptime = now
            }
        }

        animatedDrivers.keys.retainAll(activeDriverNumbers)
        postInvalidateOnAnimation()
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

        // 12% padding for clean margins and turn labels
        val dx = (maxX - minX) * 0.12f
        val dy = (maxY - minY) * 0.12f
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
        fullTrackBedPath.reset()
        pathSector1.reset()
        pathSector2.reset()
        pathSector3.reset()

        if (width <= 0 || height <= 0 || !hasValidBounds || rawTrackPoints.size < 3) return

        val n = rawTrackPoints.size
        val s1End = n / 3
        val s2End = (2 * n) / 3

        val transformedPts = rawTrackPoints.map { transformPoint(it.x, it.y) }

        // Build smooth curved path for the asphalt bed
        buildSmoothPath(fullTrackBedPath, transformedPts, isClosed = true)

        // Build Sector 1 path
        val s1Pts = transformedPts.subList(0, s1End + 1)
        buildSmoothPath(pathSector1, s1Pts, isClosed = false)

        // Build Sector 2 path
        val s2Pts = transformedPts.subList(s1End, s2End + 1)
        buildSmoothPath(pathSector2, s2Pts, isClosed = false)

        // Build Sector 3 path
        val s3Pts = transformedPts.subList(s2End, n) + listOf(transformedPts.first())
        buildSmoothPath(pathSector3, s3Pts, isClosed = false)
    }

    private fun buildSmoothPath(path: Path, pts: List<PointF>, isClosed: Boolean) {
        if (pts.isEmpty()) return
        path.moveTo(pts[0].x, pts[0].y)
        if (pts.size == 1) return

        for (i in 1 until pts.size) {
            val prev = pts[i - 1]
            val curr = pts[i]
            val midX = (prev.x + curr.x) / 2f
            val midY = (prev.y + curr.y) / 2f
            path.quadTo(prev.x, prev.y, midX, midY)
        }
        val last = pts.last()
        path.lineTo(last.x, last.y)
        if (isClosed) {
            path.close()
        }
    }

    fun transformPoint(rawX: Float, rawY: Float): PointF {
        val pt = PointF()
        transformPointInPlace(rawX, rawY, pt)
        return pt
    }

    private fun transformPointInPlace(rawX: Float, rawY: Float, outPoint: PointF) {
        val rangeX = (maxX - minX).coerceAtLeast(1f)
        val rangeY = (maxY - minY).coerceAtLeast(1f)

        val padding = 40f
        val availW = width - (padding * 2)
        val availH = height - (padding * 2)

        val scale = (availW / rangeX).coerceAtMost(availH / rangeY)

        val offsetX = padding + (availW - (rangeX * scale)) / 2f
        val offsetY = padding + (availH - (rangeY * scale)) / 2f

        outPoint.x = offsetX + ((rawX - minX) * scale)
        // Flip Y axis because F1 GPS coordinates usually have positive Y pointing North
        outPoint.y = offsetY + ((maxY - rawY) * scale)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!hasValidBounds || rawTrackPoints.size < 3) return

        // 1. Draw wide dark asphalt bed
        if (!fullTrackBedPath.isEmpty) {
            canvas.drawPath(fullTrackBedPath, trackBedPaint)

            // Draw official 3 sectors
            canvas.drawPath(pathSector1, trackPaintS1)
            canvas.drawPath(pathSector2, trackPaintS2)
            canvas.drawPath(pathSector3, trackPaintS3)

            // 2. Draw Start / Finish line
            transformPointInPlace(rawTrackPoints.first().x, rawTrackPoints.first().y, tempStartFinishPoint)
            canvas.drawCircle(tempStartFinishPoint.x, tempStartFinishPoint.y, 8f, startFinishPaint)
            drawAnnotationBadge(canvas, "🏁 META", tempStartFinishPoint.x, tempStartFinishPoint.y + 26f, annotationBadgeBg, annotationTextPaint)

            // 3. Draw authentic corner and DRS annotations from catalog
            val activeAnnotations = if (turnAnnotations.isNotEmpty()) {
                turnAnnotations
            } else {
                CircuitOutlinesCatalog.getCircuitData(circuitName).annotations
            }

            val totalPts = rawTrackPoints.size
            if (totalPts > 0) {
                for (ann in activeAnnotations) {
                    val ptIdx = ann.pointIndex.coerceIn(0, totalPts - 1)
                    transformPointInPlace(rawTrackPoints[ptIdx].x, rawTrackPoints[ptIdx].y, tempAnnPoint)
                    val bgPaint = if (ann.isDrs) drsBadgeBg else annotationBadgeBg
                    val tPaint = if (ann.isDrs) drsTextPaint else annotationTextPaint
                    drawAnnotationBadge(canvas, ann.label, tempAnnPoint.x, tempAnnPoint.y - 14f, bgPaint, tPaint)
                }
            }
        }

        // 4. Draw Real-time Drivers smoothly animated at 60 FPS
        if (isSimulationMode && simTrackDrivers.isNotEmpty() && rawTrackPoints.size >= 3) {
            val now = android.os.SystemClock.uptimeMillis()
            val dt = if (lastSimFrameUptime > 0L) {
                ((now - lastSimFrameUptime) / 1000f).coerceIn(0.001f, 0.05f)
            } else {
                0.0166f
            }
            lastSimFrameUptime = now

            val n = rawTrackPoints.size
            // Lap speed: complete full circuit in ~75 seconds
            val baseSpeed = n.toFloat() / 75f

            for (i in simTrackDrivers.indices) {
                val driver = simTrackDrivers[i]
                driver.trackProgress = (driver.trackProgress + baseSpeed * dt) % n.toFloat()

                getTrackPointAtProgress(driver.trackProgress, tempSplinePoint)
                transformPointInPlace(tempSplinePoint.x, tempSplinePoint.y, tempScreenPoint)

                val ptX = tempScreenPoint.x
                val ptY = tempScreenPoint.y

                val color = try {
                    val hex = if (driver.colorHex.startsWith("#")) driver.colorHex else "#${driver.colorHex}"
                    Color.parseColor(hex)
                } catch (e: Exception) {
                    Color.parseColor("#E10600")
                }
                markerFillPaint.color = color

                val isSelected = driver.driverNumber == selectedDriverNumber
                if (isSelected) {
                    canvas.drawCircle(ptX, ptY, 22f, highlightRingPaint)
                }

                canvas.drawCircle(ptX, ptY, if (isSelected) 14f else 11f, markerFillPaint)
                canvas.drawCircle(ptX, ptY, if (isSelected) 14f else 11f, markerStrokePaint)

                val label = if (isSelected) "★ ${driver.acronym}" else driver.acronym
                val currentTextPaint = if (isSelected) selectedTextPaint else textPaint
                val currentBgPaint = if (isSelected) selectedLabelBgPaint else labelBgPaint
                val textWidth = currentTextPaint.measureText(label)

                tempRectF.set(
                    ptX - textWidth / 2f - 7f,
                    ptY - 38f,
                    ptX + textWidth / 2f + 7f,
                    ptY - 15f
                )
                canvas.drawRoundRect(tempRectF, 4f, 4f, currentBgPaint)
                canvas.drawText(label, ptX, ptY - 20f, currentTextPaint)
            }

            postInvalidateOnAnimation()
        } else if (animatedDrivers.isNotEmpty()) {
            var hasActiveMotion = false
            val now = android.os.SystemClock.uptimeMillis()

            for (driver in animatedDrivers.values) {
                if (driver.targetRawX == 0f && driver.targetRawY == 0f) continue

                val elapsed = now - driver.animStartTime
                val duration = driver.animDurationMs.coerceAtLeast(1L).toFloat()
                val rawProgress = elapsed.toFloat() / duration

                if (rawProgress < 1.0f) {
                    driver.currentRawX = driver.startRawX + (driver.targetRawX - driver.startRawX) * rawProgress
                    driver.currentRawY = driver.startRawY + (driver.targetRawY - driver.startRawY) * rawProgress
                    hasActiveMotion = true
                } else if (rawProgress < 1.4f) {
                    // Soft extrapolation buffer in case next GPS packet has small network/dispatch jitter
                    val extra = (rawProgress - 1.0f).coerceAtMost(0.4f)
                    val damping = 1.0f - (extra / 0.4f)
                    driver.currentRawX = driver.targetRawX + (driver.targetRawX - driver.startRawX) * extra * damping * 0.5f
                    driver.currentRawY = driver.targetRawY + (driver.targetRawY - driver.startRawY) * extra * damping * 0.5f
                    hasActiveMotion = true
                } else {
                    driver.currentRawX = driver.targetRawX
                    driver.currentRawY = driver.targetRawY
                }

                transformPointInPlace(driver.currentRawX, driver.currentRawY, tempScreenPoint)
                val ptX = tempScreenPoint.x
                val ptY = tempScreenPoint.y

                val color = try {
                    val hex = if (driver.colorHex.startsWith("#")) driver.colorHex else "#${driver.colorHex}"
                    Color.parseColor(hex)
                } catch (e: Exception) {
                    Color.parseColor("#E10600")
                }
                markerFillPaint.color = color

                val isSelected = driver.driverNumber == selectedDriverNumber
                if (isSelected) {
                    canvas.drawCircle(ptX, ptY, 22f, highlightRingPaint)
                }

                canvas.drawCircle(ptX, ptY, if (isSelected) 14f else 11f, markerFillPaint)
                canvas.drawCircle(ptX, ptY, if (isSelected) 14f else 11f, markerStrokePaint)

                val label = if (isSelected) "★ ${driver.acronym}" else driver.acronym
                val currentTextPaint = if (isSelected) selectedTextPaint else textPaint
                val currentBgPaint = if (isSelected) selectedLabelBgPaint else labelBgPaint
                val textWidth = currentTextPaint.measureText(label)

                tempRectF.set(
                    ptX - textWidth / 2f - 7f,
                    ptY - 38f,
                    ptX + textWidth / 2f + 7f,
                    ptY - 15f
                )
                canvas.drawRoundRect(tempRectF, 4f, 4f, currentBgPaint)
                canvas.drawText(label, ptX, ptY - 20f, currentTextPaint)
            }

            if (hasActiveMotion) {
                postInvalidateOnAnimation()
            }
        }
    }

    private fun drawAnnotationBadge(canvas: Canvas, text: String, x: Float, y: Float, bgPaint: Paint, tPaint: Paint) {
        val tw = tPaint.measureText(text)
        tempRectF.set(x - tw / 2f - 7f, y - 16f, x + tw / 2f + 7f, y + 6f)
        canvas.drawRoundRect(tempRectF, 4f, 4f, bgPaint)
        canvas.drawText(text, x, y, tPaint)
    }
}
