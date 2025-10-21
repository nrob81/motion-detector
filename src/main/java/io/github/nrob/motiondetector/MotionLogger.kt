package io.github.nrob.motiondetector

/**
 * Logger interface – a MotionController teljesen platform-független.
 */
interface MotionLogger {
    fun d(tag: String, msg: String)
    fun e(tag: String, msg: String, throwable: Throwable? = null)
    fun count(tag: String, counterName: String, logEvery: Int = 100)
}