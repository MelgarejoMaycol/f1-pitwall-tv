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
        loadDefaultLasVegasTrack()
    }

    private fun loadDefaultLasVegasTrack() {
        if (rawTrackPoints.isNotEmpty()) return
        // 120 authentic GPS coordinates from Verstappen's fastest lap at Las Vegas Strip Circuit (OpenF1 session 9644)
        val officialLap = listOf(
            PointF(2353f, -289f), PointF(2712f, 111f), PointF(3078f, 520f), PointF(3514f, 1003f),
            PointF(3761f, 1336f), PointF(3787f, 1570f), PointF(3673f, 1802f), PointF(3460f, 1948f),
            PointF(3213f, 1993f), PointF(2800f, 1887f), PointF(2519f, 1695f), PointF(2182f, 1372f),
            PointF(1871f, 1186f), PointF(1405f, 1203f), PointF(1096f, 1345f), PointF(806f, 1606f),
            PointF(587f, 2117f), PointF(518f, 2830f), PointF(491f, 3254f), PointF(461f, 3903f),
            PointF(419f, 4955f), PointF(393f, 5689f), PointF(363f, 6552f), PointF(340f, 7207f),
            PointF(322f, 7720f), PointF(296f, 8456f), PointF(274f, 9086f), PointF(253f, 9684f),
            PointF(279f, 10341f), PointF(356f, 10534f), PointF(522f, 10683f), PointF(844f, 10709f),
            PointF(1205f, 10710f), PointF(1418f, 10712f), PointF(1699f, 10714f), PointF(2184f, 10720f),
            PointF(2623f, 10810f), PointF(3216f, 11141f), PointF(3471f, 11394f), PointF(3702f, 11739f),
            PointF(3819f, 12025f), PointF(3877f, 12293f), PointF(3873f, 12595f), PointF(3755f, 12837f),
            PointF(3533f, 12921f), PointF(3275f, 12926f), PointF(3546f, 13543f), PointF(3640f, 13812f),
            PointF(3611f, 14008f), PointF(3419f, 14125f), PointF(3058f, 14154f), PointF(2470f, 14167f),
            PointF(1795f, 14182f), PointF(1088f, 14207f), PointF(413f, 14246f), PointF(-200f, 14299f),
            PointF(-489f, 14352f), PointF(-552f, 14426f), PointF(-555f, 14516f), PointF(-517f, 14577f),
            PointF(-487f, 14643f), PointF(-682f, 14761f), PointF(-1217f, 15096f), PointF(-1815f, 15467f),
            PointF(-2440f, 15858f), PointF(-3093f, 16265f), PointF(-3777f, 16692f), PointF(-4474f, 17130f),
            PointF(-4767f, 17629f), PointF(-4986f, 17774f), PointF(-5235f, 17740f), PointF(-5451f, 17482f),
            PointF(-5736f, 16906f), PointF(-6076f, 16086f), PointF(-6409f, 15152f), PointF(-6718f, 14032f),
            PointF(-6909f, 13142f), PointF(-7042f, 12450f), PointF(-7179f, 11666f), PointF(-7304f, 10839f),
            PointF(-7419f, 9993f), PointF(-7522f, 9152f), PointF(-7615f, 8318f), PointF(-7662f, 7502f),
            PointF(-7684f, 6635f), PointF(-7706f, 5779f), PointF(-7719f, 4930f), PointF(-7723f, 4075f),
            PointF(-7724f, 3216f), PointF(-7728f, 2351f), PointF(-7730f, 1479f), PointF(-7733f, 608f),
            PointF(-7733f, -474f), PointF(-7734f, -738f), PointF(-7724f, -979f), PointF(-7637f, -1193f),
            PointF(-7437f, -1373f), PointF(-7130f, -1497f), PointF(-6738f, -1559f), PointF(-6288f, -1591f),
            PointF(-5802f, -1614f), PointF(-5310f, -1645f), PointF(-4821f, -1695f), PointF(-4334f, -1717f),
            PointF(-3844f, -1719f), PointF(-3353f, -1722f), PointF(-2862f, -1719f), PointF(-2372f, -1718f),
            PointF(-1879f, -1700f), PointF(-1388f, -1666f), PointF(-1297f, -1653f), PointF(-588f, -1643f),
            PointF(94f, -1619f), PointF(811f, -1538f), PointF(1672f, -1058f), PointF(2353f, -289f)
        )
        setTrackPoints(officialLap)
    }

    fun setTrackPoints(points: List<PointF>) {
        if (points.isEmpty()) return
        rawTrackPoints = points
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

        if (!hasValidBounds || rawTrackPoints.size < 3) return

        // 1. Draw wide dark asphalt bed
        if (!fullTrackBedPath.isEmpty) {
            canvas.drawPath(fullTrackBedPath, trackBedPaint)

            // Draw official 3 sectors
            canvas.drawPath(pathSector1, trackPaintS1)
            canvas.drawPath(pathSector2, trackPaintS2)
            canvas.drawPath(pathSector3, trackPaintS3)

            // 2. Draw Start / Finish line
            val startPt = transformPoint(rawTrackPoints.first().x, rawTrackPoints.first().y)
            canvas.drawCircle(startPt.x, startPt.y, 8f, startFinishPaint)
            drawAnnotationBadge(canvas, "🏁 META", startPt.x, startPt.y + 26f, annotationBadgeBg, annotationTextPaint)

            // 3. Draw key turn annotations on track (clean, non-intrusive)
            val n = rawTrackPoints.size
            if (n >= 100) {
                // T1 (Curva 1)
                val t1Pt = transformPoint(rawTrackPoints[10].x, rawTrackPoints[10].y)
                drawAnnotationBadge(canvas, "T1", t1Pt.x + 22f, t1Pt.y - 10f, annotationBadgeBg, annotationTextPaint)

                // T4 (The Sphere)
                val t4Pt = transformPoint(rawTrackPoints[25].x, rawTrackPoints[25].y)
                drawAnnotationBadge(canvas, "T4 Sphere", t4Pt.x + 36f, t4Pt.y, annotationBadgeBg, annotationTextPaint)

                // DRS 1 Zone
                val drs1Pt = transformPoint(rawTrackPoints[35].x, rawTrackPoints[35].y)
                drawAnnotationBadge(canvas, "DRS 1", drs1Pt.x, drs1Pt.y - 20f, drsBadgeBg, drsTextPaint)

                // T12 (Las Vegas Blvd)
                val t12Pt = transformPoint(rawTrackPoints[70].x, rawTrackPoints[70].y)
                drawAnnotationBadge(canvas, "T12 Strip", t12Pt.x - 36f, t12Pt.y, annotationBadgeBg, annotationTextPaint)

                // DRS 2 Zone (The Strip 1.9km Straight)
                val drs2Pt = transformPoint(rawTrackPoints[85].x, rawTrackPoints[85].y)
                drawAnnotationBadge(canvas, "DRS 2 (1.9 km)", drs2Pt.x - 60f, drs2Pt.y, drsBadgeBg, drsTextPaint)

                // T14 (Frenada Strip)
                val t14Pt = transformPoint(rawTrackPoints[95].x, rawTrackPoints[95].y)
                drawAnnotationBadge(canvas, "T14", t14Pt.x - 22f, t14Pt.y + 22f, annotationBadgeBg, annotationTextPaint)
            }
        }

        // 4. Draw Real-time Drivers ONLY if there are active live markers on track
        if (markers.isNotEmpty()) {
            for (marker in markers) {
                if (marker.rawX == 0f && marker.rawY == 0f) continue
                val pt = transformPoint(marker.rawX, marker.rawY)

                val color = try {
                    val hex = if (marker.colorHex.startsWith("#")) marker.colorHex else "#${marker.colorHex}"
                    Color.parseColor(hex)
                } catch (e: Exception) {
                    Color.parseColor("#E10600")
                }
                markerFillPaint.color = color

                val isSelected = marker.driverNumber == selectedDriverNumber
                if (isSelected) {
                    canvas.drawCircle(pt.x, pt.y, 24f, highlightRingPaint)
                }

                canvas.drawCircle(pt.x, pt.y, if (isSelected) 15f else 12f, markerFillPaint)
                canvas.drawCircle(pt.x, pt.y, if (isSelected) 15f else 12f, markerStrokePaint)

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
        }
    }

    private fun drawAnnotationBadge(canvas: Canvas, text: String, x: Float, y: Float, bgPaint: Paint, tPaint: Paint) {
        val tw = tPaint.measureText(text)
        val rect = RectF(x - tw / 2f - 6f, y - 16f, x + tw / 2f + 6f, y + 6f)
        canvas.drawRoundRect(rect, 4f, 4f, bgPaint)
        canvas.drawText(text, x, y, tPaint)
    }
}
