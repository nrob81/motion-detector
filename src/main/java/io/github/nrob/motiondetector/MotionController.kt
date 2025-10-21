package io.github.nrob.motiondetector

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.sqrt

private const val TAG = "MotionCtrl"

/**
 * Motion detection state machine with hysteresis and spike filtering.
 *
 * Algorithm:
 * 1. Low-pass filter (α=0.8) to estimate gravity
 * 2. High-pass filter: linear acceleration = raw - gravity
 * 3. Horizontal magnitude = sqrt(x² + y²)
 * 4. RMS calculation over ~2s rolling window
 * 5. Exponential smoothing of RMS signal
 * 6. Spike rejection: ignore sudden jumps (phone pickup detection)
 * 7. Hysteresis state machine:
 *    - STILL → MOVING: acceleration > threshold for configured delay
 *    - MOVING → STILL: acceleration < threshold for configured delay
 *
 * The hysteresis delays prevent false triggers from brief movements like
 * picking up the phone, bumps, or accidental taps.
 *
 * Singleton pattern: provides single source of truth for motion state.
 * Call init() with MotionConfig and Logger before first use.
 */
object MotionController {
    private lateinit var config: MotionConfig
    private lateinit var logger: Logger

    private val _motionFlow = MutableStateFlow(MotionState())
    val motionFlow: StateFlow<MotionState> = _motionFlow.asStateFlow()

    // Low-pass filter state for gravity estimation (α=0.8)
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f

    // Hysteresis accumulators
    private var movingForMs = 0L
    private var stillForMs = 0L
    private var lastUpdateTime = System.currentTimeMillis()

    // Smoothing state
    private var smoothedAccel = 0f

    // Sampling frequency measurement
    private var lastSampleTime = 0L
    private var sampleCount = 0
    private var rmsWindowSize = 26

    // RMS calculation
    private val accelWindow = ArrayDeque<Float>()
    private var rmsSmoothed = 0f

    fun init(config: MotionConfig, logger: Logger) {
        this.config = config
        this.logger = logger
        this.logger.d(TAG, "MotionController initialized")

        reset()

        _motionFlow.value = MotionState(
            motionStartThreshold = config.motionStartThreshold,
            motionStopThreshold = config.motionStopThreshold
        )
    }

    /**
     * Process raw accelerometer data, update motion state.
     *
     * Called by AccelerometerManager on each sensor event.
     */
    fun onAcceleration(x: Float, y: Float, z: Float, timestamp: Long) {
        // ---------------------- Sampling frequency measurement ----------------------
        if (lastSampleTime == 0L) lastSampleTime = timestamp
        sampleCount++
        val freqDt = timestamp - lastSampleTime
        if (freqDt >= 1000L) {
            val frequency = sampleCount / (freqDt / 1000f)
            //logger.d(TAG, "Approx. sampling freq = $frequency Hz")

            // RMS ablak ~2s
            rmsWindowSize = (frequency * 2).toInt().coerceAtLeast(1)
            // reset
            lastSampleTime = timestamp
            sampleCount = 0
        }

        //logger.count(TAG, "accel_updates", logEvery = 10)

        // ---------------------- Acceleration processing ----------------------
        // 1. Low-pass filter: estimate gravity
        gravityX = config.filterAlpha * gravityX + (1 - config.filterAlpha) * x
        gravityY = config.filterAlpha * gravityY + (1 - config.filterAlpha) * y
        gravityZ = config.filterAlpha * gravityZ + (1 - config.filterAlpha) * z

        // 2. High-pass filter: remove gravity
        val linearX = x - gravityX
        val linearY = y - gravityY

        // 3. Horizontal magnitude
        val rawHorizAccel = sqrt(linearX * linearX + linearY * linearY)

        // 4a. Existing smoothing (for comparison / diagram)
        smoothedAccel = config.accelSmoothingAlpha * rawHorizAccel +
                (1 - config.accelSmoothingAlpha) * smoothedAccel
        val filteredAccel = smoothedAccel

        // ---------------------- RMS calculation ----------------------
        // 4b. RMS calculation over rolling window
        accelWindow.add(rawHorizAccel)
        if (accelWindow.size > rmsWindowSize) accelWindow.removeFirst()

        // RMS formula (mean of squares, not variance!)
        val rmsAccel = if (accelWindow.size >= rmsWindowSize) {
            val meanSquare = accelWindow.sumOf { (it * it).toDouble() } / accelWindow.size
            sqrt(meanSquare).toFloat()
        } else {
            smoothedAccel // fallback until window fills
        }

        // Exponential smoothing
        rmsSmoothed = config.rmsAlpha * rmsAccel + (1 - config.rmsAlpha) * rmsSmoothed

        val isSpike = rmsSmoothed > config.spikeThreshold

        if (isSpike) {
            logger.d(TAG, "SPIKE REJECTED: rms=${rmsSmoothed.f2()} > 0.8")
        }

        val usedAccel = if (isSpike) {
            config.motionStartThreshold
        } else {
            rmsSmoothed
        }

        if (isSpike) {
            logger.d(TAG, "SPIKE: rms=${rmsSmoothed.f2()}, usedAccel=$usedAccel")
        }

        val dt = timestamp - lastUpdateTime
        lastUpdateTime = timestamp

        // ---------------------- Hysteresis ----------------------
        // Hysteresis accumulation (uses filtered usedAccel)
        if (usedAccel > _motionFlow.value.motionStartThreshold) {
            movingForMs += dt
            stillForMs = 0
        } else if (usedAccel < _motionFlow.value.motionStopThreshold) {
            stillForMs += dt
            movingForMs = 0
        }

        // State machine with delays
        val newIsMoving = when {
            !_motionFlow.value.isMoving && movingForMs > config.startDelayMs -> true
            _motionFlow.value.isMoving && stillForMs > config.stopDelayMs -> false
            else -> _motionFlow.value.isMoving
        }

        _motionFlow.update { state ->
            state.copy(
                rawAccel = rawHorizAccel,
                filteredAccel = filteredAccel,
                rmsAccel = rmsSmoothed,
                usedAccel = usedAccel,
                isMoving = newIsMoving,
                movingForMs = movingForMs,
                stillForMs = stillForMs,
                lastMovementTime = if (newIsMoving) timestamp else state.lastMovementTime,
                timestamp = timestamp
            )
        }
    }

    /**
     * Reset internal state for testing/replay.
     * Call this before replaying binlog with different config.
     */
    private fun reset() {
        gravityX = 0f
        gravityY = 0f
        gravityZ = 0f
        movingForMs = 0L
        stillForMs = 0L
        lastUpdateTime = System.currentTimeMillis()
        smoothedAccel = 0f
        lastSampleTime = 0L
        sampleCount = 0
        rmsWindowSize = 26
        accelWindow.clear()
        rmsSmoothed = 0f
    }

    private fun Float.f2() = "%.2f".format(this)
}