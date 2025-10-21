package io.github.nrob.motiondetector

/**
 * Snapshot of motion detection state at a given timestamp.
 *
 * Contains acceleration values at different processing stages:
 * - rawAccel: horizontal magnitude after gravity removal
 * - filteredAccel: exponentially smoothed raw acceleration
 * - rmsAccel: RMS over 2-second rolling window
 * - usedAccel: final value after spike rejection
 *
 * Hysteresis tracking:
 * - movingForMs: time spent above motion threshold
 * - stillForMs: time spent below stop threshold
 * - isMoving: current state (true = moving, false = still)
 */
data class MotionState(
    val rawAccel: Float = 0f,
    val filteredAccel: Float = 0f,
    var rmsAccel: Float = 0f,
    val usedAccel: Float = 0f,
    val isMoving: Boolean = false,
    val lastMovementTime: Long = 0L,
    val motionStartThreshold: Float = 0f,
    val motionStopThreshold: Float = 0f,
    val movingForMs: Long = 0L,
    val stillForMs: Long = 0L,
    val timestamp: Long = 0L
)