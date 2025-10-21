package io.github.nrob.motiondetector

/**
 * Motion state with hysteresis tracking.
 */
data class MotionState(
    val rawAccel: Float = 0f,
    val filteredAccel: Float = 0f,
    var rmsAccel: Float = 0f,
    val usedAccel: Float = 0f,
    val isMoving: Boolean = false,
    val lastMovementTime: Long = 0L,
    val motionStartThreshold: Float = 0f, // init során MotionConfig.motionStartThreshold lesz
    val motionStopThreshold: Float = 0f,  // init során MotionConfig.motionStopThreshold lesz
    val movingForMs: Long = 0L,
    val stillForMs: Long = 0L,
    val timestamp: Long = 0L
)