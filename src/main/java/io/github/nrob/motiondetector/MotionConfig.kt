package io.github.nrob.motiondetector

/**
 * Motion detection configuration parameters.
 *
 * This data class is the single source of truth for all motion detection
 * behavior. All parameters are exposed to allow testing and optimization
 * via grid search or other validation methods.
 *
 * Passed to MotionController at initialization, making it fully decoupled
 * from any specific AppConfig implementation.
 */
data class MotionConfig(
    /**
     * Low-pass filter coefficient for gravity estimation (0.0-1.0).
     *
     * gravity[i] = α * gravity[i-1] + (1 - α) * raw[i]
     *
     * Standard Android value: 0.8
     */
    val filterAlpha: Float,

    /**
     * Exponential smoothing for raw acceleration magnitude (0.0-1.0).
     *
     * Reduces sensor noise and stabilizes hysteresis timing.
     */
    val accelSmoothingAlpha: Float,

    /**
     * Acceleration threshold to trigger MOVING state (m/s²).
     *
     * Typical values:
     * - 0.3-0.4: Sensitive (detects phone handling)
     * - 0.5-0.7: Balanced (filters light handling)
     */
    val motionStartThreshold: Float,

    /**
     * Acceleration threshold to trigger STILL state (m/s²).
     *
     * Should be lower than motionStartThreshold for hysteresis.
     */
    val motionStopThreshold: Float,

    /**
     * Hysteresis delay: STILL → MOVING (milliseconds).
     *
     * Prevents false triggers from brief movements.
     */
    val startDelayMs: Long,

    /**
     * Hysteresis delay: MOVING → STILL (milliseconds).
     *
     * Prevents false triggers from brief stops.
     */
    val stopDelayMs: Long,

    /**
     * Exponential smoothing for RMS acceleration (0.0-1.0).
     *
     * Applied after RMS calculation over 2-second rolling window.
     * Higher values = faster response, more noise.
     */
    val rmsAlpha: Float = 0.3f,

    /**
     * Absolute RMS threshold for spike rejection (m/s²).
     *
     * RMS values above this are considered hand motion (phone pickup)
     * and filtered out from motion detection.
     *
     * Typical spike values: 1.5-2.7 m/s²
     * Typical walking/car: 0.4-1.2 m/s²
     */
    val spikeThreshold: Float = 1.5f
) {
    companion object {
        /**
         * Default configuration for testing/replay.
         * Uses standard Android values and conservative thresholds.
         */
        fun default() = MotionConfig(
            filterAlpha = 0.8f,
            accelSmoothingAlpha = 0.2f,
            motionStartThreshold = 0.4f,
            motionStopThreshold = 0.3f,
            startDelayMs = 3000L,
            stopDelayMs = 1500L,
            rmsAlpha = 0.3f,
            spikeThreshold = 1.5f
        )

        /**
         * Production-optimized configuration (93.25% accuracy).
         * Based on grid search validation (2025-10-20).
         */
        fun optimized() = MotionConfig(
            filterAlpha = 0.8f,
            accelSmoothingAlpha = 0.2f,
            motionStartThreshold = 0.7f,
            motionStopThreshold = 0.2f,
            startDelayMs = 5000L,
            stopDelayMs = 2500L,
            rmsAlpha = 0.4f,
            spikeThreshold = 1.5f
        )
    }
}