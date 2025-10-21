package io.github.nrob.motiondetector.visualization

import io.github.nrob.motiondetector.MotionState
import kotlin.math.max

/**
 * Platformfüggetlen “renderer” logika.
 * Az Android/iOS/Desktop adapterek csak a tényleges rajzolást végzik.
 */
data class MotionFrame(
    val raw: Float,
    val filtered: Float,
    val rms: Float,
    val used: Float,
    val isMoving: Boolean,
    val movingForMs: Long,
    val stillForMs: Long,
    val motionStartThreshold: Float,
    val motionStopThreshold: Float,
    val timestamp: Long,
    val gpsEvent: GpsIndicator? = null
) {
    companion object {
        fun fromState(state: MotionState, gps: GpsIndicator? = null): MotionFrame =
            MotionFrame(
                raw = state.rawAccel,
                filtered = state.filteredAccel,
                rms = state.rmsAccel,
                used = state.usedAccel,
                isMoving = state.isMoving,
                movingForMs = state.movingForMs,
                stillForMs = state.stillForMs,
                motionStartThreshold = state.motionStartThreshold,
                motionStopThreshold = state.motionStopThreshold,
                timestamp = state.timestamp,
                gpsEvent = gps
            )
    }
}

data class GpsIndicator(
    val timestamp: Long,
    val accuracy: Float,
    val saved: Boolean
)

data class RenderRect(val x: Float, val width: Float, val color: Color)
data class RenderLine(val startX: Float, val startY: Float, val endX: Float, val endY: Float, val color: Color, val strokeWidth: Float)
data class RenderCircle(val centerX: Float, val centerY: Float, val radius: Float, val color: Color)

data class RenderFrame(
    val timestamp: Long = 0L,
    val frameIndex: Int = 0,
    val rects: List<RenderRect>,
    val rawLines: List<RenderLine>,
    val smoothLines: List<RenderLine>,
    val rmsLines: List<RenderLine>,
    val usedLines: List<RenderLine>,
    val thresholdLines: List<RenderLine>,
    val circleIndicators: List<RenderCircle>
)

data class Color(val r: Int, val g: Int, val b: Int, val alpha: Float = 1f) {
    fun withAlpha(a: Float) = Color(r, g, b, a)
}

object GraphColors {
    val bgIdle = Color(255, 255, 255)
    val bgThreshold = Color(231, 255, 230)
    val bgMoving = Color(203, 255, 201)
    val lineStartThreshold = Color(54, 122, 54)
    val lineStopThreshold = Color(255, 0, 0)
    val lineRMS = Color(255, 0, 255)
    val lineUsed = Color(255, 128, 0)
    val lineRaw = Color(64, 64, 64)
    val lineSmooth = Color(0, 0, 255)
    val gpsValid = Color(0, 255, 0)
    val gpsInvalid = Color(255, 0, 0)
}

/**
 * Builds render frames lazily from motion states.
 */
object MotionGraphRenderer {
    fun buildRenderFrames(
        motionStates: Sequence<MotionFrame>,
        maxPoints: Int,
        canvasWidth: Float,
        canvasHeight: Float
    ): Sequence<RenderFrame> = sequence {
        val window = ArrayDeque<MotionFrame>()

        var frameIndex = 0
        for (state in motionStates) {
            if (window.size >= maxPoints) window.removeFirst()
            window.addLast(state)
            val latest = window.last()

            // compute maxAccel for height scaling
            val dataMax = window.maxOfOrNull { max(it.raw, max(it.filtered, max(it.rms, it.used))) } ?: 0f
            val maxAccel = max(
                dataMax,
                latest.motionStartThreshold * 1.2f  // 20% padding above start threshold
            ).coerceAtLeast(0.5f)

            val widthPerPoint = canvasWidth / maxPoints
            val heightScale = canvasHeight / maxAccel

            // rects for background
            val rects = window.mapIndexed { i, s ->
                val bgColor = when {
                    s.isMoving && s.stillForMs > 0L -> GraphColors.bgThreshold
                    s.isMoving -> GraphColors.bgMoving
                    !s.isMoving && s.movingForMs > 0L -> GraphColors.bgThreshold
                    else -> GraphColors.bgIdle
                }
                RenderRect(x = i * widthPerPoint, width = widthPerPoint, color = bgColor)
            }

            // lines
            val rawLines = mutableListOf<RenderLine>()
            val smoothLines = mutableListOf<RenderLine>()
            val rmsLines = mutableListOf<RenderLine>()
            val usedLines = mutableListOf<RenderLine>()

            for (i in 0 until window.size - 1) {
                val cur = window[i]
                val next = window[i + 1]

                rawLines.add(
                    RenderLine(
                        startX = i * widthPerPoint,
                        startY = canvasHeight - cur.raw * heightScale,
                        endX = (i + 1) * widthPerPoint,
                        endY = canvasHeight - next.raw * heightScale,
                        color = GraphColors.lineRaw,
                        strokeWidth = 4f
                    )
                )
                smoothLines.add(
                    RenderLine(
                        startX = i * widthPerPoint,
                        startY = canvasHeight - cur.filtered * heightScale,
                        endX = (i + 1) * widthPerPoint,
                        endY = canvasHeight - next.filtered * heightScale,
                        color = GraphColors.lineSmooth,
                        strokeWidth = 4f
                    )
                )
                rmsLines.add(
                    RenderLine(
                        startX = i * widthPerPoint,
                        startY = canvasHeight - cur.rms * heightScale,
                        endX = (i + 1) * widthPerPoint,
                        endY = canvasHeight - next.rms * heightScale,
                        color = GraphColors.lineRMS,
                        strokeWidth = 4f
                    )
                )
                usedLines.add(
                    RenderLine(
                        startX = i * widthPerPoint,
                        startY = canvasHeight - cur.used * heightScale,
                        endX = (i + 1) * widthPerPoint,
                        endY = canvasHeight - next.used * heightScale,
                        color = GraphColors.lineUsed,
                        strokeWidth = 8f
                    )
                )
            }

            // threshold lines
            val thresholdLines = listOf(
                RenderLine(0f, canvasHeight - latest.motionStartThreshold * heightScale, canvasWidth,
                    canvasHeight - latest.motionStartThreshold * heightScale, GraphColors.lineStartThreshold, 2f),
                RenderLine(0f, canvasHeight - latest.motionStopThreshold * heightScale, canvasWidth,
                    canvasHeight - latest.motionStopThreshold * heightScale, GraphColors.lineStopThreshold, 2f)
            )

            // gps indicators
            val circleIndicators = latest.gpsEvent?.let { gps ->
                val timeSinceGps = System.currentTimeMillis() - gps.timestamp
                if (timeSinceGps < 3000) {
                    val alpha = (1f - timeSinceGps / 3000f).coerceIn(0f, 1f)
                    val color = if (gps.saved) GraphColors.gpsValid.withAlpha(alpha) else GraphColors.gpsInvalid.withAlpha(alpha)
                    listOf(RenderCircle(
                        centerX = canvasWidth - 50f,
                        centerY = 50f,
                        radius = 40f,
                        color = color
                    ))
                } else emptyList()
            } ?: emptyList()

            yield(
                RenderFrame(
                    frameIndex = frameIndex,
                    rects = rects,
                    rawLines = rawLines,
                    smoothLines = smoothLines,
                    rmsLines = rmsLines,
                    usedLines = usedLines,
                    thresholdLines = thresholdLines,
                    circleIndicators = circleIndicators
                )
            )
            frameIndex++
        }
    }
}
